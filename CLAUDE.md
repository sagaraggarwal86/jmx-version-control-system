# CLAUDE.md

## Rules

**Behavioral**
- Never assume — ask if in doubt.
- Never edit code until the user confirms.
- Never expand scope beyond what was confirmed.
- Recommend alternatives only when there is a concrete risk or significant benefit.
- On conflicting requirements: flag, pause, wait for decision.
- On obstacles: fix the root cause, not the symptom. Never bypass safety checks (`--no-verify`, `git reset --hard`, disabling tests).

**Technical**
- Target JMeter 5.6.3 exclusively. Verify every API exists in 5.6.3 before using it.
- Java 17, Maven 3.8+. Do not change these targets.
- Do not rewrite git history.
- Decision priority: **Correctness → Security → Performance → Readability → Simplicity**.
- Before proposing changes, trace impact along the dependency direction (see Architecture).

## Workflow & Communication

- Interactive — present choices one at a time unless trivial and clearly scoped.
- Multi-file changes: present all files together, note dependency order.
- Rollback: revert to the last explicitly approved file set, then ask.
- After changes: self-check for regressions, naming consistency, and rule adherence.
- Summarize confirmed state if context grows large; suggest `/compact` proactively.
- Responses: concise. No filler, no restating the request.
- Feedback: direct, not diplomatic. Flag concrete concerns even when not asked.
- For every decision point, present a table and highlight the recommendation:

  | Option | Risk | Effort | Impact | Recommendation |
  |--------|------|--------|--------|----------------|

## Environment

- JDK 17, Maven 3.8+. All runtime deps `provided` (JMeter + Jackson on JMeter classpath). Thin JAR, no shading.
- Test stack: JUnit 5.10.1 + Mockito 5.8.0. Jackson 2.16.1.
- Shell: bash on Windows (Unix syntax — `/dev/null`, forward slashes). `find`/`grep` via Bash tool are fork-unstable; use Glob/Grep tools instead.
- UI changes cannot be exercised without a live JMeter runtime — say so explicitly rather than claiming success.
- R-tags (R1–R9) in source comments reference an internal requirements doc not in this repo. Preserve existing tags; do not invent new ones.

## Build & Coverage

```bash
mvn clean verify                          # Build + tests + JaCoCo gate
mvn clean package -DskipTests             # Build only
mvn test -Dtest=SomeTestClass             # Single test class
mvn test -Dtest=SomeTestClass#testMethod  # Single test method
mvn clean deploy -Prelease                # Release to Maven Central
```

- JaCoCo gate: ≥85% line coverage on testable code (`verify` phase).
- Excluded from the gate (require live JMeter runtime): `ui/**`, `ScmInitializer`, `AutoSaveScheduler`, `SaveCommandWrapper`, `ScmOpenListener`. Tests may still exist for these; they just don't enforce the threshold.
- Report: `target/site/jacoco/index.html`.

## Architecture

Lightweight local version control for JMeter `.jmx` test plans. Auto-snapshots on save, linear history, one-click rollback. Single-user, local-disk. Pure additive: never modifies or removes JMeter behavior; all reflection has graceful fallback.

**Dependency direction (strict, no cycles):** `ui → core → storage → model`, and `core → config`.

### Class inventory

