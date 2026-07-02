# Code Review Report — `emergency-auth-code-web`

**Repository:** `companieshouse/emergency-auth-code-web`  
**Service type:** Java 21 · Spring Boot 3 · Spring MVC · Thymeleaf · GOV.UK Frontend  
**Standards applied:** `core.md`, `java-web.md`, CHS [Java styleguide](https://github.com/companieshouse/styleguides/blob/main/standards/java.md)  
**Review date:** 2026-07-02  
**Reviewer:** CHS Reviewer Agent  

---

## Summary

| Severity | Count |
|---|---|
| CRITICAL | 0 |
| HIGH | 6 |
| MEDIUM | 10 |
| LOW | 5 |
| INFO | 3 |

No CRITICAL findings block deployment. However the six HIGH findings should be resolved before the next significant release; several introduce real runtime crash risk.

---

## HIGH Findings

---

### H-1 · Field injection (`@Autowired` on fields) used pervasively throughout the codebase

**Files:** `BaseController.java`, all controllers, `NavigatorService.java`, `CompanyServiceImpl.java`, `EmergencyAuthCodeServiceImpl.java`, `UserDetailsInterceptor.java`

Field injection makes classes impossible to unit-test without a running Spring context, hides dependencies, and prevents the compiler from enforcing that all required collaborators are provided. The CHS Java standard and [companieshouse/styleguides](https://github.com/companieshouse/styleguides/blob/main/standards/java.md) require constructor injection throughout.

```java
// BaseController.java — field injection on shared base class
@Autowired
protected NavigatorService navigatorService;

// EmergencyAuthCodeServiceImpl.java — four @Autowired fields
@Autowired
private ApiClientService apiClientService;
@Autowired
private EACRequestTransformer eacRequestTransformer;
// ...
```

Every `@Autowired` field should be replaced with a constructor parameter. `BaseController` is abstract and cannot be instantiated directly, so it should declare a protected constructor that accepts `NavigatorService` and passes it in from each concrete subclass constructor.

---

### H-2 · `formatFullName()` in `EACOfficerTransformer` will throw `NullPointerException` when `name` is null

**File:** `src/main/java/.../transformer/emergencyauthcode/officer/EACOfficerTransformer.java`, line 29

```java
static String formatFullName(String name) {
    if(name.isEmpty()) {   // NPE if name is null
        return "";
    }
    ...
}
```

The API does not guarantee that an officer has a name. If `name` is `null`, calling `.isEmpty()` throws a `NullPointerException` which will surface as an unhandled 500 to the user and pollute error logs. The fix is `if (name == null || name.isEmpty())`.

---

### H-3 · `extractIdFromSelfLink()` has no null guard — NPE if API returns no `self` link

**File:** `src/main/java/.../controller/eac/CompanyConfirmationPageController.java`, lines 110–113

```java
private String extractIdFromSelfLink(Map<String, String> links) {
    String requestSelfLink = links.get(SELF_KEY);  // returns null if key absent
    int index = requestSelfLink.lastIndexOf('/');   // NPE here
    return requestSelfLink.substring(index + 1);
}
```

`Map.get()` returns `null` when the key is absent. If the API response ever omits the `self` link, this will throw a `NullPointerException` inside the `try/catch (ServiceException)` block which will not be caught (NPE is a `RuntimeException`, not a `ServiceException`), propagating to the `GlobalExceptionHandler`. The fix is to add an explicit null/empty check and throw a `ServiceException` with a meaningful message.

---

### H-4 · PRG pattern violated: `cannotUseThisService` template rendered without redirect inside POST handlers

**Files:** `ListOfOfficersController.java` line 97, `OfficerConfirmationPageController.java` lines 55 & 94

```java
// In a @PostMapping
if (eacRequest.getStatus().equals(SUBMITTED_STATUS)) {
    return CANNOT_USE_THIS_SERVICE;  // renders view, URL stays at POST endpoint
}
```

When a POST handler returns a view name directly (instead of a redirect), the browser URL stays at the POST endpoint. Refreshing the page will replay the POST. This violates the mandatory Post-Redirect-Get pattern required by the Java web standard. These should redirect to a dedicated GET endpoint (e.g. `/auth-code-requests/company/{companyNumber}/cannot-use-this-service`), which already exists via `CannotUseThisServiceController`.

---

### H-5 · All user-visible strings hardcoded in templates and Java code; locale files are empty

**Files:** All Thymeleaf templates, `BaseController.java`, `CompanyConfirmationPageController.java`

```properties
# src/main/resources/locales/messages.properties — 0 bytes
# src/main/resources/locales/messages_cy.properties — 0 bytes
```

Every user-visible string — page titles, headings, body copy, button labels, error messages — is hardcoded directly in the templates or in Java constants (`TEMPLATE_HEADING = "Confirm company details"` in the controller). The Java web standard requires all user-visible text to live in `messages.properties` and `messages_cy.properties` and to be referenced with `#{key}` in templates. Welsh language support is a legal obligation. The locale files exist but are empty, suggesting this requirement has never been addressed.

> **Note on `ValidationMessages.properties`:** the two bean validation messages (`officer.selectionNotMade`, `officer.confirmationNotMade`) are correctly externalised here, but this does not substitute for full i18n coverage.

---

### H-6 · Pagination implementation is a known WCAG 2.5.8 failure and the remediation deadline has passed

**Files:** `src/main/resources/templates/eac/listOfOfficers.html`, `accessibilityStatement.html`

The accessibility statement acknowledges: *"Pagination links do not meet a minimum size… We plan to fix this issue by December 2024."* That deadline has now passed. The current pagination is a custom `<div class="pagination">` with inline styles and does not use the GOV.UK Pagination component. This is an active WCAG 2.5.8 non-compliance on a public-facing service under the Public Sector Bodies Accessibility Regulations 2018.

```html
<!-- Custom pagination with inline styles — not the GOV.UK component -->
<a style="font-size: 19px;padding: 8px 16px;" class="govuk-link" th:href="...">Previous</a>
```

---

## MEDIUM Findings

---

### M-1 · `@Deprecated` Spring Security CSRF fluent API used; will produce compiler warnings

**File:** `src/main/java/.../security/WebSecurity.java`

```java
http.securityMatcher(...)
    .csrf().disable()  // deprecated in Spring Security 6
```

Spring Security 6 (included via Spring Boot 3.x) deprecated the `.csrf()` fluent style in favour of `.csrf(csrf -> csrf.disable())`. The current syntax produces deprecation warnings in the compiler output. The service's own `copilot-instructions.md` documents that CSRF is intentionally disabled at the gateway layer — this finding is about the deprecated API style, not the CSRF policy itself.

---

### M-2 · `th:id` attributes in `officerConfirmation.html` set to data values — invalid HTML and unnecessary PII in DOM

**File:** `src/main/resources/templates/eac/officerConfirmation.html`

```html
<dd th:id="${eacOfficer.id}" th:text="${eacOfficer.name}"></dd>
<dd th:id="${eacOfficer.officerRole}" th:text="${eacOfficer.officerRole}"></dd>
<dd th:id="${eacOfficerDOBMonth} + ' ' + ${eacOfficer.dateOfBirth.year}" th:text="..."></dd>
<dd th:id="${eacOfficer.nationality}" th:text="${eacOfficer.nationality}"></dd>
```

`id` attributes must be unique within a page and are intended for element identification, not for mirroring data values. Setting `id` to DOB month/year or nationality embeds PII in the DOM attribute unnecessarily. Additionally `id` values with spaces (e.g. `"JANUARY 1990"`) are invalid HTML (the `id` attribute must not contain spaces per HTML5). These `th:id` bindings should be removed entirely.

---

### M-3 · `cannotUseThisService.html` and `confirmationPage.html` contain unused `<form method="post">` wrappers

**Files:** `src/main/resources/templates/eac/cannotUseThisService.html`, `confirmationPage.html`

Both templates wrap their content in `<form th:action="@{''}" method="post">` but neither page has a POST controller mapping. These forms are never submitted. The form wrappers are misleading (a `<button type="submit">` or accidental enter-key press would POST to the same URL and receive a 405), add unnecessary DOM structure, and could confuse accessibility tools. Remove the `<form>` wrappers; use plain `<div>` containers.

---

### M-4 · Invalid HTML: `<p>` elements placed inside `<ul>` in multiple templates

**Files:** `startPage.html` (lines ~41, ~48), `cannotUseThisService.html` (line ~44)

```html
<ul class="govuk-list govuk-list--bullet">
    <li>...</li>
    <p></ul>  <!-- <p> inside <ul> is invalid -->
```

`<p>` is not a permitted child of `<ul>`; only `<li>`, `<script>`, and `<template>` are. Browsers will attempt to repair the DOM in inconsistent ways, which can cause screen readers to misinterpret list structure. These should be `</ul><p>...</p>` or simply removed if they contain no content.

---

### M-5 · Error message for confirmation checkbox not linked with `aria-describedby`

**File:** `src/main/resources/templates/eac/officerConfirmation.html`

```html
<span class="govuk-error-message"
      th:if="${#fields.hasErrors('confirm')}"
      th:each="e : ${#fields.errors('confirm')}" th:text="${e}">
</span>
<div class="govuk-checkboxes__item">
    <input ... th:field="*{confirm}">  <!-- no aria-describedby -->
```

The error message `<span>` is not referenced by the checkbox `<input>` via `aria-describedby`. Screen readers will not associate the error with the control. The GOV.UK Frontend checkboxes component handles this automatically when used correctly; the error span should have an `id` (e.g. `confirm-error`) and the input should carry `aria-describedby="confirm-error"`.

---

### M-6 · Accessibility statement has incorrect page title

**File:** `src/main/resources/templates/eac/accessibilityStatement.html`, line 8

```html
<title>Request an authentication code to be sent to a home address</title>
```

This is identical to the start page title. WCAG 2.4.2 requires page titles to be descriptive and unique. The title should be something like `"Accessibility statement — Request an authentication code to be sent to a home address"`.

---

### M-7 · Company type/status validation logic belongs in the service layer, not the controller

**File:** `src/main/java/.../controller/eac/CompanyConfirmationPageController.java`

```java
private static final List<String> ACCEPTED_TYPES = new ArrayList<>(Arrays.asList(...));
private static final String ACCEPTED_STATUS = "Active";

// inside @PostMapping
if(!ACCEPTED_STATUS.equals(companyDetail.getCompanyStatus()) || !ACCEPTED_TYPES.contains(companyDetail.getType())){
    return getCannotUseThisServiceView(companyNumber);
}
```

The Java web standard requires controllers to be thin — request binding, validation, model population, and view selection only. Business rules (what company types and statuses are eligible for an EAC request) belong in `EmergencyAuthCodeService` or a dedicated eligibility service, not as static constants and `if`-statements in the controller.

---

### M-8 · `EACRequest` model added to template contains `userEmail` field

**File:** `src/main/java/.../controller/eac/OfficerConfirmationPageController.java`, line 67

```java
model.addAttribute("eacRequest", eacRequest);
```

`EACRequest` has a `userEmail` field populated by the API response. Adding the full domain model to the view exposes `eacRequest.userEmail` (and `eacRequest.userId`) to the Thymeleaf template scope and any tooling that serialises model attributes (e.g. error debugging pages). A dedicated view model/DTO containing only the fields required for the template (`officerName`, `companyNumber`, `officerId`) would prevent accidental exposure.

---

### M-9 · `Collectors.toList()` used in Java 21 code; should use `.toList()`

**File:** `src/main/java/.../controller/eac/ListOfOfficersController.java`, line 59

```java
List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages)
        .boxed()
        .collect(Collectors.toList());  // returns a mutable list, verbose
```

Since Java 16, `Stream.toList()` is the idiomatic replacement. It returns an unmodifiable list, which is appropriate here, and removes the unnecessary `.boxed()` → `Collectors.toList()` chain.

---

### M-10 · No tests for `GlobalExceptionHandler`, `WebSecurity`, or `EACStartController`

**Files:** `src/test/java/.../`

`GlobalExceptionHandler` has no test class — the handler that catches all `RuntimeException` and returns the error view is untested. `WebSecurity` filter chain configuration is untested (no verification that authenticated routes reject unauthenticated requests). `EACStartControllerTest` is absent (GET/POST paths for the start page have no test coverage).

The Java web standard requires every authentication-required route to have a test verifying unauthenticated access redirects to sign-in. As the tests use `MockMvcBuilders.standaloneSetup()` (bypassing Spring Security), this is not currently achievable without adding `@WebMvcTest` tests with a mock security configuration for those cases.

---

## LOW Findings

---

### L-1 · `javascript:window.print()` in `confirmationPage.html` may be blocked by Content Security Policy

**File:** `src/main/resources/templates/eac/confirmationPage.html`, line 58

```html
<a class="govuk-link" href="javascript:window.print()">Print this page for your records</a>
```

`javascript:` URIs in `href` are blocked by any CSP that does not include `'unsafe-inline'` for scripts. Additionally, `<a>` is semantically a link (navigation) not a button; a `<button type="button" onclick="window.print()">` is more correct. The `onclick` form is also blocked unless `'unsafe-inline'` is allowed; consider a small unobtrusive script tag or `data-module` approach consistent with GOV.UK Frontend patterns.

---

### L-2 · Inline `onclick` Matomo tracker on start page

**File:** `src/main/resources/templates/eac/startPage.html`, line 56

```html
onclick="_paq.push(['trackGoal', 3]);"
```

Inline event handlers are blocked by strict CSPs and are a code-quality concern (mixing behaviour with structure). This should be attached via a `data-` attribute and a small unobtrusive script.

---

### L-3 · Inline styles on pagination links override GOV.UK Frontend defaults

**File:** `src/main/resources/templates/eac/listOfOfficers.html`, lines 64–68

```html
<a style="font-size: 19px;padding: 8px 16px;" class="govuk-link" ...>
```

Hard-coded inline styles prevent the GOV.UK Frontend design system from correctly controlling sizing and spacing, make theming impossible, and are the direct cause of the WCAG 2.5.8 failure acknowledged in the accessibility statement. These should be replaced with the standard `govuk-pagination` component.

---

### L-4 · `formatCompanyStatus` in `CompanyDetailTransformerImpl` rebuilds a `HashMap` on every call

**File:** `src/main/java/.../transformer/company/impl/CompanyDetailTransformerImpl.java`

```java
private String formatCompanyStatus(String companyStatus) {
    Map<String, String> statuses = new HashMap<>();
    statuses.put("active", "Active");
    // ...
```

The map is constructed fresh on every transformer invocation. As this transformer is `@Component` (singleton-scoped), the map should be a `private static final Map<String, String>` constant initialised once with `Map.of(...)` (Java 9+).

---

### L-5 · Spring Boot version property name `spring-boot.version` conflicts with Boot's own internal property

**File:** `pom.xml`

Naming the custom version property `<spring-boot.version>` is the same name used internally by the `spring-boot-starter-parent`/`companies-house-parent` to control the Boot BOM. If the parent is upgraded and also defines this property, the override behaviour can be surprising. A less ambiguous name such as `<spring-boot.override.version>` would avoid any potential conflict.

---

## INFO Observations

---

### I-1 · Service is still on ALPHA phase banner

**File:** `src/main/java/.../controller/BaseController.java`, line 36

```java
model.addAttribute("phaseBanner", "ALPHA");
```

The service has been in production for several years. If this ALPHA designation is still accurate, no action is required. If the service has completed Beta, the phase banner should be updated.

---

### I-2 · `EACOfficerListTransformer` uses `InjectionStrategy.CONSTRUCTOR` but `EACOfficerTransformer` does not

**File:** `src/main/java/.../transformer/emergencyauthcode/officer/EACOfficerListTransformer.java`

`EACOfficerListTransformer` correctly specifies `injectionStrategy = InjectionStrategy.CONSTRUCTOR` because it uses another mapper (`EACOfficerTransformer`). The base `EACOfficerTransformer` is a `@RequestScope` bean — this is an unusually narrow scope for a stateless transformer. Request scope creates a new instance per HTTP request, adding unnecessary overhead. Consider whether singleton scope is sufficient.

---

### I-3 · `UserDetailsInterceptor` unchecked-casts session data

**File:** `src/main/java/.../interceptor/UserDetailsInterceptor.java`

```java
Map<String, Object> signInInfo = (Map<String, Object>) sessionData.get(SIGN_IN_KEY);
Map<String, Object> userProfile = (Map<String, Object>) signInInfo.get(USER_PROFILE_KEY);
```

Both casts are unchecked. If the session schema changes or a different session structure arrives (e.g. from a different OAuth flow), these will throw `ClassCastException` at runtime. Using `instanceof` pattern matching with `if (sessionData.get(SIGN_IN_KEY) instanceof Map<?,?> signInInfo)` would be safer and idiomatic Java 16+.

---

## Summary table

| ID | Severity | File / area | Finding |
|---|---|---|---|
| H-1 | HIGH | All classes | `@Autowired` field injection throughout — must be constructor injection |
| H-2 | HIGH | `EACOfficerTransformer` | `name.isEmpty()` NPE if name is null |
| H-3 | HIGH | `CompanyConfirmationPageController` | `extractIdFromSelfLink` NPE if API omits self link |
| H-4 | HIGH | `ListOfOfficersController`, `OfficerConfirmationPageController` | PRG violated — "cannot use service" rendered without redirect inside POST handlers |
| H-5 | HIGH | All templates, `BaseController` | All user-visible strings hardcoded; locale files empty; no Welsh language support |
| H-6 | HIGH | `listOfOfficers.html`, accessibility statement | WCAG 2.5.8 pagination failure; remediation deadline (Dec 2024) passed |
| M-1 | MEDIUM | `WebSecurity.java` | Deprecated `csrf().disable()` fluent API |
| M-2 | MEDIUM | `officerConfirmation.html` | `th:id` set to data values — invalid HTML, unnecessary PII in DOM |
| M-3 | MEDIUM | `cannotUseThisService.html`, `confirmationPage.html` | `<form method="post">` with no POST handler |
| M-4 | MEDIUM | `startPage.html`, `cannotUseThisService.html` | `<p>` inside `<ul>` — invalid HTML |
| M-5 | MEDIUM | `officerConfirmation.html` | Checkbox error not linked via `aria-describedby` |
| M-6 | MEDIUM | `accessibilityStatement.html` | Page title not unique/descriptive (same as start page) |
| M-7 | MEDIUM | `CompanyConfirmationPageController` | Business logic (eligibility check) in controller |
| M-8 | MEDIUM | `OfficerConfirmationPageController` | Full `EACRequest` (containing `userEmail`) added to view model |
| M-9 | MEDIUM | `ListOfOfficersController` | `Collectors.toList()` deprecated in Java 21 |
| M-10 | MEDIUM | Test suite | No tests for `GlobalExceptionHandler`, `WebSecurity`, or `EACStartController` |
| L-1 | LOW | `confirmationPage.html` | `javascript:window.print()` may be blocked by CSP |
| L-2 | LOW | `startPage.html` | Inline `onclick` Matomo tracker |
| L-3 | LOW | `listOfOfficers.html` | Inline styles on pagination links |
| L-4 | LOW | `CompanyDetailTransformerImpl` | Status map rebuilt on every call; should be a static constant |
| L-5 | LOW | `pom.xml` | `spring-boot.version` property name clashes with parent's internal property |
| I-1 | INFO | `BaseController` | ALPHA phase banner — confirm still accurate |
| I-2 | INFO | `EACOfficerTransformer` | `@RequestScope` on stateless transformer — singleton likely sufficient |
| I-3 | INFO | `UserDetailsInterceptor` | Unchecked casts on session map structure |
