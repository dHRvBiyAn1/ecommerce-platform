#!/usr/bin/env python3
"""Build the deterministic, combined documentation contract from service inputs."""

from __future__ import annotations

import argparse
import copy
import re
import sys
from pathlib import Path
from typing import Any

import yaml


REPOSITORY = Path(__file__).resolve().parents[1]
OUTPUT = REPOSITORY / "swagger.yaml"
SERVICES = (
    ("auth", "auth-service"),
    ("product", "product-service"),
    ("inventory", "inventory-service"),
    ("order", "order-service"),
    ("payment", "payment-service"),
    ("notification", "notification-service"),
    ("cart", "cart-service"),
    ("coupon", "coupon-service"),
)
METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}
COMPONENT_SECTIONS = (
    "schemas", "responses", "parameters", "examples", "requestBodies", "headers",
    "securitySchemes", "links", "callbacks",
)


class ContractError(ValueError):
    pass


class StableDumper(yaml.SafeDumper):
    def ignore_aliases(self, data: Any) -> bool:
        return True


class UniqueKeyLoader(yaml.SafeLoader):
    def construct_mapping(self, node: yaml.MappingNode, deep: bool = False) -> dict[Any, Any]:
        mapping: dict[Any, Any] = {}
        for key_node, value_node in node.value:
            try:
                key = self.construct_object(key_node, deep=deep)
                duplicate = key in mapping
            except TypeError as error:
                raise yaml.constructor.ConstructorError(
                    "while constructing a mapping", node.start_mark,
                    "mapping keys must be scalar values", key_node.start_mark,
                ) from error
            if duplicate:
                raise yaml.constructor.ConstructorError(
                    "while constructing a mapping", node.start_mark,
                    f"duplicate mapping key {key!r}", key_node.start_mark,
                )
            mapping[key] = self.construct_object(value_node, deep=deep)
        return mapping


UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    UniqueKeyLoader.construct_mapping,
)


def _pointer(document: dict[str, Any], ref: str) -> Any:
    if not ref.startswith("#/"):
        raise ContractError(f"external reference is not allowed: {ref}")
    current: Any = document
    for token in ref[2:].split("/"):
        token = token.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or token not in current:
            raise ContractError(f"unresolved reference: {ref}")
        current = current[token]
    return current


def _transform_security(requirements: list[dict[str, Any]], namespace: str) -> list[dict[str, Any]]:
    return [{f"{namespace}_{name}": scopes for name, scopes in requirement.items()}
            for requirement in requirements]


def _validate_security(value: Any, location: str) -> None:
    if not isinstance(value, list) or any(not isinstance(entry, dict) for entry in value):
        raise ContractError(f"{location} must be a list of security requirement objects")
    for requirement in value:
        if any(not isinstance(name, str) or not isinstance(scopes, list) for name, scopes in requirement.items()):
            raise ContractError(f"{location} contains a malformed security requirement")


def _validate_server_list(value: Any, location: str) -> None:
    if not isinstance(value, list) or any(
        not isinstance(server, dict) or not isinstance(server.get("url"), str) for server in value
    ):
        raise ContractError(f"{location} must be a list of server objects with string URLs")


