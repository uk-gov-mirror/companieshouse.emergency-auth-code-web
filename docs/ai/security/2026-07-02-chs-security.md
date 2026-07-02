# Security Review Report — `emergency-auth-code-web`

**Repository:** `companieshouse/emergency-auth-code-web`
**Review date:** 2 July 2026
**Service type:** Java 21 / Spring Boot 3.5.x / Thymeleaf / GOV.UK Frontend (ECS)
**Reviewer:** CHS Security Review Agent
**Scope:** Full codebase review — dependencies, secrets, OWASP Top 10, GDPR, access control

---

## Executive Summary

No CRITICAL findings were identified. The service has a solid foundation: no hardcoded secrets, correct actuator lockdown, properly managed external credentials (Vault + SSM), and consistently safe Thymeleaf templating (`th:text` throughout). The most significant issue is that **CSRF protection is globally disabled without a compensating `SameSite` cookie control**. This is rated HIGH. Four MEDIUM findings and four LOW findings round out the report.

---

## Findings

### 🔴 HIGH — CSRF Protection Disabled with No `SameSite` Mitigation

**File:** `src/main/java/.../security/WebSecurity.java`
**Also:** `src/main/resources/application.properties`

Both Spring Security filter chains explicitly disable CSRF:
```java
.csrf().disable()
```
`server.servlet.session.cookie.same-site` is **not configured**, meaning the browser will attach the session cookie to any cross-origin form submission to the CHS domain.

**Impact:** An attacker who can lure a signed-in user to a malicious page could trigger a fraudulent auth code request, causing an authentication code to be dispatched to an officer's home address.

**Recommendation:** Add to `application.properties`:
```properties
server.servlet.session.cookie.same-site=Strict
```

---

### 🟠 MEDIUM — `requestId` Has No Client-Side Ownership Verification (IDOR Risk)

**Files:** `ListOfOfficersController.java`, `OfficerConfirmationPageController.java`, `ConfirmationPageController.java`

All three controllers accept `{requestId}` as a URL path variable with no check that the request belongs to the currently authenticated user. Ownership enforcement is entirely delegated to the backend API.

**Recommendation:** Verify the backend API returns `403` when a user accesses a request they do not own. Add an integration test confirming a second user cannot access the first user's `requestId`.

---

### 🟠 MEDIUM — No Content Security Policy Header

**File:** `src/main/java/.../security/WebSecurity.java`

Neither filter chain configures a `Content-Security-Policy` header. Spring Security does not add one by default.

**Recommendation:** Add CSP to both filter chains using the lambda-based API:
```java
.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; script-src 'self' <cdn-host>; style-src 'self' <cdn-host>; img-src 'self' data:; frame-ancestors 'none'"))
)
```

---

### 🟠 MEDIUM — Missing `.dockerignore`

**File:** Project root

No `.dockerignore` exists. Local `docker build` invocations send the full build context — including any `.env` files, `.git` history, and IDE settings — to the Docker daemon.

**Recommendation:** Create a `.dockerignore` excluding at minimum:
```
.env
.git
.idea
*.iml
target/
*.jar
src/test/
```

---

### 🟠 MEDIUM — Session Cookie `SameSite` Attribute Not Set

**File:** `src/main/resources/application.properties`

`server.servlet.session.cookie.same-site` is absent. Spring Boot's default is no `SameSite` attribute.

**Recommendation:** Add `server.servlet.session.cookie.same-site=Strict`.

---

### 🟡 LOW — Deprecated Spring Security Fluent API

**File:** `src/main/java/.../security/WebSecurity.java`

Both filter chains use the pre-Spring Security 6 method-chaining DSL (deprecated since 5.8). Migrate to lambda DSL:
```java
http
    .csrf(AbstractHttpConfigurer::disable)
    .addFilterBefore(new SessionHandler(), BasicAuthenticationFilter.class)
```

---

### 🟡 LOW — `commons-text` Pinned at Stale Version (1.10.0)

**File:** `pom.xml`

`commons-text:1.10.0` (November 2022) is declared with an explicit version rather than being managed by the CH BOM. Remove the explicit version pin and delegate to `ch-dependency-bom`, or update to the latest release.

---

### 🟡 LOW — No Rate Limiting on Auth Code Submission Endpoints

**Files:** `OfficerConfirmationPageController.java`, `CompanyConfirmationPageController.java`

No rate limiting in application code. Confirm with the platform team that ERIC or the ALB applies request-rate limiting for authenticated users.

---

### 🟡 LOW — `UserDetailsInterceptor` Will NPE on Missing User Profile

**File:** `src/main/java/.../interceptor/UserDetailsInterceptor.java`

If `signInInfo` is non-null but `user_profile` is null (e.g., malformed session), `userProfile.get(EMAIL_KEY)` throws `NullPointerException`. Add a null check on `userProfile`.

---

## Passed / Positive Controls

| Area | Finding |
|---|---|
| **Hardcoded secrets** | ✅ None found. All credentials injected via environment variables / SSM. |
| **Actuator security** | ✅ All endpoints disabled by default; only `/healthcheck` exposed with `show-details=never`. |
| **XSS (Thymeleaf)** | ✅ All templates exclusively use `th:text`. No `th:utext` anywhere. |
| **Injection** | ✅ No SQL/LDAP/command injection. All URIs built via `UriTemplate.expand()`. |
| **Deserialization** | ✅ No unsafe deserialization patterns. |
| **Cookie `Secure` flag** | ✅ `server.servlet.session.cookie.secure=true` set. |
| **Cookie `HttpOnly` flag** | ✅ Spring Boot defaults to `HttpOnly=true`. |
| **Error handling** | ✅ `GlobalExceptionHandler` returns generic error view — no stack traces to client. |
| **Log injection / PII** | ✅ Structured logging; no officer names, emails, or addresses logged. |
| **Terraform secrets** | ✅ Secrets via Vault at plan time; injected as ECS `secrets` (SSM ARN refs), not plaintext env vars. |
| **Dependency management** | ✅ Active CVE remediation (ASM-2145). Migration to CH BOM centralises version governance. |

---

## GDPR Assessment

| Concern | Assessment |
|---|---|
| **PII in logs** | ✅ PASS — Officer names, DOB, addresses not logged. |
| **PII in responses** | ✅ PASS — Officer DOB partially masked (month + year only). Home addresses never returned to frontend. |
| **Data minimisation** | ✅ PASS — Only company number and officer selection collected. |
| **Session isolation** | ✅ PASS — Per-user session tokens via `java-session-handler`. |
| **Encryption in transit** | ✅ PASS — `Secure` cookie flag set; service behind HTTPS ALB. |
| **User email in model** | ℹ️ INFO — `UserDetailsInterceptor` adds authenticated user's email to all model views. Verify `Cache-Control: no-store` is applied by ERIC or the base layout. |

---

## Summary Table

| Severity | Count | Findings |
|---|---|---|
| 🔴 CRITICAL | 0 | — |
| 🔴 HIGH | 1 | CSRF disabled + no SameSite cookie |
| 🟠 MEDIUM | 4 | IDOR (`requestId`), no CSP, no `.dockerignore`, SameSite absent |
| 🟡 LOW | 4 | Deprecated Security API, `commons-text` version, no rate limiting, NPE in `UserDetailsInterceptor` |
| ℹ️ INFO | 1 | User email in model / cache-control |

**Overall verdict:** Deployment is not blocked. The HIGH finding (CSRF + SameSite) should be addressed before the next release. MEDIUM findings should be resolved within the current sprint.
