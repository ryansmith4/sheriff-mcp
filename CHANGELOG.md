# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-02-04

### Added

- Initial release of Sheriff-MCP
- **Core Actions**
  - `load` - Load SARIF file(s) into the database with change detection
  - `next` - Get next batch of issues grouped by file (default limit: 25)
  - `done` - Mark issues as fixed or skipped
  - `progress` - Get session progress summary with scope filtering
  - `reopen` - Reopen issues that were marked as fixed/skipped
  - `summary` - Get breakdown by rule, severity, and file
  - `export` - Export remaining issues to JSON or list format
- **Scope Filtering** - Filter by rule ID (with wildcards), severity, or file pattern
- **Persistent Progress** - H2 embedded database survives restarts and context compaction
- **Multi-format SARIF Support** - Works with Qodana, Semgrep, ESLint, CodeQL, and other SARIF producers
- **Installation Options**
  - Fat JAR distribution
  - Docker image on GitHub Container Registry
  - MCP Registry publishing
- **CI/CD**
  - GitHub Actions for multi-platform testing
  - Automated release workflow with Sigstore signing

### Security

- Path traversal protection in export action
- SQL injection prevention via prepared statements
- Sanitized error messages (no stack trace exposure)

[Unreleased]: https://github.com/ryansmith4/sheriff-mcp/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ryansmith4/sheriff-mcp/releases/tag/v1.0.0
