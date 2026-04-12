# CLAUDE.md

## Prohibitions [STRICT]

- Target **JMeter 5.6.3** exclusively — verify all APIs exist in 5.6.3 before using them
- Never change git history or Java 17 implementation
- Never assume — ask if in doubt
- Never make changes to code until user confirms
- Never change existing functionality or make changes beyond confirmed scope
- Only recommend alternatives when there is a concrete risk or significant benefit
- Analyze impact across dependent layers (storage → core → ui) before proposing changes
- Conflicting requirements: flag the conflict, pause, and wait for decision
- Decision priority: **Correctness → Security → Performance → Readability → Simplicity**

## Workflow

- Interactive session — present choices one by one, unless changes are trivial and clearly scoped
- If my choices severely impact application integrity or cause excessive changes, briefly explain consequences and
  recommend better alternatives
- After all changes are finalized, self-check for regressions, naming consistency, and adherence to these rules
- Multi-file changes: present all files together with dependency order noted
- Conflicting requirements: flag the conflict, pause, and wait for decision
- Rollback: revert to last explicitly approved file set, then ask how to proceed
- If context grows large, summarize confirmed state before continuing

## Response Style

- Concise — no filler phrases, no restating the request, no vague or over-explanatory content

## Communication

- Always provide honest feedback — flag risks, trade-offs, or better alternatives even if the user didn't ask.
  Do not agree silently if there is a concrete concern. Be direct, not diplomatic.
- For every decision point or design choice, present options in a concise table:

  | Option | Risk | Effort | Impact | Recommendation |
              |--------|------|--------|--------|----------------|

  Highlight the recommended option. Keep descriptions brief — one line per cell.

## Self-Maintenance

- **Auto-optimize CLAUDE.md**: After any session that adds or modifies design decisions, constraints, or architectural
  details in this file, review CLAUDE.md for redundancy, stale entries, and verbosity. Remove duplicates, compress
  verbose entries, and ensure every line carries actionable information. Do not wait for the user to request this.
- **Auto-compact**: When the conversation context grows large (many tool calls, long code reads, repeated file edits),
  proactively suggest `/compact` to the user before context becomes unwieldy. Do not wait until context is nearly full.
- **Auto-update README.md**: After any session that adds, removes, or modifies user-facing features, update README.md
  to reflect the change. Keep feature tables and configuration sections current. Do not wait for the user to request
  this.

## Build Commands

```bash
mvn clean verify                          # Build + tests
mvn clean package -DskipTests             # Build only
mvn test -Dtest=SomeTestClass             # Single test class
mvn test -Dtest=SomeTestClass#testMethod  # Single test method
mvn clean deploy -Prelease                # Release to Maven Central
```

Requirements: JDK 17 only, Maven 3.8+.

## Architecture

Lightweight local version control for JMeter test plans (.jmx files). Auto-snapshots on save, linear version history,
one-click rollback. Single-user, local-disk operation. No Git, no SVN, no external tools.

### Package Structure

| Package   | Key Classes                                                                                                                                                                  |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `model`   | `VersionEntry`, `VersionIndex`, `TriggerType`, `LockInfo`                                                                                                                    |
| `config`  | `ScmConfigManager` — hybrid: `jmeter.properties` defaults + `index.json` per-plan overrides                                                                                  |
| `storage` | `FileOperations` (copy, atomic restore, checksum), `IndexManager` (index.json CRUD, self-heal), `LockManager` (.lock)                                                        |
| `core`    | `ScmContext` (per-plan lifecycle), `ScmInitializer` (lazy init, singleton), `SaveCommandWrapper` (save hook), `SnapshotEngine`, `DirtyTracker`, `RetentionManager`           |
| `ui`      | `ScmToolbarGroup` (3 buttons), `VersionHistoryPanel` (bottom dockable), `ScmMenuHandler` (Tools menu), `DirtyIndicator`, `CheckpointDialog`, `SettingsDialog`, `AboutDialog` |

### Dependency Direction (Strict)

```
ui → core → storage → model
              ↑
            config
```

No circular or upward dependencies.

### Core Design Decisions

- **Save Hook Isolation (R3)**: Entire post-save call in `try-catch(Throwable)`. Native save must never be blocked.
- **Atomic Writes (R4, R6)**: All file writes use temp + `Files.move(ATOMIC_MOVE)` pattern. Prevents corruption.
- **Snapshot Deduplication (R8)**: SHA-256 checksum comparison against latest version. Skips identical saves.
- **Self-Healing (R2)**: index.json parse failure → rename to `.bak`, rebuild from `.jmxv` filenames on disk.
- **Existence-Only Validation (R7)**: `Files.exists()` per entry — no file reads. Sub-millisecond.
- **Version Numbers Never Reset (R9)**: Global, monotonically incrementing. Even after retention pruning.
- **ScmContext Lifecycle (R1)**: Per-test-plan state object. Dispose previous on new open. Prevents state leaks.
- **Concurrency**: `SwingWorker` for all storage ops. UI updates via `SwingUtilities.invokeLater`.
- **All runtime deps `provided`**: JMeter core, Jackson on JMeter classpath. Thin JAR, no shading.
- **Pure additive**: Never modifies or removes JMeter behavior. All reflection with graceful fallback.

### JMeter Integration Points (4 reflection touchpoints)

| Touchpoint               | What                       | Failure                                     |
|--------------------------|----------------------------|---------------------------------------------|
| `ActionRouter` save hook | Wrap `ActionNames.SAVE`    | `try-catch(Throwable)`: native save works   |
| `MainFrame` toolbar      | Append `ScmToolbarGroup`   | Toolbar absent. JMeter functional           |
| `MainFrame` bottom panel | Access `JSplitPane`        | Fallback to content pane. JMeter functional |
| `JMenuBar` Tools menu    | Find Tools `JMenu`, append | Menu absent. JMeter functional              |

### Storage Schema

- **index.json**: `schemaVersion: 1`, `maxRetention`, `storageLocation`, `versions[]` array of `VersionEntry`
- **.lock**: JSON with `pid`, `hostname`, `timestamp`, `jmeterVersion`. Timestamp-based stale detection.
- **Folder**: `.history/` alongside `.jmx` with `index.json`, `.lock`, `v001.jmxv`, `v002.jmxv`, ...

### Key Constraints

- **index.json schema is public** — `VersionEntry` and `VersionIndex` field names are backward-compatible.
  Field renames are breaking changes.
- **Version file naming**: `v001.jmxv` zero-padded to 3 digits.
- **Retention pruning**: FIFO — oldest versions removed first.
- **Delete guard**: Latest version cannot be deleted.
- **Restore is append-only**: Auto-snapshots current state before restoring. History never destructive.
- **Lock**: Application-level (no `FileLock`). Configurable stale timeout. Second instance → read-only mode.
