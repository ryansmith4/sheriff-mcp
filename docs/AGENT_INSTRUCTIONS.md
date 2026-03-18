# Sheriff Static Analysis Instructions

Copy this to your project's `CLAUDE.md`, `copilot-instructions.md`, or equivalent AI agent instructions file.

---

## Static Analysis with Sheriff

This project uses Sheriff MCP server for managing static analysis issue fixes.

### Prerequisites
- Sheriff MCP server configured and running
- SARIF file from static analysis (Qodana, Semgrep, ESLint, etc.)

### Workflow

1. **Load SARIF file:**
   ```
   sheriff load target="path/to/results.sarif"
   ```
   Returns overview of issues by severity and rule.

2. **Get next batch:**
   ```
   sheriff next scope={rule: "ConstantValue"}  # Optional filter
   ```
   Returns all issues in one file with code context.
   - `limit` (optional): Maximum issues to return (default: 25)
   - `fmt` (optional): Output format, `"default"` or `"checklist"`

3. **Fix issues:**
   - Read the file
   - Use `snip` field to locate each issue (line numbers may drift after edits)
   - Fix all issues in the batch

4. **Mark complete:**
   ```
   sheriff done fps=["fp1", "fp2", ...] status="fixed"
   ```
   Or use `status="skip"` for false positives.

5. **Repeat** until `progress.rem = 0`

### Tips

- **Always fix ALL issues in a file before moving to next** - issues are batched by file for efficiency
- **Use snippet matching, not line numbers** - after you edit a file, line numbers shift but snippets remain findable
- **Mark issues as "skip" if they're false positives** - this tracks them without counting as fixed
- **Check `progress`** to see overall progress and remaining work

### Example Session

```
# Load SARIF and see overview
sheriff load target="build/qodana/qodana.sarif.json"
→ 136 total issues, 22 ConstantValue, 15 unused...

# Get first batch of ConstantValue issues
sheriff next scope={rule: "ConstantValue"}
→ 3 issues in Service.java at lines 45, 52, 78

# Read file, fix all 3 issues, then mark done
sheriff done fps=["88d32cab", "ab1c2d3e", "f9e8d7c6"] status="fixed"
→ 3 marked, 19 remaining

# Continue...
sheriff next scope={rule: "ConstantValue"}
→ 2 issues in Repository.java...
```

### Scope Filters

Focus on specific issues:
- `{rule: "ConstantValue"}` - specific rule
- `{severity: "High"}` - only high severity
- `{file: "src/**/*.java"}` - file pattern
- Combine: `{rule: "unused", severity: "Moderate", file: "src/main/**"}`
