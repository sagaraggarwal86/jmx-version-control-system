# SCM JMeter Plugin

Lightweight local version control for JMeter test plans (.jmx files). Auto-snapshots on every save, maintains linear
version history, one-click rollback. A persistent undo stack across JMeter sessions — no Git, no SVN, no external tools.

## Features

| #  | Feature                  | Description                                                                   |
|----|--------------------------|-------------------------------------------------------------------------------|
| 1  | Auto-Snapshot on Save    | Automatically creates a version snapshot after every Ctrl+S save              |
| 2  | Version History Panel    | Bottom dockable panel showing tabular version list with actions               |
| 3  | Restore / Rollback       | One-click restore to any previous version. Auto-snapshots current state first |
| 4  | Max Version Retention    | Configurable limit (default 20). Oldest versions pruned automatically         |
| 5  | Dirty State Indicator    | Amber dot on toolbar + panel label when unsaved changes exist                 |
| 6  | Selective Version Delete | Delete individual versions (except latest). Confirmation dialog               |
| 7  | Version Note / Comment   | Optional text note on manual checkpoints                                      |
| 8  | Manual Checkpoint        | Create a named snapshot at any time via toolbar or menu                       |
| 9  | Export Version           | Export any version snapshot to a file location of your choice                 |
| 10 | Configurable Storage     | Choose where version history is stored (default: `.history` folder)           |
| 11 | Lock File Mechanism      | Prevents conflicts when multiple JMeter instances open the same plan          |
| 12 | Tools Menu Integration   | Tools > Version Manager > Open History, Create Checkpoint, Settings, About    |

## Requirements

- JMeter 5.6.3
- Java 17

## Installation

1. Build the plugin:
   ```bash
   mvn clean package -DskipTests
   ```
2. Copy `target/scm-jmeter-plugin-1.0.0-SNAPSHOT.jar` to JMeter's `lib/ext/` directory
3. Restart JMeter

## Usage

### Auto-Snapshots

Simply save your test plan (Ctrl+S). The plugin automatically creates a version snapshot after each save. Duplicate
saves (no changes) are skipped via checksum comparison.

### Manual Checkpoints

Click the **Checkpoint** button in the SCM toolbar group, or use **Tools > Version Manager > Create Checkpoint**. You
can add an optional note describing the checkpoint.

### Version History

Click **History** in the toolbar or **Tools > Version Manager > Open Version History** to open the bottom panel. The
panel shows all versions with:

- Version number
- Trigger type (SAVE or CHECKPOINT)
- Timestamp
- Note (if any)
- Action buttons: Restore, Export, Delete

### Restore

Click **Restore** on any version in the history panel. The plugin will:

1. Auto-snapshot your current state (safety net)
2. Atomically restore the selected version
3. Prompt you to reload the test plan

### Settings

Configure via **Tools > Version Manager > Settings**:

- **Storage Location**: Where to store version history (default: `.history`)
- **Max Retention**: Maximum number of versions to keep (default: 20)
- **Stale Lock Timeout**: Minutes before a lock is considered stale (default: 60)

Global defaults can be set in `jmeter.properties`:

```properties
scm.storage.location=.history
scm.max.retention=20
scm.lock.stale.minutes=60
```

## Storage Structure

```
my-test-plan.jmx              ← working file
.history/                      ← version storage
    index.json                 ← version metadata
    .lock                      ← concurrency guard
    v001.jmxv                  ← version snapshots
    v002.jmxv
    ...
```

## Uninstall

Remove the JAR from `lib/ext/`. Your `.history` folders remain intact — version snapshots are preserved as user data.

## Building

```bash
mvn clean verify           # Build + run tests
mvn clean package          # Build JAR only
```
