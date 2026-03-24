# Contributing to Sheriff-MCP

Thank you for your interest in contributing to Sheriff-MCP! This document provides guidelines and instructions for contributing.

## Code of Conduct

Please be respectful and constructive in all interactions. We welcome contributors of all experience levels.

## Getting Started

### Prerequisites

- Java 21 or later
- Gradle 8.x or later (or use the included wrapper)

### Building

```bash
# Clone the repository
git clone https://github.com/ryansmith4/sheriff-mcp.git
cd sheriff-mcp

# Build and run tests
./gradlew build

# Create the fat JAR
./gradlew fatJar
```

### Running Tests

```bash
./gradlew test
```

### Local CodeQL Analysis (Optional)

You can run the same CodeQL security analysis locally that runs in CI. This is optional but recommended before pushing security-sensitive changes.

**One-time setup:**

1. Install the [GitHub CLI](https://cli.github.com/) if you don't have it
2. Install the CodeQL extension:
   ```bash
   gh extension install github/gh-codeql
   ```
   The first run downloads the CodeQL CLI (~360MB).

**Running the analysis:**

```bash
./scripts/codeql-local.sh
```

This creates a CodeQL database, runs the `security-extended` query suite (same as CI), and writes results to `build/codeql-results.sarif`. The script auto-detects your platform (Windows/macOS/Linux) and uses the appropriate Gradle wrapper.

If `gh` or the CodeQL extension isn't installed, the script prints install instructions and exits cleanly — it won't block your workflow.

**Viewing results:**

- **VS Code**: Install the [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer) extension and open `build/codeql-results.sarif`
- **GitHub**: Upload results with the command shown at the end of the script output

## Development Workflow

### 1. Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork locally
3. Add the upstream remote: `git remote add upstream https://github.com/ryansmith4/sheriff-mcp.git`

### 2. Create a Branch

```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/your-bug-fix
```

### 3. Make Changes

- Follow the code style (enforced by Spotless)
- Add tests for new functionality
- Update documentation as needed

### 4. Code Style

This project uses [Palantir Java Format](https://github.com/palantir/palantir-java-format) via Spotless. Code is automatically formatted on compile, but you can also run:

```bash
# Check formatting
./gradlew spotlessCheck

# Apply formatting
./gradlew spotlessApply
```

**Style Guidelines:**

- Use meaningful variable and method names
- Keep methods focused and reasonably sized
- Add Javadoc for public APIs
- Prefer immutable objects (records) where appropriate

### 5. Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(export): add CSV export format
fix(next): handle empty SARIF files gracefully
docs(readme): add troubleshooting section
```

### 6. Submit a Pull Request

1. Push your branch to your fork
2. Open a Pull Request against the `main` branch
3. Fill out the PR template
4. Wait for review

## Testing Guidelines

### Test Structure

- Unit tests go in `src/test/java`
- Use JUnit 5 and AssertJ
- Test files should match source files: `FooAction.java` → `FooActionTest.java`

### What to Test

- **Actions**: Test input validation, success cases, and error cases
- **Services**: Test business logic independently
- **Repositories**: Test database operations

### Example Test

```java
@Test
void shouldReturnErrorWhenSarifNotLoaded() {
    var result = action.execute(null);

    assertThat(result).isInstanceOf(ErrorResponse.class);
    assertThat(((ErrorResponse) result).error().code()).isEqualTo("SARIF_NOT_LOADED");
}
```

## Project Structure

```
src/main/java/com/guidedbyte/sheriff/
├── cli/                 # CLI commands (Picocli)
├── mcp/
│   ├── SheriffMcpServer.java  # MCP server
│   └── tools/
│       ├── SheriffTool.java   # Tool dispatcher
│       └── actions/           # Action handlers
├── model/
│   ├── response/        # API response records
│   ├── sarif/           # SARIF data models
│   └── state/           # State models
├── service/             # Business logic
└── util/                # Utilities
```

## Reporting Issues

### Bug Reports

Please include:
- Sheriff-MCP version
- Java version
- Operating system
- Steps to reproduce
- Expected vs actual behavior
- SARIF file snippet (if relevant, sanitized of sensitive data)

### Feature Requests

Please include:
- Use case description
- Proposed solution (if any)
- Alternatives considered

## Questions?

Open a [GitHub Discussion](https://github.com/ryansmith4/sheriff-mcp/discussions) for questions or ideas.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
