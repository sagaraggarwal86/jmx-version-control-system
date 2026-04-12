# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 0.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in the BPM plugin,
please report it responsibly:

1. **Do not** open a public issue.
2. Email the maintainer at the address listed in `pom.xml`, or use GitHub's
   [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
   feature on this repository.

## Scope

This plugin captures browser performance metrics locally via Chrome DevTools Protocol.
It does not make outbound network calls, run a server, accept inbound connections,
or store credentials on disk.