| Class | Package | Responsibility |
|-------|---------|----------------|
| `VersionEntry` | model | Single version record: number, file, timestamp, trigger, note, SHA-256. |
| `VersionIndex` | model | Root index. Returns `Collections.unmodifiable*` views; mutation only via `addVersion`, `removeVersionAt`, `removeVersion`, `removeVersions`, `addAllVersions`, `pin`, `unpin`. |
| `TriggerType` | model | `CHECKPOINT`, `AUTO_CHECKPOINT`, `RESTORE`. |
| `LockInfo` | model | Lock marker: `pid`, `hostname`, `timestamp`, `jmeterVersion`. |
| `ScmConfigManager` | config | Reads/writes `user.properties`. All getters self-heal (defaults on blank/missing). Property detection: line-anchored `Pattern.find()` with `Pattern.quote()`. |
| `FileOperations` | storage | Atomic copy/restore (temp + `Files.move(ATOMIC_MOVE)`), SHA-256, `extractStem()` (single source of truth for naming). |
| `IndexManager` | storage | `index.json` CRUD. Self-heal: parse failure → `.bak` + rebuild from `.jmxv` filenames. Validates `schemaVersion`. |
| `LockManager` | storage | Application-level lock (no `FileLock`). Ownership = hostname + PID. Timestamp-based stale detection. |
| `AuditLogger` | storage | JSON-lines `audit.log`, 1 MB rotation to `.1`. |
| `ScmContext` | core | Per-plan lifecycle. `volatile readOnly`/`disposed`. Disposed on new-open to prevent state leaks. |
| `SnapshotEngine` | core | Create/restore/delete. SHA-256 computed **outside** `synchronized(index)`. Dedup only for `AUTO_CHECKPOINT`. |
| `RetentionManager` | core | FIFO pruning after add. Skips pinned + latest. Delete failure → skip index removal (keeps disk/index in sync). |
| `DirtyTracker` | core | Hybrid flag + on-demand SHA-256. Checksum init deferred to `ScmContext.initialize()` via `reset()` (avoids blocking EDT). |
| `AutoSaveScheduler` | core | Cancellable `SwingWorker`; tracked and cancelled on `stop()`. |
| `ScmInitializer` | core | Singleton, lazy init, toolbar wiring. |
| `SaveCommandWrapper` | core | `ActionRouter` SAVE hook wrapped in `try-catch(Throwable)` — native save must never be blocked. |
| `ScmOpenListener` | core | OPEN / SUB_TREE_LOADED / CLOSE. Deferred `ensureInitializedWithContext()` via `invokeLater`. `testPlanFile == null` → dispose. |
| `VersionHistoryPanel` | ui | Bottom-dockable JTable. Retention-capacity warning banner. `JTable.getToolTipText(MouseEvent)` override for per-cell tooltips. |
| `ScmMenuCreator` | ui | JMeter `MenuCreator` entry point; deferred startup init. |
| `ScmMenuHandler` | ui | Tools menu submenu. |
| `DirtyIndicator` | ui | Toolbar status badge. |
| `CheckpointDialog` | ui | 500-char note limit, optional freeze checkbox (default unchecked). |
| `SettingsDialog` | ui | Custom `JDialog`: OK / Cancel / Reset to Defaults. Storage path validation. Storage change → Migrate/Reset/Cancel. |
| `AboutDialog` | ui | Version from JAR manifest. |
| `Toast`, `TimeFormatUtils` | ui | Transient notifications; time formatting. |

### Design decisions

- **Save Hook Isolation**: post-save wrapped in `try-catch(Throwable)`.
- **Atomic Writes**: all writes use temp + `Files.move(ATOMIC_MOVE)`.
- **Snapshot Dedup**: SHA-256 outside the index lock. `AUTO_CHECKPOINT` dedups; `CHECKPOINT` and `RESTORE` always create.
- **Self-Healing**: corrupt `index.json` → `.bak` + rebuild from disk. `schemaVersion != 1` logs a warning.
- **Existence-Only Validation**: `Files.exists()` per entry; no file reads. Sub-millisecond.
- **Monotonic Version Numbers**: never reset, even after retention pruning.
- **Per-Plan Lifecycle**: `ScmContext` is per-`.jmx`. Dispose previous on new-open.
- **Thread Safety**: `volatile` flags for cross-thread visibility; UI updates via `SwingUtilities.invokeLater`.
- **Config Self-Heal**: deleted `user.properties` → `ensureDefaultsPersisted()` recreates with manual-migration instructions.
- **Post-Restore Normalization**: save + `DirtyTracker.reset()` after reload. Restore note: `"Replaced by vN"`.

### Integration points (reflection)

| Touchpoint | What | Failure mode |
|------------|------|--------------|
| `ActionRouter` SAVE hook | Wrap `ActionNames.SAVE` | Caught; native save works |
| `MainFrame` toolbar | Insert 5 buttons (C, H, I, L, D) + separator | Toolbar absent; JMeter functional |
| `MainFrame` bottom panel | Access `JSplitPane` | Fallback to content pane; JMeter functional |
| `JMenuBar` Tools menu | Append Version Control submenu | Menu absent; JMeter functional |

### Lifecycle hooks

| Action | Handler | Behavior |
|--------|---------|----------|
| SAVE | `SaveCommandWrapper` | Bootstrap plugin, `ensureInitializedWithContext()` |
| OPEN / SUB_TREE_LOADED | `ScmOpenListener` | Deferred `ensureInitializedWithContext()` via `invokeLater` |
| CLOSE (File > New) | `ScmOpenListener` | Same deferred call. `testPlanFile == null` → dispose. Cancel-on-save safe. |

