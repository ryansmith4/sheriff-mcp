# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in Sheriff-MCP, please report it responsibly:

1. **Do not** open a public GitHub issue
2. Email security concerns to the maintainer (see GitHub profile for contact)
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

You can expect:
- Acknowledgment within 48 hours
- Status update within 7 days
- Credit in the fix announcement (unless you prefer anonymity)

## Release Signing

All release artifacts are cryptographically signed using [Sigstore](https://sigstore.dev/) keyless signing:

- **What's signed**: JAR files, tar.gz, and zip archives
- **Signature format**: Cosign blob signatures (`.sig` files)
- **Certificates**: OIDC-based certificates (`.pem` files)
- **Identity**: GitHub Actions workflow identity

Verify signatures using cosign:

```bash
cosign verify-blob \
  --signature artifact.sig \
  --certificate artifact.pem \
  --certificate-identity-regexp "https://github.com/ryansmith4/sheriff-mcp" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  artifact
```

See README.md for detailed verification instructions.

## Security Measures

Sheriff-MCP implements the following security measures:

### Path Traversal Protection

The `export` action validates output paths to prevent writing outside the current working directory:
- Rejects paths containing `..`
- Rejects absolute paths
- Normalizes and validates resolved paths

### SQL Injection Prevention

All database operations use prepared statements with parameterized queries. No user input is concatenated into SQL strings.

### Error Message Sanitization

Error responses return generic messages without exposing:
- Stack traces
- Internal file paths
- Database details
- System information

### Input Validation

- Fingerprints are validated against known issues before processing
- Action names are validated against an allowlist
- Numeric parameters are bounds-checked

## Security Considerations for Users

### SARIF Files

- Only load SARIF files from trusted sources
- SARIF files may contain file paths from your codebase; be cautious when sharing database files

### Database Files

- The H2 database file (`.sheriff/sheriff.mv.db`) contains issue data and paths
- Do not share database files publicly if they contain sensitive path information

### Network Exposure

- Sheriff-MCP uses stdio transport by default (no network exposure)
- If configured with other transports, ensure appropriate network security

## Dependencies

Sheriff-MCP uses the following key dependencies:
- H2 Database (embedded, file-based)
- Jackson (JSON parsing)
- MCP SDK (Model Context Protocol)
- SLF4J/Logback (logging)

We monitor dependencies for known vulnerabilities and update promptly.