def _validate_document(doc: Any, path: Path) -> dict[str, Any]:
    if not isinstance(doc, dict) or not isinstance(doc.get("paths"), dict):
        raise ContractError(f"invalid OpenAPI contract: {path}")
    if "info" in doc and not isinstance(doc["info"], dict):
        raise ContractError(f"info must be an object: {path}")
    if "servers" in doc:
        _validate_server_list(doc["servers"], f"servers in {path}")
    if "security" in doc:
        _validate_security(doc["security"], f"security in {path}")
    tags = doc.get("tags", [])
    if not isinstance(tags, list) or any(
        not isinstance(tag, dict) or not isinstance(tag.get("name"), str) for tag in tags
    ):
        raise ContractError(f"tags must be a list of objects with string names: {path}")
    components = doc.get("components", {})
    if not isinstance(components, dict):
        raise ContractError(f"components must be an object: {path}")
    for section, entries in components.items():
        if not isinstance(section, str):
            raise ContractError(f"component section names must be strings: {path}")
        if section.startswith("x-"):
            continue
        if section not in COMPONENT_SECTIONS:
            raise ContractError(f"unsupported components section {section!r}: {path}")
        if not isinstance(entries, dict) or any(not isinstance(name, str) for name in entries):
            raise ContractError(f"components.{section} names must be strings: {path}")
        if any(not isinstance(value, dict) for value in entries.values()):
            raise ContractError(f"components.{section} must contain objects: {path}")
    for route, item in doc["paths"].items():
        if not isinstance(route, str) or not isinstance(item, dict):
            raise ContractError(f"path items must be objects keyed by strings: {path}")
        if "servers" in item:
            _validate_server_list(item["servers"], f"servers for {route}")
        for method, operation in item.items():
            if not isinstance(method, str):
                raise ContractError(f"path item keys must be strings for {route}")
            if method.lower() in METHODS:
                if not isinstance(operation, dict):
                    raise ContractError(f"{method.upper()} {route} must be an operation object")
                if "security" in operation:
                    _validate_security(operation["security"], f"security for {method.upper()} {route}")
                if "tags" in operation and (
                    not isinstance(operation["tags"], list)
                    or any(not isinstance(tag, str) for tag in operation["tags"])
                ):
                    raise ContractError(f"tags for {method.upper()} {route} must be a list of strings")
    return doc


def _transform(value: Any, namespace: str, document: dict[str, Any]) -> Any:
    if isinstance(value, list):
        return [_transform(item, namespace, document) for item in value]
    if not isinstance(value, dict):
        return value
    result: dict[str, Any] = {}
    for key, item in value.items():
        if isinstance(key, str) and key.startswith("x-"):
            result[key] = copy.deepcopy(item)
        elif key in {"example", "default", "enum", "value"}:
            result[key] = copy.deepcopy(item)
        elif key == "$ref" and isinstance(item, str):
            _pointer(document, item)
            if item.startswith("#/components/"):
                parts = item[len("#/components/"):].split("/", 1)
                if len(parts) != 2 or not parts[0] or not parts[1]:
                    raise ContractError(f"invalid component reference: {item}")
                section, name = parts
                result[key] = f"#/components/{section}/{namespace}_{name}"
            elif item.startswith("#/paths/"):
                raise ContractError(f"path item reference is not supported: {item}")
            elif item.startswith("#/"):
                raise ContractError(f"unsupported internal reference: {item}")
            else:
                raise ContractError(f"external reference is not allowed: {item}")
        elif key == "security" and isinstance(item, list):
            result[key] = _transform_security(item, namespace)
        elif key == "tags" and isinstance(item, list):
            result[key] = [f"{namespace}: {tag}" for tag in item]
        elif key == "operationId" and isinstance(item, str):
            result[key] = f"{namespace}_{item}"
        else:
            result[key] = _transform(item, namespace, document)
    return result


def _load(path: Path) -> dict[str, Any]:
    try:
        with path.open(encoding="utf-8") as stream:
            doc = yaml.load(stream, Loader=UniqueKeyLoader)
    except (OSError, yaml.YAMLError) as error:
        raise ContractError(f"cannot read {path}: {error}") from error
    return _validate_document(doc, path)


