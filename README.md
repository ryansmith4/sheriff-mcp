<!-- mcp-name: io.github.ryansmith4/sheriff-mcp -->
# Sheriff-MCP

[![CI](https://github.com/ryansmith4/sheriff-mcp/actions/workflows/ci.yml/badge.svg)](https://github.com/ryansmith4/sheriff-mcp/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/ryansmith4/sheriff-mcp/badge)](https://scorecard.dev/viewer/?uri=github.com/ryansmith4/sheriff-mcp)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/12244/badge)](https://www.bestpractices.dev/projects/12244)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![GitHub release](https://img.shields.io/github/v/release/ryansmith4/sheriff-mcp)](https://github.com/ryansmith4/sheriff-mcp/releases)

Sheriff is an MCP server that helps AI agents efficiently fix static analysis issues from SARIF reports.

**[Documentation](https://ryansmith4.github.io/sheriff-mcp/)** | **[Installation](https://ryansmith4.github.io/sheriff-mcp/getting-started/installation/)** | **[Tool Reference](https://ryansmith4.github.io/sheriff-mcp/tools/)**

---

## Why Sheriff?

AI agents struggle with large static analysis reports:
- **Context overload** - 100+ issues overwhelm context windows
- **Lost progress** - Work is lost on context compaction or session restart
- **Inefficient navigation** - No batching means jumping between files repeatedly

Sheriff solves this by acting as a work queue manager:
- **Intelligent batching** - Issues grouped by file for efficient fixing
- **Persistent progress** - State survives compaction, restarts, and agent switches
- **Scope filtering** - Focus on specific rules, severities, or file patterns
- **Compact responses** - Minimal context usage with abbreviated field names

### Supported Static Analysis Tools

Sheriff works with any tool that produces [SARIF](https://sarifweb.azurewebsites.net/) output:

| Tool | Language | SARIF Command |
|------|----------|---------------|
| **Qodana** | Java/Kotlin/JS/Python | `qodana scan` |
| **Semgrep** | Multi-language | `semgrep --sarif -o results.sarif` |
| **ESLint** | JavaScript/TypeScript | `eslint --format @microsoft/sarif` |
| **CodeQL** | Multi-language | Built-in SARIF output |
| **SpotBugs** | Java | `spotbugs -sarif` |
| **Bandit** | Python | `bandit -f sarif` |
| **Checkov** | IaC | `checkov -o sarif` |
| **Trivy** | Container/IaC | `trivy --format sarif` |
| **SonarQube** | Multi-language | Built-in SARIF export |

---

## Quick Start

### 1. Install

<details>
<summary><strong>JAR (All Platforms)</strong> — Requires Java 21+</summary>

Download `sheriff-mcp-1.0.1-all.jar` from [Releases](https://github.com/ryansmith4/sheriff-mcp/releases).

</details>

<details>
<summary><strong>Docker</strong></summary>

```bash
docker pull ghcr.io/ryansmith4/sheriff-mcp:latest
```

</details>

<details>
<summary><strong>MCP Registry</strong></summary>

Clients that support the [MCP Registry](https://registry.modelcontextprotocol.io/) can install directly by name: `io.github.ryansmith4/sheriff-mcp`

</details>

See the [Installation Guide](https://ryansmith4.github.io/sheriff-mcp/getting-started/installation/) for full details.

### 2. Configure Your MCP Client

Add Sheriff to your MCP client (Claude Code, Cursor, ChatGPT Desktop, etc.):

```json
{
  "mcpServers": {
    "sheriff": {
      "command": "java",
      "args": ["-jar", "/path/to/sheriff-mcp-1.0.1-all.jar", "start"]
    }
  }
}
```

Or with Docker:

```json
{
  "mcpServers": {
    "sheriff": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "-v", ".:/data", "ghcr.io/ryansmith4/sheriff-mcp:latest"]
    }
  }
}
```

See the [Agent Setup Guide](https://ryansmith4.github.io/sheriff-mcp/getting-started/agent-setup/) for client-specific instructions and recommended agent instructions.

### 3. Use It

```
1. Run static analysis     →  qodana scan
2. Load into Sheriff       →  sheriff load target="results.sarif"
3. Get next file's issues  →  sheriff next
4. Fix all issues in file  →  [edit the code]
5. Mark as done            →  sheriff done fps=[...] status="fixed"
6. Repeat 3-5              →  until remaining = 0
```

Sheriff exposes a single `sheriff` tool with 7 actions: `load`, `next`, `done`, `progress`, `summary`, `reopen`, and `export`. See the [Tool Reference](https://ryansmith4.github.io/sheriff-mcp/tools/) for full documentation.

---

## Example Session

```
User: "Fix all ConstantValue issues in my codebase"

Agent: sheriff load target="build/qodana/qodana.sarif.json"
       → 136 total issues, 22 ConstantValue, 15 unused...

Agent: sheriff next scope={rule: "ConstantValue"}
       → 3 issues in Service.java with code snippets

Agent: [reads Service.java, fixes all 3 issues]

Agent: sheriff done fps=["88d32cab35478753", "ab1c2d3e12345678", "f9e8d7c6a1b2c3d4"] status="fixed"
       → 3 marked fixed, 19 remaining

       ... continues until remaining = 0
```

---

## Security

All release artifacts are signed with [Sigstore](https://sigstore.dev/) for supply chain security.

**Verify JAR:**
```bash
VERSION=1.0.1
cosign verify-blob \
  --signature sheriff-mcp-${VERSION}-all.jar.sig \
  --certificate sheriff-mcp-${VERSION}-all.jar.pem \
  --certificate-identity-regexp "https://github.com/ryansmith4/sheriff-mcp" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  sheriff-mcp-${VERSION}-all.jar
```

**Verify Docker image:**
```bash
cosign verify ghcr.io/ryansmith4/sheriff-mcp:latest \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  --certificate-identity-regexp="github.com/ryansmith4/sheriff-mcp"
```

See [SECURITY.md](SECURITY.md) for our security policy.

---

## Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

Apache License 2.0 - see [LICENSE](LICENSE)
