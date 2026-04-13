# JVCS — JMX Version Control System

A lightweight local version control plugin for Apache JMeter test plans (.jmx files). Auto-snapshots
on every save, linear version history, one-click rollback. A persistent undo stack across JMeter
sessions — no Git, no SVN, no external tools.

---

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [GUI Overview](#gui-overview)
- [Configuration](#configuration)
- [Storage Structure](#storage-structure)
- [Known Limitations](#known-limitations)
- [Troubleshooting](#troubleshooting)
- [Uninstall](#uninstall)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Feature                    | Description                                                                              |
|----------------------------|------------------------------------------------------------------------------------------|
| **Auto-Snapshot on Save**  | Creates a version snapshot after every Ctrl+S. Duplicate saves skipped via SHA-256 dedup |
| **Manual Checkpoint**      | Named snapshot with optional note (500 chars) and optional freeze. Toolbar C or Ctrl+K   |
| **Version History Panel**  | Bottom dockable panel with version list, actions, retention warning banner               |
| **Restore / Rollback**     | One-click restore to any version. Auto-snapshots current state first (safety net)        |
| **Freeze (Pin) Versions**  | Frozen versions are exempt from retention pruning and bulk deletion                      |
| **Auto-Checkpoint**        | Configurable periodic auto-save + snapshot (disabled by default)                         |
| **Selective Deletion**     | Header checkbox select-all + Delete Versions. Latest and frozen versions protected       |
| **Export Version**         | Export any snapshot as a .jmx file to a location of your choice                          |
| **Configurable Retention** | Max versions per plan (default 20). FIFO pruning of oldest unpinned versions             |
| **Configurable Storage**   | Relative or absolute storage path. GUI migration with Migrate/Reset/Cancel dialog        |
| **Lock File Mechanism**    | Hostname + PID ownership. Stale detection. Force release with confirmation               |
| **Dirty State Indicator**  | Toolbar indicator: green (clean), amber (dirty), red (read-only)                         |
| **Toolbar Buttons**        | C (Checkpoint), H (History), I (Indicator), L (Lock), D (Delete). Visibility togglable   |
| **Keyboard Shortcuts**     | Ctrl+K (Checkpoint), Ctrl+H (Toggle History)                                             |
| **Audit Log**              | JSON-lines audit trail of all actions. 1MB rotation with single backup                   |
| **Tools Menu Integration** | Tools > Version Control > History, Checkpoint, Settings, About                           |
| **Self-Healing**           | Corrupt index.json auto-recovered from .jmxv filenames on disk                           |
| **Pure Additive**          | Never modifies JMeter behavior. All reflection with graceful fallback                    |

---

## Requirements

| Requirement   | Version             |
|---------------|---------------------|
| Java          | 17                  |
| Apache JMeter | 5.6.3               |
| Maven         | 3.8+ *(build only)* |

---

## Installation

### Manual JAR

1. Download the latest JAR from
   [Maven Central](https://central.sonatype.com/artifact/io.github.sagaraggarwal86/jmx-version-control-system)
   or the [GitHub Releases](https://github.com/sagaraggarwal86/jmx-version-control-system/releases) page.

2. Copy it to your JMeter `lib/ext/` directory:
   ```
   <JMETER_HOME>/lib/ext/jmx-version-control-system-<version>.jar
   ```

3. Restart JMeter.

### Build from Source

```bash
git clone https://github.com/sagaraggarwal86/jmx-version-control-system.git
cd jmx-version-control-system
mvn clean verify
cp target/jmx-version-control-system-*.jar $JMETER_HOME/lib/ext/
```

---

## Quick Start

1. Open any test plan (.jmx file) in JMeter.
2. Save the test plan (Ctrl+S) — a version snapshot is created automatically.
3. Click **H** in the toolbar (or Ctrl+H) to open the Version History panel.
4. Make changes, save again — each save creates a new version.
5. Click **Restore** on any version to roll back. Your current state is auto-saved first.

Zero configuration required. JVCS activates automatically on first save or file open.

---

## GUI Overview

### Toolbar Buttons

| Button | Action                | Shortcut |
|--------|-----------------------|----------|
| **C**  | Create Checkpoint     | Ctrl+K   |
| **H**  | Toggle History Panel  | Ctrl+H   |
| **I**  | Dirty State Indicator | —        |
| **L**  | Lock Status / Acquire | —        |
| **D**  | Delete Versions       | —        |

Toolbar visibility can be toggled in Settings.

### Version History Panel

Bottom dockable panel showing all version snapshots:

- **Version** — auto-incrementing, never resets
- **Trigger** — CHECKPOINT, AUTO_CHECKPOINT, or RESTORE
- **Timestamp** — relative time (e.g., "3m ago") with full timestamp tooltip
- **Note** — user note or "Current version" for latest
- **Actions** — Restore, Export, Delete (disabled for latest, frozen, or missing snapshots)

**Header checkbox** selects all eligible (non-frozen, non-latest) versions for bulk deletion.

**Retention warning banner** appears in bold red when version count reaches max retention and all
non-latest versions are frozen (no versions can be pruned).

### Lock Button (L)

The L button serves three purposes depending on lock state:

| State         | Click Behavior                                       |
|---------------|------------------------------------------------------|
| Lock active   | Shows "Lock active" tooltip                          |
| Read-only     | Attempts polite lock acquisition (succeeds if stale) |
| Held by other | Shows hostname, PID, timestamp; offers force release |

### Dirty Indicator (I)

| Color | Meaning                    |
|-------|----------------------------|
| Green | Clean — no unsaved changes |
| Amber | Dirty — unsaved changes    |
| Red   | Read-only mode             |

---

## Configuration

### Settings Dialog

Access via **Tools > Version Control > Settings** or configure in `user.properties`.

| Setting            | Property                        | Default    | Description                               |
|--------------------|---------------------------------|------------|-------------------------------------------|
| Storage Location   | `scm.storage.location`          | `.history` | Relative (to .jmx) or absolute path       |
| Max Retention      | `scm.max.retention`             | `20`       | Max versions per test plan                |
| Stale Lock Timeout | `scm.lock.stale.minutes`        | `60`       | Minutes before a lock is considered stale |
| Auto-Save Enabled  | `scm.autosave.enabled`          | `false`    | Enable periodic auto-checkpoint           |
| Auto-Save Interval | `scm.autosave.interval.minutes` | `5`        | Auto-checkpoint interval in minutes       |
| Toolbar Visible    | `scm.toolbar.visible`           | `true`     | Show/hide SCM toolbar buttons             |

`user.properties` is the single source of truth for all settings. Default values are written
automatically on first plugin activation with inline documentation.

### Storage Location Change

Changing storage location in the Settings dialog triggers a three-option dialog:

| Option      | Behavior                                                            |
|-------------|---------------------------------------------------------------------|
| **Migrate** | Moves all version files to the new location                         |
| **Reset**   | Backs up old files to `<stem>_backup_<timestamp>.zip`, starts fresh |
| **Cancel**  | Reverts the change                                                  |

> [!NOTE]
> Editing `scm.storage.location` directly in `user.properties` requires a JMeter restart. Use the
> Settings dialog for live migration.

### Retention Reduction

Reducing max retention below the current version count triggers a confirmation dialog showing
exactly how many versions will be deleted. Retention cannot be reduced below `frozen count + 1`
(latest version always preserved).

---

## Storage Structure

```
my-test-plan.jmx                    <-- working file
.history/                            <-- storage root
  my-test-plan/                      <-- per-plan subdirectory
    index.json                       <-- version metadata (schema v1)
    .lock                            <-- concurrency guard (hostname + PID)
    audit.log                        <-- action audit trail (JSON-lines)
    my-test-plan_001.jmxv            <-- version snapshots
    my-test-plan_002.jmxv
    ...
```

- **index.json** — `schemaVersion`, `maxRetention`, `storageLocation` (record only), `versions[]`,
  `pinnedVersions`. Schema version validated on load.
- **.lock** — JSON with `pid`, `hostname`, `timestamp`, `jmeterVersion`. Ownership verified by
  hostname + PID match.
- **audit.log** — JSON-lines format. 1MB rotation with single `.1` backup. Actions: CHECKPOINT,
  AUTO_CHECKPOINT, RESTORE, DELETE, RETENTION_PRUNE, PIN, UNPIN, EXPORT, FORCE_RELEASE_LOCK,
  STORAGE_MIGRATE, STORAGE_RESET.
- **.jmxv** — Binary copy of the .jmx file at snapshot time. Naming: `<stem>_NNN.jmxv` (zero-padded,
  self-extends at 1000+).

---

## Known Limitations

- **Single-user design**: Lock mechanism is advisory (application-level, not OS-level). Not designed
  for concurrent multi-user access to the same storage directory.
- **JMeter 5.6.3 only**: Reflection-based integration with MainFrame internals. Other JMeter versions
  may work but are untested.
- **No branching**: Linear version history only. No merge, no diff, no branch.
- **No remote storage**: Local disk only. Network drives work but lock stale detection relies on
  system clock consistency.

---

## Troubleshooting

| Problem                              | Solution                                                                                   |
|--------------------------------------|--------------------------------------------------------------------------------------------|
| Plugin not visible after install     | Verify JAR is in `<JMETER_HOME>/lib/ext/`. Restart JMeter.                                 |
| Toolbar buttons missing              | Check `scm.toolbar.visible=true` in `user.properties`. Reflection may fail on non-5.6.3.   |
| "Read-Only Mode" dialog on open      | Another JMeter instance holds the lock. Use L button to force release if the other closed. |
| History panel empty                  | Save the test plan at least once. New/unsaved plans have no history.                       |
| Restore button disabled              | Snapshot file missing from disk. Delete the orphaned entry to clean up.                    |
| "Cannot reduce retention" error      | Unfreeze some versions first. Floor = frozen count + 1.                                    |
| Versions not pruned at max retention | All non-latest versions are frozen. Unfreeze to allow FIFO pruning.                        |

---

## Uninstall

Remove the JAR from `lib/ext/`. Your `.history` folders remain intact — version snapshots are
preserved as user data. Delete them manually if no longer needed.

---

## Contributing

Bug reports and pull requests are welcome via
[GitHub Issues](https://github.com/sagaraggarwal86/jmx-version-control-system/issues).

Before submitting a pull request:

```bash
mvn clean verify          # All tests must pass
```

- Test manually with JMeter 5.6.3
- Keep each pull request focused on a single change

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
