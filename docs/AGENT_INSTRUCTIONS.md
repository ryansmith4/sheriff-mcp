# Sheriff Static Analysis Instructions

Copy this to your project's `CLAUDE.md`, `copilot-instructions.md`, or equivalent AI agent instructions file.

---

## Static Analysis with Sheriff

This project uses Sheriff MCP server for managing static analysis issue fixes. Sheriff is a work queue manager — it tracks which issues have been fixed, batches them by file, and persists progress across sessions.

### Prerequisites
- Sheriff MCP server configured and running
- SARIF file from static analysis (Qodana, Semgrep, ESLint, etc.)

### Workflow

1. **Load SARIF file:**
   ```
   sheriff load target="path/to/results.sarif"
   ```
   Returns overview of issues by severity and rule. If progress exists from a prior session with the same file, it is automatically restored.

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
   Or use `status="skip"` for false positives. Always use full fingerprint strings from the `next` response.

5. **Repeat** until `progress.rem = 0`

### Rules

- **Fix ALL issues in a file before calling `next` again.** Issues are batched by file — partial fixes waste a tool call because `next` returns the same file until all its issues are marked done.
- **Use snippet matching, not line numbers.** After editing a file, line numbers shift. The `snip` field content remains searchable even after edits above it.
- **Work top-to-bottom within a file.** This minimizes line number drift for issues lower in the file.
- **Mark false positives as `skip`, not `fixed`.** Skipping tracks them separately so they don't inflate your fix count and can be reviewed or reopened later.
- **Don't rescan until the full fix pass is complete.** Re-running static analysis changes the SARIF file, which resets all Sheriff progress. Finish fixing, then rescan to verify.
- **Call `progress` to report status.** It shows total/fixed/skipped/remaining counts, useful for reporting to the user or deciding when to stop.

### Example Session

```
# Load SARIF and see overview
sheriff load target="build/qodana/qodana.sarif.json"
→ 136 total issues, 22 ConstantValue, 15 unused...

# Get first batch of ConstantValue issues
sheriff next scope={rule: "ConstantValue"}
→ 3 issues in Service.java at lines 45, 52, 78

# Read file, fix all 3 issues, then mark done
sheriff done fps=["88d32cab35478753", "ab1c2d3e12345678", "f9e8d7c6a1b2c3d4"] status="fixed"
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
