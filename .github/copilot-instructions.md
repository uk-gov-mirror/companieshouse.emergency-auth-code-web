# Copilot Instructions — emergency-auth-code-web

Spring Boot 3 / Java 21 web service (Thymeleaf + GOV.UK Frontend) that allows
company officers to request an emergency authentication code. Backed by
[emergency-auth-code-api](https://github.com/companieshouse/emergency-auth-code-api)
and [oracle-query-api](https://github.com/companieshouse/oracle-query-api).

---

## Build, test, and lint

```bash
# Run all tests (unit + integration) with JaCoCo coverage
make test

# Unit tests only
make test-unit

# Run a single test class
./mvnw test -Dtest=CompanyConfirmationPageControllerTest

# Run a single test method
./mvnw test -Dtest=CompanyConfirmationPageControllerTest#getCompanyInformation_success

# Build JAR (skips tests)
make dev

# SonarQube analysis
make sonar
```

The Makefile wraps Maven — prefer `make` targets for consistency with CI.

---

## Architecture

### User journey flow

```
GET/POST /auth-code-requests/start
  → company-lookup/search (external service)
    → GET/POST /auth-code-requests/company/{companyNumber}/confirm
      → GET/POST /auth-code-requests/requests/{requestId}/officers
        → GET/POST /auth-code-requests/requests/{requestId}/confirm-officer
          → GET /auth-code-requests/requests/{requestId}/confirmation
```

The `POST /confirm` step creates an EAC request via the API and obtains the
`requestId` that threads through the rest of the journey.

### Navigation system

Controllers are linked in a chain using custom annotations instead of
hardcoded redirects. `NavigatorService` resolves the actual URL at runtime:

```java
@Controller
@PreviousController(CompanyConfirmationPageController.class)
@NextController(OfficerConfirmationPageController.class)
@RequestMapping("/auth-code-requests/requests/{requestId}/officers")
public class ListOfOfficersController extends BaseController { ... }
```

- `navigatorService.getNextControllerRedirect(this.getClass(), pathVars...)` — returns `redirect:/path`
- `navigatorService.getPreviousControllerPath(this.getClass(), pathVars...)` — returns path string for back-button
- `@NextController` can take multiple values: one `BranchController` (implements `shouldBranch()`) plus one default
- `ConditionalController` (implements `willRender()`) causes `NavigatorService` to skip that step automatically

### Layers

| Layer | Package | Purpose |
|---|---|---|
| Controllers | `controller/eac/` | Thin — bind request, call service, set model, return view name |
| Services | `service/` | Business logic; `EmergencyAuthCodeService`, `CompanyService` |
| API clients | `api/` | Wraps `ApiClientManager` — `getApiClient()` (public) vs `getInternalApiClient()` (private SDK) |
| Transformers | `transformer/` | MapStruct mappers converting SDK API models to web models |
| Session | `session/SessionService` | Wraps CHS `java-session-handler` |

### Base controller

All controllers extend `BaseController`, which provides:
- `LOGGER` (CHS structured logger)
- `navigatorService` (injected)
- `addBackPageAttributeToModel(model, pathVars...)` helper
- `@ModelAttribute` that adds `headerText`, `headerURL`, `phaseBanner` to every model
- Abstract `getTemplateName()` that controllers must implement

### Security

Two `SecurityFilterChain` beans in `WebSecurity`:
- `/start` and `/accessibility-statement` — session + hijack filter only (unauthenticated)
- `/company/**` and `/requests/**` — session + hijack + `UserAuthFilter` (authenticated)

CSRF is disabled (CHS OAuth handles this at the gateway layer).

---

## Key conventions

### Controllers
- Return view name strings or `"error"` (constant `ERROR_VIEW` from `BaseController`)
- Use PRG: successful POST always returns `navigatorService.getNextControllerRedirect(...)`
- Log exceptions with `LOGGER.errorRequest(request, e.getMessage(), e)` before returning error view
- Accepted company types and status are validated in `CompanyConfirmationPageController` before creating the EAC request

### Transformers (MapStruct)
- Interfaces annotated `@Mapper(componentModel = "spring")` and `@RequestScope`
- Officer name formatting: API stores `FIRSTNAME MIDDLENAME LASTNAME`; `EACOfficerTransformer` reformats to `LASTNAME, Firstname Middlename`

### Localisation
- English: `src/main/resources/locales/messages.properties`
- Welsh: `src/main/resources/locales/messages_cy.properties`
- Welsh is currently disabled (`welshLanguage.enabled=false` in `application.properties`) but both files must be kept in sync

### Validation messages
- Custom Bean Validation messages go in `src/main/resources/ValidationMessages.properties`

### API access
- Public API: `apiClientService.getApiClient()` → `ApiClientManager.getSDK()`
- Private/internal API: `apiClientService.getInternalApiClient()` → `ApiClientManager.getPrivateSDK()`

### Testing pattern
```java
@ExtendWith(MockitoExtension.class)
class SomeControllerTest {
    @Mock NavigatorService navigatorService;
    @InjectMocks SomeController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
}
```
Tests use `MockMvcBuilders.standaloneSetup()` (not `@WebMvcTest`) so Spring
Security config is bypassed. `NavigatorService` is always mocked.

### Configuration
Required environment variables are documented in `README.md`. Key ones:
`CHS_API_KEY`, `ACCOUNT_LOCAL_URL`, `CDN_HOST`, `CHS_URL`.
