# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.x   | Yes       |
| < 1.0   | No        |

## Security Model

JVCS is a **local-only, single-user** JMeter plugin. It does not open network sockets, accept
remote input, or communicate with external services. All operations are local file I/O within
the user's file system permissions.

### Threat Surface

| Area              | Design                                                                                                                        |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------|
| **File I/O**      | Atomic temp + move writes. Storage paths resolved from `user.properties` and .jmx parent — no user-controlled path traversal. |
| **Lock files**    | Advisory locks (JSON with hostname + PID). Prevents accidental concurrent writes, not malicious access.                       |
| **Serialization** | Jackson with `FAIL_ON_UNKNOWN_PROPERTIES = false`. No polymorphic deserialization. Input limited to index.json and .lock.     |
| **Reflection**    | 4 touchpoints into JMeter MainFrame internals. All wrapped in try-catch with graceful fallback.                               |
| **Audit log**     | Append-only JSON-lines. Notes stored as plain text, never interpreted or executed.                                            |
| **Properties**    | Read via `JMeterUtils.getProperty()`. Type-validated with hardcoded defaults on failure.                                      |
| **No network**    | Zero outbound connections. No telemetry, no update checks, no remote storage.                                                 |

### What JVCS Does NOT Protect Against

- **Malicious local access**: An attacker with write access to the storage directory or
  `user.properties` can tamper with version history. JVCS operates within the user's own
  file system trust boundary.
- **Shared storage races**: The lock mechanism is advisory. Two processes can overwrite each
  other if one force-releases the lock while the other is writing.

## Reporting a Vulnerability

1. **Do not** open a public GitHub issue.
2. Use
   GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
   on this repository with a description, reproduction
   steps, and potential impact.

## Dependencies

All runtime dependencies are `provided` scope (on JMeter's classpath):

| Dependency        | Purpose            |
|-------------------|--------------------|
| ApacheJMeter_core | JMeter integration |
| jackson-databind  | JSON serialization |
| slf4j-api         | Logging            |

Thin JAR with no bundled transitive dependencies. Updates managed via Dependabot.
