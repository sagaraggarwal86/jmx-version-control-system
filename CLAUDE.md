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

| Package   | Key Classes                                                                                                                                                                                                                                                                                       |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `model`   | `VersionEntry`, `VersionIndex`, `TriggerType` (CHECKPOINT, AUTO_CHECKPOINT, RESTORE), `LockInfo`                                                                                                                                                                                                  |
| `config`  | `ScmConfigManager` — **`user.properties` is single source of truth** for all settings. `index.json` stores per-plan data (versions, pins, retention) but NOT storage location. All getters have hardcoded defaults; deleted/blank values self-heal. Backslashes escaped for `.properties` format. |
| `storage` | `FileOperations` (copy, atomic restore, checksum), `IndexManager` (index.json CRUD, self-heal), `LockManager` (.lock), `AuditLogger` (audit.log, 1MB rotation, STORAGE_MIGRATE/STORAGE_RESET events)                                                                                                |
| `core`    | `ScmContext` (per-plan lifecycle, `resolveParent()`/`extractStem()` helpers), `ScmInitializer` (lazy init, singleton, toolbar), `SaveCommandWrapper` (save hook), `ScmOpenListener` (open/close/new lifecycle), `SnapshotEngine`, `DirtyTracker`, `RetentionManager`, `AutoSaveScheduler`         |
| `ui`      | `VersionHistoryPanel` (bottom dockable), `ScmMenuCreator` (JMeter `MenuCreator` entry point, deferred startup init), `ScmMenuHandler` (Tools menu), `DirtyIndicator` (toolbar status), `CheckpointDialog` (500-char note limit), `SettingsDialog` (custom JDialog: OK/Cancel/Reset to Defaults, storage path validation, Migrate/Reset/Cancel storage dialog), `AboutDialog` (version from manifest), `Toast`, `TimeFormatUtils` |

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
- **Snapshot Deduplication (R8)**: SHA-256 checksum. Only `AUTO_CHECKPOINT` deduplicates. `CHECKPOINT` and `RESTORE`
  always create.
- **Self-Healing (R2)**: index.json parse failure → rename to `.bak`, rebuild from `.jmxv` filenames on disk.
- **Existence-Only Validation (R7)**: `Files.exists()` per entry — no file reads. Sub-millisecond.
- **Version Numbers Never Reset (R9)**: Global, monotonically incrementing. Even after retention pruning.
- **ScmContext Lifecycle (R1)**: Per-test-plan state object. Dispose previous on new open. Prevents state leaks.
- **Concurrency**: `SwingWorker` for all storage ops. UI updates via `SwingUtilities.invokeLater`.
- **Post-Restore Normalization**: Save + dirty tracker reset after reload. Restore note: "Replaced by vN".
- **Config Self-Heal**: All config getters have defaults. Deleted `user.properties` → `ensureDefaultsPersisted()`
  recreates. Blank values → default kicks in.
- **All runtime deps `provided`**: JMeter core, Jackson on JMeter classpath. Thin JAR, no shading.
- **Pure additive**: Never modifies or removes JMeter behavior. All reflection with graceful fallback.

### JMeter Integration Points (4 reflection touchpoints)

| Touchpoint               | What                                     | Failure                                     |
|--------------------------|------------------------------------------|---------------------------------------------|
| `ActionRouter` save hook | Wrap `ActionNames.SAVE`                  | `try-catch(Throwable)`: native save works   |
| `MainFrame` toolbar      | Insert 5 buttons (C,H,I,L,D) + separator | Toolbar absent. JMeter functional           |
| `MainFrame` bottom panel | Access `JSplitPane`                      | Fallback to content pane. JMeter functional |
| `JMenuBar` Tools menu    | Append Version Control submenu           | Menu absent. JMeter functional              |

### Lifecycle Hooks

| Action                 | Handler              | Behavior                                                                             |
|------------------------|----------------------|--------------------------------------------------------------------------------------|
| SAVE                   | `SaveCommandWrapper` | Bootstrap plugin, `ensureInitializedWithContext()`                                   |
| OPEN / SUB_TREE_LOADED | `ScmOpenListener`    | Deferred `ensureInitializedWithContext()` via `invokeLater`                          |
| CLOSE (File > New)     | `ScmOpenListener`    | Same deferred call — `testPlanFile == null` → disposes context. Cancel-on-save safe. |

### Storage Schema

- **index.json**: `schemaVersion: 1`, `maxRetention`, `storageLocation` (record only, not used for resolution),
  `versions[]`, `pinnedVersions`
- **.lock**: JSON with `pid`, `hostname`, `timestamp`, `jmeterVersion`. Timestamp-based stale detection.
- **audit.log**: JSON-lines. 1MB limit, single `.1` backup rotation. Actions: CHECKPOINT, AUTO_CHECKPOINT, RESTORE,
  DELETE, CLEAR_HISTORY, RETENTION_PRUNE, PIN, UNPIN, EXPORT, FORCE_RELEASE_LOCK, STORAGE_MIGRATE, STORAGE_RESET.
- **Folder**: `<storageLocation>/<jmx-stem>/` per test plan, containing `index.json`, `.lock`, `<stem>_001.jmxv`, ...

### Key Constraints

- **index.json schema is public** — field renames are breaking changes.
- **Version file naming**: `<stem>_001.jmxv` — stem from jmx filename, `%03d` format (zero-padded to minimum 3
  digits, self-extends for versions 1000+). `FileOperations.extractStem()` is single source of truth.
- **Retention pruning**: FIFO, oldest unpinned first. Latest always preserved.
- **Freeze (pin)**: Exempt from retention pruning and bulk deletion. `isSelectable()` is the single predicate for
  checkbox eligibility.
- **Delete guard**: Latest and frozen versions cannot be deleted.
- **Selective deletion**: Header checkbox select-all + "Delete Versions" deletes only checked, non-frozen, non-latest.
- **Restore**: Append-only. Snapshots current state (RESTORE trigger, note "Replaced by vN") before restoring.
- **Lock**: Application-level (no `FileLock`). Dual-purpose L button: verify ownership → polite acquire → force release
  with confirmation. `ensureWriteLock()` on every write op. Dynamic tooltip reflects state.
- **Missing snapshots**: `refresh()` detects via `Files.exists()`, disables Restore/Export with tooltip. Delete cleans
  orphaned entries. WARN log level.
- **Swing tooltips**: `JTable.getToolTipText(MouseEvent)` override — renderer-level tooltips don't receive mouse events.
- **Toolbar**: C, H, I, L, D. Visibility togglable in Settings. Sized to match native JMeter buttons.
- **Storage location**: `user.properties` is single source of truth (not index.json). Supports relative (to .jmx) or
  absolute paths. Backslashes escaped in `.properties`. Browse button in Settings. GUI change triggers
  Migrate/Reset/Cancel dialog: Migrate moves files (dispose → move → reinit), Reset backs up old files to
  `<stem>_backup_<yyyyMMdd_HHmmss>.zip` in parent dir then deletes originals and starts fresh, Cancel reverts.
  user.properties change takes effect on restart only — no runtime detection. Partial migration failures handled
  per-file. `ensureDefaultsPersisted()` writes step-by-step manual migration instructions to user.properties.
- **Read-only lock dialog**: Shows hostname, PID, and timestamp from `.lock` file when another instance holds the lock.
- **File > New**: Deferred `ensureInitializedWithContext()` for all lifecycle actions. Cancel-on-save preserves context.
- **Export**: Default filename `<jmx-name>_v<N>.jmx`, file filter "JMeter (.jmx)".
