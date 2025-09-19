# Security Policy

## Supported Versions
Only the `main` branch is actively maintained.  
Security fixes will be applied to the latest release only.

## Automated Security Scanning

This project uses **GitHub Actions** for continuous security monitoring and code quality assurance:

### Security Workflow (`.github/workflows/security.yml`)
- **Static Code Analysis**: PMD security and best practices rules
- **Dependency Scanning**: Maven and npm vulnerability detection
- **CodeQL Analysis**: Automated security vulnerability detection for Java and JavaScript
- **Secret Scanning**: Detection of accidentally committed secrets
- **Build Security**: Java 21 + Spring Boot security validation

### Code Quality Tools
- **PMD**: Static analysis for security vulnerabilities and code quality issues
- **SpotBugs**: Bug detection including security-related issues
- **Maven Security**: Dependency vulnerability scanning
- **Log4j Security**: Proper logging practices to prevent information disclosure

### Security Reports
Security scan results are available in:
- GitHub Actions tab → Security workflow runs
- PMD reports: Generated during CI/CD pipeline
- Dependency scan results: Available in security tab

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
  - Log4j security vulnerabilities (addressed via proper logging practices)

- **WebSocket layer**  
  - Message flooding or replay attacks  
  - Unauthorized access to private game sessions  
  - MCP (Model Context Protocol) security vulnerabilities

- **AI engine integrations**  
  - Unsafe handling of engine subprocesses  
  - Resource exhaustion (CPU/GPU overuse)  
  - Potential escape from sandboxed execution  
  - Training data security and privacy

- **Frontend (browser)**  
  - Cross-site scripting (XSS)  
  - Session hijacking  
  - Manipulation of move sequences or state sync

- **Code Quality & Security**  
  - PMD security rule violations  
  - SpotBugs security warnings  
  - Dependency vulnerabilities  
  - Information disclosure through logging  

## Out of Scope
- UI/UX improvements or feature requests  
- Engine evaluation accuracy (e.g., "AI plays poorly")  
- Local misconfiguration issues not related to the codebase  

## Security Monitoring & Compliance

### Continuous Security
- **Automated Scanning**: Every push and pull request triggers security scans
- **Dependency Updates**: Regular monitoring of vulnerable dependencies
- **Code Quality**: PMD and SpotBugs ensure security best practices
- **Logging Security**: Proper Log4j implementation prevents information disclosure

### Security Metrics
- **PMD Violations**: Currently 0 SystemPrintln warnings (all converted to proper logging)
- **Dependency Health**: Maven and npm audit results available in CI/CD
- **Code Coverage**: Security-focused test coverage monitoring
- **Vulnerability Response**: 7-day acknowledgment, 30-day remediation target

## Disclosure Policy
We follow a **responsible disclosure** model:
1. You report privately.  
2. We confirm and investigate the issue.  
3. We develop and test a fix.  
4. We publish a security advisory and acknowledge your contribution.  

### Security Contact
- **Primary**: GitHub Security Advisories
- **Response Time**: 7 days acknowledgment, 30 days remediation
- **Scope**: All security vulnerabilities in code, dependencies, and infrastructure

Thank you for helping keep this project secure!
