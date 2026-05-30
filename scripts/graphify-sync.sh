#!/bin/bash
set -e

echo "=========================================================="
echo "          GRAPHIFY KNOWLEDGE GRAPH SYNCHRONIZER          "
echo "=========================================================="
echo ""

# Navigate to project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Check if graphify CLI tool is installed
if ! command -v graphify &> /dev/null; then
    echo "WARNING: 'graphify' command could not be found."
    echo "Please ensure the graphify MCP/toolchain is globally installed."
    exit 1
fi

echo "Step 1: Re-extracting local code files and updating graph..."
graphify update .

# Check if code-review-graph is installed
if command -v code-review-graph &> /dev/null; then
    echo "Step 2: Re-indexing codebase structure..."
    code-review-graph index
else
    echo "Info: 'code-review-graph' command is not available, skipping index step."
fi

echo ""
echo "SUCCESS: Knowledge graph sync completed successfully!"
echo "=========================================================="
