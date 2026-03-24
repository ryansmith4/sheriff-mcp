# Sheriff-MCP Development Guide

## Project Overview

Sheriff-MCP is a Java MCP server for parsing and querying SARIF static analysis reports. It's designed as an AI agent work queue manager for efficiently fixing static analysis issues.

**Current Version:** Run `./gradlew currentVersion`

## Versioning

Sheriff-MCP follows [Semantic Versioning 2.0.0](https://semver.org/) via the [axion-release-plugin](https://github.com/allegro/axion-release-plugin):

- **MAJOR** version (X.0.0): Incompatible API changes, breaking changes to MCP tool interface
- **MINOR** version (0.X.0): New functionality in a backwards-compatible manner
- **PATCH** version (0.0.X): Backwards-compatible bug fixes

### Version Source

Version is derived from git tags — there is no version in any file. The plugin reads the nearest `v*` tag and computes:
- **On a tagged commit** (release): `1.0.0`
- **After a tagged commit** (development): `1.0.1-SNAPSHOT`

This version is used by all build tools: Gradle, JReleaser, Jib, MCP Registry, and Java runtime (via generated `version.properties`).

### Release Process

```bash
./gradlew release                              # Patch release (1.0.0 → 1.0.1)
./gradlew release -Prelease.increment=minor    # Minor release (1.0.0 → 1.1.0)
./gradlew release -Prelease.increment=major    # Major release (1.0.0 → 2.0.0)
./gradlew release -Prelease.forceVersion=2.0.0 # Explicit version
./gradlew release -Prelease.prerelease=beta    # Pre-release (1.0.0-beta1)
./gradlew release -Prelease.prerelease=rc      # Release candidate (1.0.0-rc1)
```

The `release` task creates a git tag (e.g., `v1.0.0`) and pushes it. No version-bump commits are created.

The tag push triggers the CI/CD pipeline (`release.yml`):
- **`release` job**: Builds, runs JReleaser (GitHub Release + Sigstore signing) — runs for all tags
- **`docker` job**: Builds and pushes multi-arch Docker image to `ghcr.io/ryansmith4/sheriff-mcp` via Jib — final releases only
- **`mcp-registry` job**: Publishes to the MCP Registry via `mcp-publisher` — final releases only
- **`docs` workflow**: Deploys documentation to GitHub Pages

Pre-release tags (e.g., `v1.0.0-beta1`) only run the `release` job (JAR + GitHub Release). Docker and MCP Registry publishing are skipped.

### Re-releasing

If a release pipeline fails partway through:

**Re-run a single failed job:** Use the GitHub Actions UI to re-run just the failed job (e.g., `docker` or `mcp-registry`) without re-tagging. This is the preferred approach when earlier jobs succeeded.

**Full re-release** (when the tag itself needs to change or the `release` job failed):
```bash
git push origin :refs/tags/v1.0.0   # Delete remote tag
git tag -d v1.0.0                    # Delete local tag
# Fix the issue, commit if needed
./gradlew release -Prelease.forceVersion=1.0.0  # Re-tag and push
```

All pipeline steps are idempotent:
- JReleaser overwrites the GitHub Release (`overwrite.set(true)`)
- Jib overwrites Docker tags on ghcr.io
- `mcp-publisher` updates the existing MCP Registry entry
- Cosign re-signs the new image digest automatically

### Artifact Signing

All release artifacts are signed using [Sigstore](https://www.sigstore.dev/) keyless signing with GitHub Actions OIDC — no keys to manage or rotate.

| Artifact | Signing Method | Tool |
|----------|---------------|------|
| Fat JAR (GitHub Release) | Sigstore cosign | JReleaser |
| Docker image (ghcr.io) | Sigstore cosign (keyless OIDC) | `sigstore/cosign-installer` |
| MCP Registry namespace | GitHub OIDC | `mcp-publisher` |

Jib writes the image digest to `build/jib-image.digest` after pushing, which cosign uses to sign by digest (not tag) to prevent TOCTOU race conditions.

Verify the Docker image signature:
```bash
cosign verify ghcr.io/ryansmith4/sheriff-mcp:latest \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  --certificate-identity-regexp="github.com/ryansmith4/sheriff-mcp"
```

## Git Workflow

GitHub is configured to **squash-and-merge** PRs. After a PR merges, use `git branch -D branch` (force delete) to clean up local branches — `git branch -d` will fail because the squashed commit has a different SHA than the branch's original commits.

### Creating Pull Requests

Branch protection requires 1 reviewer approval. To enable the human developer to review and approve PRs created by Claude Code, PRs must be authored by the `sheriff-mcp-bot` GitHub App:

```bash
BOT_TOKEN=$(py C:/Users/RyanSmith/.claude/github-apps/get-token.py)
GH_TOKEN=$BOT_TOKEN gh pr create --title "..." --body "..."
```

- **Git commits**: Use normal `git` commands (committed as the developer)
- **PR creation only**: Use `GH_TOKEN=$BOT_TOKEN gh pr create` (authored by bot)
- The developer can then review and approve the bot-authored PR on GitHub

### Conventional Commits

Use these prefixes for automatic changelog generation:

- `feat:` - New features → "Features" section
- `fix:` - Bug fixes → "Bug Fixes" section
- `docs:` - Documentation changes
- `chore:` - Maintenance tasks
- `refactor:` - Code refactoring
- `test:` - Test changes

## Build Commands

```bash
./gradlew build          # Build and run tests
./gradlew test           # Run tests only
./gradlew fatJar         # Create fat JAR for distribution
./gradlew spotlessApply  # Format code with Palantir Java Format
./gradlew spotlessCheck  # Check code formatting
./gradlew jibBuildTar    # Build container image as tar (single-platform)
./gradlew currentVersion # Show current version derived from git tags
./gradlew release        # Tag a release (see Release Process above)
./scripts/codeql-local.sh  # Run CodeQL security analysis locally (optional, requires gh codeql)
```

## Project Structure

- `src/main/java/com/guidedbyte/sheriff/` - Main source code
  - `model/sarif/` - SARIF data model records
  - `model/state/` - Progress state models
  - `model/response/` - API response models
  - `service/` - Business logic services
  - `mcp/` - MCP server and tool implementation
  - `cli/` - Picocli CLI commands
- `.github/workflows/` - CI/CD pipelines
  - `ci.yml` - Build/test/format check on push to main and PRs
  - `release.yml` - JAR release → Docker push → MCP Registry publish → README version update PR (on `v*` tags)
  - `docs.yml` - GitHub Pages deployment (on `v*` tags, docs changes, or manual)
  - `dependabot-auto-merge.yml` - Auto-squash-merges non-major Dependabot PRs

## Key Design Decisions

1. **Single unified tool** - All actions through one `sheriff` tool with action parameter
2. **File-based batching** - Issues grouped by file for efficient agent workflow
3. **Compact JSON responses** - Abbreviated field names to minimize context usage
4. **H2 embedded database** - Persistent state survives restarts
5. **Two-phase startup** - MCP transport starts immediately; DB init deferred to background thread
6. **Serialized tool execution** - All tool calls synchronized to prevent concurrent DB access

## Concurrency

- Tool calls are serialized via `synchronized` on `SheriffTool.execute()` — the H2 embedded database uses a single shared Connection that is not thread-safe
- Multiple Claude Code instances on the same codebase will fail with a clear error (H2 file lock)
- If SARIF data is reloaded while agents are working, stale fingerprints in `done` calls will return a descriptive error

## Troubleshooting

- **Database locked error**: Another Sheriff instance is using the `.sheriff/` database. Close the other instance or use a different working directory.
- **Corrupt database**: Delete the `.sheriff/` directory and reload the SARIF file. All progress will be reset.

## Testing

Run `./gradlew test` to execute the test suite.

For manual testing:
1. Build: `./gradlew fatJar`
2. Start: `java -jar build/libs/sheriff-mcp-*-all.jar start`

## MCP Tool Actions

- `load` - Load SARIF file, returns overview
- `next` - Get next batch of issues (grouped by file). Returns at most 25 items by default. Use `limit` parameter to override (e.g., `limit=10` for smaller batches). Use `fmt` parameter for output format (`default` or `checklist`).
- `done` - Mark issues as fixed/skipped
- `progress` - Get session progress summary
- `reopen` - Reopen issues marked as fixed/skipped (undo capability)
- `summary` - Get breakdown by rule/severity/file without iterating
- `export` - Export remaining issues to file (JSON or list format)
