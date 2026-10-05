#!/usr/bin/env python3
"""Focused regression checks for the OpenAPI bundler."""

import importlib.util
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml

SCRIPT = Path(__file__).with_name("bundle-openapi.py")
NORMALIZER = Path(__file__).with_name("normalize-spectral-json.py")
STATUS_RESOLVER = Path(__file__).with_name("resolve-openapi-check-status.sh")
FIXTURES = Path(__file__).parent / "fixtures/openapi"
spec = importlib.util.spec_from_file_location("bundle_openapi", SCRIPT)
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class BundleOpenApiTests(unittest.TestCase):
    def repo(self, auth_fixture="collision-a.yaml", product_fixture="collision-b.yaml"):
        root = Path(tempfile.mkdtemp())
        for namespace, service in bundle.SERVICES:
            source = root / "services" / service / "src/main/openapi/swagger.yaml"
            source.parent.mkdir(parents=True)
            fixture = auth_fixture if namespace == "auth" else product_fixture if namespace == "product" else None
            if fixture:
                source.write_text((FIXTURES / fixture).read_text())
            else:
                source.write_text("openapi: 3.0.1\ninfo: {title: Empty, version: '1'}\npaths: {}\n")
        return root

    def test_namespaces_colliding_schemas_security_and_operation_ids(self):
        result = bundle.build(self.repo())
        self.assertIn("auth_Record", result["components"]["schemas"])
        self.assertIn("product_Record", result["components"]["schemas"])
        self.assertEqual("auth_read", result["paths"]["/shared"]["get"]["operationId"])
        self.assertEqual([{"auth_Bearer": []}], result["paths"]["/shared"]["get"]["security"])
        self.assertEqual([], result["paths"]["/shared"]["post"]["security"])
        self.assertEqual(["auth: Things"], result["paths"]["/shared"]["get"]["tags"])
        self.assertEqual("http://localhost:8081", result["paths"]["/shared"]["servers"][0]["url"])
        ref = result["paths"]["/shared"]["get"]["responses"]["200"]["content"]["application/json"]["schema"]["$ref"]
        self.assertEqual("#/components/schemas/auth_Record", ref)

    def test_duplicate_method_and_path_fails(self):
        root = self.repo()
        b = root / "services/product-service/src/main/openapi/swagger.yaml"
        doc = yaml.safe_load(b.read_text())
        doc["paths"]["/shared"] = doc["paths"].pop("/other")
        b.write_text(yaml.safe_dump(doc))
        with self.assertRaisesRegex(bundle.ContractError, "duplicate GET /shared"):
            bundle.build(root)

    def test_missing_and_external_refs_fail(self):
        for fixture, expected in (("missing-ref.yaml", "unresolved reference"), ("external-ref.yaml", "external reference")):
            with self.subTest(fixture=fixture):
                with self.assertRaisesRegex(bundle.ContractError, expected):
                    bundle.build(self.repo(auth_fixture=fixture))

    def test_undefined_security_scheme_and_normalized_path_collision_fail(self):
        root = self.repo()
        auth = root / "services/auth-service/src/main/openapi/swagger.yaml"
        product = root / "services/product-service/src/main/openapi/swagger.yaml"
        doc = yaml.safe_load(auth.read_text())
        doc["paths"]["/shared/{recordId}"] = doc["paths"]["/shared"]
        product_doc = yaml.safe_load(product.read_text())
        product_doc["paths"]["/shared/{slug}"] = product_doc["paths"].pop("/other")
        product.write_text(yaml.safe_dump(product_doc))
        auth.write_text(yaml.safe_dump(doc))
        with self.assertRaisesRegex(bundle.ContractError, "duplicate normalized path templates"):
            bundle.build(root)

        doc["paths"].pop("/shared/{recordId}")
        product_doc["paths"]["/other"] = product_doc["paths"].pop("/shared/{slug}")
        product.write_text(yaml.safe_dump(product_doc))
        doc["paths"]["/shared"]["get"]["security"] = [{"Missing": []}]
        auth.write_text(yaml.safe_dump(doc))
        with self.assertRaisesRegex(bundle.ContractError, "undefined security scheme"):
            bundle.build(root)

    def test_duplicate_yaml_mapping_key_fails(self):
        with self.assertRaisesRegex(bundle.ContractError, "duplicate mapping key 'get'"):
            bundle.build(self.repo(auth_fixture="duplicate-yaml-key.yaml"))

    def test_component_extensions_are_preserved_and_namespaced(self):
        result = bundle.build(self.repo(auth_fixture="components-extension.yaml"))
        self.assertIs(result["components"]["x-auth-feature-flag"], True)
        self.assertEqual(
            {"operationId": "literal-owner-value"},
            result["components"]["x-auth-metadata"],
        )

    def test_component_section_and_name_keys_must_be_strings(self):
        root = self.repo()
        auth = root / "services/auth-service/src/main/openapi/swagger.yaml"
        baseline = yaml.safe_load(auth.read_text())
        for invalid_key in (1, True, None):
            with self.subTest(kind="section", key=invalid_key):
                doc = copy.deepcopy(baseline)
                doc["components"] = {invalid_key: {}}
                auth.write_text(yaml.safe_dump(doc))
                with self.assertRaisesRegex(bundle.ContractError, "component section names must be strings"):
                    bundle.build(root)
            with self.subTest(kind="component", key=invalid_key):
                doc = copy.deepcopy(baseline)
                doc["components"] = {"schemas": {invalid_key: {"type": "object"}}}
                auth.write_text(yaml.safe_dump(doc))
                with self.assertRaisesRegex(bundle.ContractError, "components.schemas names must be strings"):
                    bundle.build(root)

    def test_literal_example_default_enum_and_example_value_are_not_rewritten(self):
        result = bundle.build(self.repo(auth_fixture="payload-literals.yaml"))
        schema = result["paths"]["/literal"]["post"]["requestBody"]["content"]["application/json"]["schema"]
        literal = {"tags": ["payload"], "operationId": "keep-me", "security": [{"Bearer": []}]}
        self.assertEqual(literal, schema["default"])
        self.assertEqual([literal], schema["enum"])
        self.assertEqual(literal, schema["example"])
        example = result["paths"]["/literal"]["post"]["requestBody"]["content"]["application/json"]["examples"]["sample"]
        self.assertEqual(literal, example["value"])
        self.assertEqual("auth_submit", result["paths"]["/literal"]["post"]["operationId"])

    def test_path_item_refs_fail_when_routes_can_merge(self):
        root = self.repo(auth_fixture="path-item-ref.yaml")
        product = root / "services/product-service/src/main/openapi/swagger.yaml"
        doc = yaml.safe_load(product.read_text())
        doc["paths"]["/shared"] = doc["paths"].pop("/other")
        product.write_text(yaml.safe_dump(doc))
        with self.assertRaisesRegex(bundle.ContractError, "path item reference is not supported: #/paths/"):
            bundle.build(root)

    def test_null_openapi_structures_fail_without_tracebacks(self):
        root = self.repo()
        auth = root / "services/auth-service/src/main/openapi/swagger.yaml"
        baseline = yaml.safe_load(auth.read_text())
        cases = (
            ("operation", lambda doc: doc["paths"]["/shared"].update({"get": None}), "GET /shared must be an operation object"),
            ("components", lambda doc: doc.update({"components": None}), "components must be an object"),
            ("tags", lambda doc: doc.update({"tags": None}), "tags must be a list"),
            ("security", lambda doc: doc.update({"security": None}), "security in .* must be a list"),
        )
        for _label, mutate, expected in cases:
            with self.subTest(case=_label):
                doc = copy.deepcopy(baseline)
                mutate(doc)
                auth.write_text(yaml.safe_dump(doc))
                with self.assertRaisesRegex(bundle.ContractError, expected):
                    bundle.build(root)

        with self.assertRaisesRegex(bundle.ContractError, "path items must be objects"):
            bundle.build(self.repo(auth_fixture="null-path-item.yaml"))

    def test_check_detects_output_drift(self):
        root = self.repo()
        output = root / "swagger.yaml"
        output.write_text("stale\n")
        command = [sys.executable, str(SCRIPT), "--check", "--repo-root", str(root)]
        self.assertNotEqual(0, subprocess.run(command, check=False, capture_output=True).returncode)
        generated = bundle.render(bundle.build(root))
        self.assertNotEqual(output.read_text(), generated)
        output.write_text(generated)
        self.assertEqual(0, subprocess.run(command, check=False, capture_output=True).returncode)

    def test_spectral_normalizer_discards_cli_footer_and_requires_json(self):
        with tempfile.TemporaryDirectory() as temp:
            raw = Path(temp) / "raw.log"
            output = Path(temp) / "diagnostics.json"
            raw.write_text('[{"code":"rule","severity":1}]\nNo results with severity "warn" found.\n')
            command = [sys.executable, str(NORMALIZER), str(raw), str(output)]
            self.assertEqual(0, subprocess.run(command, check=False).returncode)
            self.assertEqual([{"code": "rule", "severity": 1}], json.loads(output.read_text()))

            raw.write_text("not json")
            self.assertNotEqual(0, subprocess.run(command, check=False, capture_output=True).returncode)

    def test_spectral_failure_status_wins_when_json_normalization_fails(self):
        spectral_failed = subprocess.run(["bash", str(STATUS_RESOLVER), "17", "2"], check=False)
        self.assertEqual(17, spectral_failed.returncode)
        normalizer_failed = subprocess.run(["bash", str(STATUS_RESOLVER), "0", "2"], check=False)
        self.assertEqual(2, normalizer_failed.returncode)


if __name__ == "__main__":
    unittest.main()