def build(repo: Path) -> dict[str, Any]:
    paths: dict[str, Any] = {}
    normalized_paths: dict[str, str] = {}
    tags: dict[str, dict[str, Any]] = {}
    components: dict[str, dict[str, Any]] = {section: {} for section in COMPONENT_SECTIONS}
    component_extensions: dict[str, Any] = {}
    for namespace, service in SERVICES:
        source = repo / "services" / service / "src/main/openapi/swagger.yaml"
        doc = _load(source)
        # Validate every reference, including references nested in component values.
        transformed_doc = _transform(doc, namespace, doc)
        security_schemes = doc.get("components", {}).get("securitySchemes", {})
        for path, original_path_item in doc["paths"].items():
            path_item = original_path_item
            if "servers" not in path_item and doc.get("servers"):
                path_item = {**path_item, "servers": doc["servers"]}
            normalized = re.sub(r"\{[^{}]+\}", "{}", path)
            previous_path = normalized_paths.get(normalized)
            if previous_path is not None and previous_path != path:
                raise ContractError(f"duplicate normalized path templates {previous_path!r} and {path!r}")
            normalized_paths[normalized] = path
            for method in path_item:
                if not isinstance(method, str):
                    raise ContractError(f"path item keys must be strings for {path}")
                if method.lower() in METHODS and path in paths and method.lower() in paths[path]:
                    raise ContractError(f"duplicate {method.upper()} {path}")
                if method.lower() in METHODS:
                    effective_security = path_item[method].get("security", doc.get("security", []))
                    for requirement in effective_security:
                        unknown = set(requirement) - set(security_schemes)
                        if unknown:
                            raise ContractError(f"undefined security scheme(s) for {method.upper()} {path}: {', '.join(sorted(unknown))}")
            transformed = _transform(path_item, namespace, doc)
            # Root-level security is service-specific; materialize it on operations
            # that inherit it so public `security: []` remains an explicit override.
            if "security" in doc:
                for method, operation in transformed.items():
                    if method.lower() in METHODS and "security" not in operation:
                        operation["security"] = [dict(requirement) for requirement in transformed_doc["security"]]
            if path not in paths:
                paths[path] = transformed
            else:
                paths[path].update({key: value for key, value in transformed.items() if key.lower() in METHODS})
        for tag in doc.get("tags", []):
            renamed = f"{namespace}: {tag['name']}"
            tags[renamed] = {"name": renamed, **({"description": tag["description"]} if "description" in tag else {})}
        for section, entries in doc.get("components", {}).items():
            if section.startswith("x-"):
                component_extensions[f"x-{namespace}-{section[2:]}"] = entries
                continue
            components.setdefault(section, {})
            for name, value in entries.items():
                components[section][f"{namespace}_{name}"] = _transform(value, namespace, doc)

    result: dict[str, Any] = {
        "openapi": "3.0.1",
        "info": {
            "title": "Ecommerce Platform APIs",
            "version": "1.0.0",
            "contact": {"name": "Ecommerce Platform maintainers"},
            "description": (
                "Combined documentation derived from the eight service-owned "
                "src/main/openapi/swagger.yaml contracts. Edit service inputs, then "
                "regenerate this file. Per-path servers preserve direct service access "
                "and gateway alternatives."
            ),
        },
        "servers": [{"url": "http://localhost:8080", "description": "Local API gateway"}],
        "tags": [tags[key] for key in sorted(tags)],
        "paths": {key: paths[key] for key in sorted(paths)},
        "components": {},
    }
    for section in COMPONENT_SECTIONS:
        if components.get(section):
            result["components"][section] = {key: components[section][key] for key in sorted(components[section])}
    for extension in sorted(component_extensions):
        result["components"][extension] = component_extensions[extension]
    return result


def render(document: dict[str, Any]) -> str:
    return "# Generated combined documentation. Edit the service-owned contracts, not this file.\n" + yaml.dump(
        document, Dumper=StableDumper, allow_unicode=True, default_flow_style=False,
        sort_keys=False, width=1000,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if swagger.yaml is out of date")
    parser.add_argument("--repo-root", type=Path, default=REPOSITORY, help=argparse.SUPPRESS)
    parser.add_argument("--output", type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args()
    destination = args.output or args.repo_root / "swagger.yaml"
    try:
        generated = render(build(args.repo_root))
        if args.check:
            current = destination.read_text(encoding="utf-8") if destination.exists() else ""
            if current != generated:
                print(f"OpenAPI bundle is stale: {destination}", file=sys.stderr)
                return 1
            print("OpenAPI bundle is current.")
            return 0
        destination.write_text(generated, encoding="utf-8")
        print(f"Wrote {destination}")
        return 0
    except (ContractError, OSError, yaml.YAMLError) as error:
        print(f"OpenAPI bundle failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