### Storage schema

- **Folder**: `<storageLocation>/<jmx-stem>/` per plan. Contains `index.json`, `.lock`, `<stem>_NNN.jmxv`.
- **index.json**: `schemaVersion: 1`, `maxRetention`, `storageLocation` (record-only — not used for resolution), `versions[]`, `pinnedVersions`.
- **.lock**: JSON (`pid`, `hostname`, `timestamp`, `jmeterVersion`).
- **audit.log**: JSON-lines, 1 MB rotation, single `.1` backup. Actions: `CHECKPOINT`, `AUTO_CHECKPOINT`, `RESTORE`, `DELETE`, `RETENTION_PRUNE`, `PIN`, `UNPIN`, `EXPORT`, `FORCE_RELEASE_LOCK`, `STORAGE_MIGRATE`, `STORAGE_RESET`.
- **Version file naming**: `<stem>_001.jmxv` — `%03d`, self-extends for ≥1000.

## Enforced invariants (do not violate)

- **`index.json` schema is public** — field renames are breaking changes. Bump `schemaVersion` if structure changes.
- **Storage location SSoT is `user.properties`** — `index.json.storageLocation` is a record, not a resolver. Runtime change requires restart; no hot-reload.
- **Retention floor**: cannot reduce below `frozen count + 1` (latest always preserved). Settings dialog must block with a message.
- **Delete guard**: latest and frozen versions cannot be deleted. `isSelectable()` is the single predicate for checkbox eligibility.
- **Restore safety**: append-only. Snapshot current state (RESTORE, note `"Replaced by vN"`) before restoring. Target copied to temp before auto-snapshot to survive pruning. Blocked at unprunable capacity (`isAtUnprunableCapacity()`).
- **Lock**: hostname + PID ownership (hostname resolved once at class load). `ensureWriteLock()` on every write op. L button: verify → polite acquire → force release with confirmation.
- **Missing snapshots**: `refresh()` detects via `Files.exists()`, disables Restore/Export with tooltip, WARN log. Delete cleans orphaned entries.
- **Storage migration**: GUI change → Migrate/Reset/Cancel. Migrate = dispose → move → reinit. Reset = zip old files to `<stem>_backup_<yyyyMMdd_HHmmss>.zip` in parent, delete originals, start fresh. Partial failures handled per file.
- **Export**: default filename `<jmx-name>_v<N>.jmx`, filter `"JMeter (.jmx)"`.
- **Read-only lock dialog**: shows hostname, PID, timestamp from `.lock`.

## Self-Maintenance

- **Ownership split**: `CLAUDE.md` = rules and context for Claude. `README.md` = user-facing features, install, config. When both need updating, change each in its own lane — do not duplicate content across files.
- **Auto-update CLAUDE.md**: after a session that changes design, architecture, invariants, or class responsibilities, review this file in the same session. Remove stale entries, dedupe, confirm every line still carries actionable information. After the update, perform **one** re-review pass (not recursive) and verify **100% accuracy** (every claim matches current code), **100% optimization** (no line can be tightened further), **0% redundancy** (no fact stated more than once).
- **Auto-update README.md**: after feature changes, keep README feature tables and config sections current. Apply the README update rules below.
- **Auto-compact**: suggest `/compact` before context becomes unwieldy.

### README update rules

When editing `README.md`, every change must satisfy:

1. **User-benefit framing** — describe features by what they do *for the user*, not by internal mechanics. Architectural terms ("pure additive", "self-healing", "reflection touchpoint") stay in CLAUDE.md.
2. **Features table = summary only** — one short line per feature. Defaults, config keys, keyboard shortcuts, and button names live only in Configuration / GUI Overview sections.
3. **Cross-platform shell blocks** — any command involving paths or env vars must show Linux/macOS, Windows PowerShell, and Windows cmd.
4. **Concrete paths for user actions** — when the user must open or edit a file, give the absolute path on each supported OS.
5. **Self-updating references over hardcoded strings** — prefer Maven Central / release badges to literal version numbers.
6. **Link CLAUDE.md, do not duplicate** — architecture, design decisions, and invariants live only in CLAUDE.md. README links to it from Contributing.
7. **Explicit auto-behavior** — when documenting auto-activation, auto-recovery, or auto-snapshot, state what is created and where.
