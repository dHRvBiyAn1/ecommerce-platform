# Local Code Tools

Optional developer tools, independent of the application build. Run commands from
this worktree's root. No shell-profile edits, global installs, Git hooks, or agent
plugins are required. Existing global Graphify installations are not changed.

## Install

Prerequisites: existing `uv`, Python 3.10+, `curl`, `tar`, and `shasum`.
The commands below were tested on macOS arm64 with Python 3.11 and uv 0.12.5.

Graphify uses the official **`graphifyy`** PyPI package (double y), pinned to
0.8.24. Its dependencies are resolved by uv, not transitively locked here.

```sh
mkdir -p .tools/bin .tools/downloads .tools/rtk .tools/uv-cache
UV_TOOL_DIR="$PWD/.tools/uv-tools" \
UV_TOOL_BIN_DIR="$PWD/.tools/bin" \
UV_CACHE_DIR="$PWD/.tools/uv-cache" \
UV_PYTHON_DOWNLOADS=never \
uv tool install graphifyy==0.8.24
.tools/bin/graphify --version
```

RTK uses the official v0.48.0 release binary, not the unrelated crates.io package.
For **macOS arm64 only**:

```sh
curl -fL https://github.com/rtk-ai/rtk/releases/download/v0.48.0/rtk-aarch64-apple-darwin.tar.gz \
  -o .tools/downloads/rtk-aarch64-apple-darwin.tar.gz
shasum -a 256 .tools/downloads/rtk-aarch64-apple-darwin.tar.gz
```

Before extracting, verify the SHA-256 equals the official GitHub release asset digest:

```text
4fa025cc93a744b6963f4e53a008e5ba3f74b6a38061f4a47c639e1c3023e0db
```

```sh
tar -tzf .tools/downloads/rtk-aarch64-apple-darwin.tar.gz
# The archive should contain only: rtk
tar -xzf .tools/downloads/rtk-aarch64-apple-darwin.tar.gz -C .tools/bin
RTK_TELEMETRY_DISABLED=1 .tools/bin/rtk --version
```

Other platforms must choose the matching asset and verify its own digest from
the official release. No remote installer script is executed. Release hashes
check download integrity against GitHub metadata, not independent provenance.

## Graphify

Use the supported local AST update command for initial creation and refresh:

```sh
GRAPHIFY_OUT=graphify-out GRAPHIFY_QUERY_LOG_DISABLE=1 \
  .tools/bin/graphify update . --no-cluster
GRAPHIFY_QUERY_LOG_DISABLE=1 \
  .tools/bin/graphify query "JwtService" --budget 800
GRAPHIFY_QUERY_LOG_DISABLE=1 \
  .tools/bin/graphify explain "JwtService"
```

This writes `graphify-out/graph.json` and local caches. `--no-cluster` skips
community detection, reports, and HTML; queries still work. Source AST and
supported document syntax are parsed locally, without semantic model calls.
Graphify 0.8.24 uses `.graphifyignore` instead of falling back to `.gitignore`.
Keep tooling, agent state, environment files, private config, Terraform state,
local kustomize secrets, and build outputs explicitly listed there. Never use
`extract`, `/graphify`, external backends, or global graph merging for this
repository without approval for the relevant data handling.

`graphify build`, mentioned in existing `GEMINI.md`, is **not** a command in
0.8.24. Use `update . --no-cluster` after edits instead. Rebuild before relying on
queries while teammates are editing. AST graphs are navigation aids, not proof
of runtime behavior or exhaustive cross-service dependency analysis.

## RTK

Set these in the current terminal, from the worktree root, before RTK commands:

```sh
export RTK_TELEMETRY_DISABLED=1
export RTK_DB_PATH="$PWD/.tools/rtk/history.db"
export RTK_TEE_DIR="$PWD/.tools/rtk/tee"
.tools/bin/rtk --ultra-compact git status
.tools/bin/rtk git diff --stat
.tools/bin/rtk gain
```

The database and failure-output logs stay under ignored `.tools/`. They can
contain command arguments and raw output: do not share them or run secret-dumping
commands through RTK. Environment overrides apply only to this terminal and its
children; reset them when switching worktrees. RTK may read existing user config
but this setup never creates or modifies it.

Invocation is explicit. The "No hook installed" warning is expected; do **not**
follow its `rtk init -g` suggestion. No automatic OpenCode command rewriting is
installed. `gain` may say "Global Scope", but its database is the worktree-local
path above. Filtering is lossy: use native commands for full review and diagnostic
output. Some RTK search filters require ripgrep, which is not installed by this
setup. Built-in agent Read/Grep/Glob tools are unaffected.

## Verification And Sources

Setup verified Graphify 0.8.24 version/help, local AST rebuild (3,275 nodes,
21,870 edges), and `JwtService` query/explain (11 direct connections). Generated
node source paths contained no `.tools`, `.opencode`, `.agents`, `.superpowers`,
`.claude`, `.antigravitycli`, `config-repo`, `.env*`, Terraform state, local
kustomize secrets, `node_modules`, `target`, or `dist` entries. Counts change
with ongoing edits.

RTK 0.48.0 version/help and Git status succeeded. Its local `gain` database
recorded one command, 679 estimated input tokens, 568 output tokens, and 16.3%
savings. This is a single-command measurement, not a general savings guarantee.

- [Graphify official docs (default branch v8)](https://github.com/Graphify-Labs/graphify/blob/v8/README.md)
- [Graphify PyPI package](https://pypi.org/project/graphifyy/0.8.24/)
- [RTK pinned release](https://github.com/rtk-ai/rtk/releases/tag/v0.48.0)
- [RTK pinned configuration docs](https://github.com/rtk-ai/rtk/blob/v0.48.0/docs/guide/getting-started/configuration.md)

Installation uses outbound GitHub and PyPI/package-download requests only; local
indexing does not upload repository content. To uninstall this local setup,
remove this worktree's `.tools/` and `graphify-out/` directories after confirming
they contain no data you need. No global uninstall command is needed.
