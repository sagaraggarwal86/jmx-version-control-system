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

- **Auto-optimize CLAUDE.md**: After any session that modifies design decisions or architecture, review for redundancy
  and stale entries. Every line must carry actionable information.
- **Auto-compact**: Proactively suggest `/compact` before context becomes unwieldy.
- **Auto-update README.md**: After feature changes, keep README feature tables and config sections current.

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

| Package   | Key Classes                                                                                                                                                             |
|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `model`   | `VersionEntry`, `VersionIndex`, `TriggerType` (CHECKPOINT, AUTO_CHECKPOINT, RESTORE), `LockInfo`                                                                        |
| `config`  | `ScmConfigManager` — hybrid: `jmeter.properties` defaults + `index.json` per-plan overrides                                                                             |
| `storage` | `FileOperations` (copy, atomic restore, checksum), `IndexManager` (index.json CRUD, self-heal), `LockManager` (.lock), `AuditLogger` (audit.log, 1MB rotation)          |
| `core`    | `ScmContext` (per-plan lifecycle), `ScmInitializer` (lazy init, singleton, toolbar), `SaveCommandWrapper` (save hook), `SnapshotEngine`, `DirtyTracker`, `RetentionManager`, `AutoSaveScheduler` |
| `ui`      | `VersionHistoryPanel` (bottom dockable), `ScmMenuHandler` (Tools menu), `DirtyIndicator` (toolbar status), `CheckpointDialog`, `SettingsDialog`, `AboutDialog`, `Toast`  |

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
- **Snapshot Deduplication (R8)**: SHA-256 checksum comparison. Only `AUTO_CHECKPOINT` deduplicates. `CHECKPOINT` and `RESTORE` always create a version.
- **Self-Healing (R2)**: index.json parse failure → rename to `.bak`, rebuild from `.jmxv` filenames on disk.
- **Existence-Only Validation (R7)**: `Files.exists()` per entry — no file reads. Sub-millisecond.
- **Version Numbers Never Reset (R9)**: Global, monotonically incrementing. Even after retention pruning.
- **ScmContext Lifecycle (R1)**: Per-test-plan state object. Dispose previous on new open. Prevents state leaks.
- **Concurrency**: `SwingWorker` for all storage ops. UI updates via `SwingUtilities.invokeLater`.
- **Post-Restore Normalization**: Save + dirty tracker reset after reload to prevent redundant auto-checkpoint from re-serialization differences.
- **All runtime deps `provided`**: JMeter core, Jackson on JMeter classpath. Thin JAR, no shading.
- **Pure additive**: Never modifies or removes JMeter behavior. All reflection with graceful fallback.

### JMeter Integration Points (4 reflection touchpoints)

| Touchpoint               | What                                           | Failure                                     |
|--------------------------|-------------------------------------------------|---------------------------------------------|
| `ActionRouter` save hook | Wrap `ActionNames.SAVE`                         | `try-catch(Throwable)`: native save works   |
| `MainFrame` toolbar      | Insert 5 buttons (C,H,I,L,D) + separator        | Toolbar absent. JMeter functional           |
| `MainFrame` bottom panel | Access `JSplitPane`                              | Fallback to content pane. JMeter functional |
| `JMenuBar` Tools menu    | Find Tools `JMenu`, append Version Control submenu | Menu absent. JMeter functional              |

### Storage Schema

- **index.json**: `schemaVersion: 1`, `maxRetention`, `storageLocation`, `versions[]` array of `VersionEntry`, `pinnedVersions` set of version numbers
- **.lock**: JSON with `pid`, `hostname`, `timestamp`, `jmeterVersion`. Timestamp-based stale detection.
- **audit.log**: JSON-lines audit trail. 1MB size limit with single `.1` backup rotation.
- **Folder**: `.history/<jmx-stem>/` per test plan, containing `index.json`, `.lock`, `v001.jmxv`, `v002.jmxv`, ...

### Key Constraints

- **index.json schema is public** — `VersionEntry` and `VersionIndex` field names are backward-compatible. Field renames are breaking changes.
- **Version file naming**: `v001.jmxv` zero-padded to 3 digits.
- **Retention pruning**: FIFO — oldest unpinned versions removed first. Latest version always preserved.
- **Freeze (pin)**: Frozen versions are exempt from retention pruning and bulk deletion. Unfreeze to allow deletion.
- **Delete guard**: Latest version cannot be deleted. Frozen versions cannot be deleted.
- **Selective deletion**: Checkbox selection + "Delete Versions" deletes only checked, non-frozen, non-latest versions.
- **Restore is append-only**: Always snapshots current state (RESTORE trigger) before restoring. History never destructive.
- **Lock**: Application-level (no `FileLock`). Configurable stale timeout. Second instance → read-only mode. Write operations re-validate lock via `ensureWriteLock()` — re-acquires if deleted, switches to read-only if stolen.
- **Missing snapshot resilience**: Restore validates `.jmxv` file exists before proceeding. Delete cleans up orphaned index entries if file is missing.
- **Toolbar**: 5 buttons (C=Checkpoint, H=History, I=Indicator, L=Lock, D=Delete). Visibility togglable in Settings. Sized to match native JMeter toolbar buttons.
