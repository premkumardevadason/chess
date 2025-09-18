# Security Policy

## Supported Versions
Only the `main` branch is actively maintained.  
Security fixes will be applied to the latest release only.

## Reporting a Vulnerability
If you discover a security vulnerability in this project:

- **Do not open a public GitHub issue.**
- Instead, report it privately via:
  - GitHub's [Security Advisories](https://github.com/premkumardevadason/chess/security/advisories)

We will acknowledge your report within **7 days** and provide a timeline for investigation and remediation.

## Scope of Security Concerns
We are particularly interested in reports related to:
- **Backend (Spring Boot)**  
  - Injection attacks (SQL, command, deserialization)  
  - Authentication/session handling weaknesses  
  - Denial of service via crafted requests  

- **WebSocket layer**  
  - Message flooding or replay attacks  
  - Unauthorized access to private game sessions  

- **AI engine integrations**  
  - Unsafe handling of engine subprocesses  
  - Resource exhaustion (CPU/GPU overuse)  
  - Potential escape from sandboxed execution  

- **Frontend (browser)**  
  - Cross-site scripting (XSS)  
  - Session hijacking  
  - Manipulation of move sequences or state sync  

## Out of Scope
- UI/UX improvements or feature requests  
- Engine evaluation accuracy (e.g., "AI plays poorly")  
- Local misconfiguration issues not related to the codebase  

## Disclosure Policy
We follow a **responsible disclosure** model:
1. You report privately.  
2. We confirm and investigate the issue.  
3. We develop and test a fix.  
4. We publish a security advisory and acknowledge your contribution.  

Thank you for helping keep this project secure!
