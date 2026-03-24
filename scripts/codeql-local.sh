#!/usr/bin/env bash
# Run CodeQL analysis locally before pushing.
#
# Prerequisites:
#   1. GitHub CLI: https://cli.github.com/
#   2. CodeQL extension: gh extension install github/gh-codeql
#      (first run downloads ~360MB)
#
# Usage:
#   ./scripts/codeql-local.sh          # Analyze and report
#   ./scripts/codeql-local.sh --help   # Show this help
#
# This mirrors the CI CodeQL workflow (security-extended query suite)
# and produces a SARIF report at build/codeql-results.sarif.
# Exit code is non-zero if alerts are found.

set -euo pipefail

DB_DIR="build/codeql-db"
SARIF_FILE="build/codeql-results.sarif"

if [[ "${1:-}" == "--help" ]]; then
    sed -n '2,16p' "$0" | sed 's/^# \?//'
    exit 0
fi

# --- Check prerequisites ---

if ! command -v gh > /dev/null 2>&1; then
    echo "SKIP: GitHub CLI (gh) is not installed."
    echo "  Install from: https://cli.github.com/"
    echo "  Then run: gh extension install github/gh-codeql"
    exit 0
fi

if ! gh codeql version > /dev/null 2>&1; then
    echo "SKIP: CodeQL CLI extension is not installed."
    echo "  Install with: gh extension install github/gh-codeql"
    echo "  (First run downloads ~360MB)"
    exit 0
fi

# --- Determine platform-appropriate build command ---
# CodeQL runs the build command via the system shell (cmd.exe on Windows,
# sh on Mac/Linux), so the Gradle wrapper invocation differs by platform.

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        # Windows (Git Bash, MSYS2, Cygwin) — CodeQL uses cmd.exe internally
        BUILD_CMD="cmd.exe /C .\\gradlew.bat compileJava -x spotlessCheck --no-daemon --quiet --rerun-tasks"
        ;;
    *)
        # macOS, Linux
        BUILD_CMD="./gradlew compileJava -x spotlessCheck --no-daemon --quiet --rerun-tasks"
        ;;
esac

# --- Create database ---

echo "==> Creating CodeQL database..."
rm -rf "$DB_DIR"
gh codeql database create "$DB_DIR" \
    --language=java-kotlin \
    --overwrite \
    --source-root=. \
    --command="$BUILD_CMD"

# --- Analyze ---

echo "==> Running CodeQL analysis (security-extended)..."
gh codeql database analyze "$DB_DIR" \
    --format=sarifv2.1.0 \
    --output="$SARIF_FILE" \
    --sarif-include-query-help=always \
    codeql/java-queries:codeql-suites/java-security-extended.qls

# --- Report results ---

echo "==> Results written to $SARIF_FILE"
echo ""
echo "  View in VS Code: install the 'SARIF Viewer' extension and open the file"
echo "  Upload to GitHub:"
echo "    gh codeql github upload-results \\"
echo "      --sarif=$SARIF_FILE \\"
echo "      --ref=\$(git rev-parse --abbrev-ref HEAD) \\"
echo "      --commit=\$(git rev-parse HEAD)"
