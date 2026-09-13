# FEATURE 9 - Swagger / API documentation

## Scope

Branch: docs/backend-api. Initial branch/status/diff checks confirmed a clean working
tree. The sandbox uses a different Windows account from the repository owner, so
read-only Git commands use a command-local safe.directory exception. No persistent
Git configuration or Git history/index operations were performed.

Documentation covers all 55 existing controller operations. No business feature,
service/repository implementation, database entity or frontend file was changed.
The VNPay Return method's generic response declaration was narrowed to its existing
concrete DTO so OpenAPI can expose the real schema; its response logic is unchanged.
All existing controller method bodies were compared with HEAD and remain unchanged.

## Files created (4)

- backend/src/main/java/com/electronics/store/config/OpenApiConfig.java
- backend/src/test/java/com/electronics/store/documentation/OpenApiIntegrationTest.java
- docs/api-spec.md
- docs/backend-api-documentation.md

## Files modified (35)

- .env.example
- README.md
- backend/pom.xml
- backend/src/main/resources/application.yml
- backend/src/main/java/com/electronics/store/config/SecurityConfig.java
- docs/README.md
- backend/src/main/java/com/electronics/store/controller/AdminOrderController.java
- backend/src/main/java/com/electronics/store/controller/AuthController.java
- backend/src/main/java/com/electronics/store/controller/BrandController.java
- backend/src/main/java/com/electronics/store/controller/CartController.java
- backend/src/main/java/com/electronics/store/controller/CategoryController.java
- backend/src/main/java/com/electronics/store/controller/HealthController.java
- backend/src/main/java/com/electronics/store/controller/OrderController.java
- backend/src/main/java/com/electronics/store/controller/ProductController.java
- backend/src/main/java/com/electronics/store/controller/ProductImageController.java
- backend/src/main/java/com/electronics/store/controller/ProductSpecificationController.java
- backend/src/main/java/com/electronics/store/controller/ReviewController.java
- backend/src/main/java/com/electronics/store/controller/UserController.java
- backend/src/main/java/com/electronics/store/controller/VnPayController.java
- backend/src/main/java/com/electronics/store/controller/WishlistController.java
- backend/src/main/java/com/electronics/store/dto/request/AddCartItemRequest.java
- backend/src/main/java/com/electronics/store/dto/request/BrandRequest.java
- backend/src/main/java/com/electronics/store/dto/request/CategoryRequest.java
- backend/src/main/java/com/electronics/store/dto/request/ChangePasswordRequest.java
- backend/src/main/java/com/electronics/store/dto/request/CheckoutRequest.java
- backend/src/main/java/com/electronics/store/dto/request/CreateReviewRequest.java
- backend/src/main/java/com/electronics/store/dto/request/LoginRequest.java
- backend/src/main/java/com/electronics/store/dto/request/ProductImageRequest.java
- backend/src/main/java/com/electronics/store/dto/request/ProductRequest.java
- backend/src/main/java/com/electronics/store/dto/request/ProductSpecificationRequest.java
- backend/src/main/java/com/electronics/store/dto/request/RegisterRequest.java
- backend/src/main/java/com/electronics/store/dto/request/UpdateCartItemRequest.java
- backend/src/main/java/com/electronics/store/dto/request/UpdateOrderStatusRequest.java
- backend/src/main/java/com/electronics/store/dto/request/UpdateProfileRequest.java
- backend/src/main/java/com/electronics/store/dto/request/UpdateReviewRequest.java

## OpenAPI and security

- Added springdoc-openapi-starter-webmvc-ui 2.8.13 for Spring Boot 3.4.3. The
  [official compatibility matrix](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)
  lists Spring Boot 3.4.x with springdoc 2.7.x-2.8.x.
- OpenApiConfig supplies API metadata, a shared error envelope, common error
  responses and the BearerAuth Authorization header scheme.
- The scheme uses apiKey/header to accept the literal **Bearer <JWT>** value.
  The existing JWT filter remains the authenticator; no token parsing rule changed.
- No global security requirement: each protected operation declares BearerAuth.
  Every operation explicitly identifies PUBLIC, AUTHENTICATED or ROLE_ADMIN.
- Only documentation GET routes were added to the allowlist: /swagger-ui.html,
  /swagger-ui/**, /v3/api-docs, /v3/api-docs/**, /v3/api-docs.yaml.
- Existing public/authenticated/admin rules and CORS were retained.
- General static resource mappings remain disabled. springdoc's dedicated Swagger
  resource handlers serve its HTML/CSS/JS successfully.
- Disabled generic controller-advice response injection so operations expose
  their documented response sets; success DTO schemas are inferred from signatures.
- Swagger does not persist authorization across reloads. Default Petstore URL disabled.
- Request schema descriptions include Bean Validation and service/deserializer
  constraints. VNPay callbacks expose individual signed query fields, not an opaque map.

Default URLs after starting the application:

- http://localhost:8080/swagger-ui/index.html (also /swagger-ui.html)
- http://localhost:8080/v3/api-docs
- http://localhost:8080/v3/api-docs.yaml

## Modules covered

Health; Authentication; User Profile; Category; Brand; Product; Product Specification;
Product Images; Product Search / Filter / Sort / Pagination; Shopping Cart; Checkout;
Order; Admin Order Management; User Order Cancellation; Payment COD; VNPay; Wishlist;
Review / Rating.

COD is documented through checkout and order delivery, matching the existing API.
There is no invented standalone COD endpoint. VNPay Return is read-only and IPN
uses its own HTTP-200 RspCode/Message contract. Personal order/review ownership
and the delivered-purchase rule for reviews are documented.

## README and environment

Replaced the scaffold README with current overview/features/architecture/stack/
structure/database/authentication/order lifecycle/payments/environment/setup/run/
API documentation/Git workflow/limitations. Corrected frontend stack to React 19,
TypeScript 6 and Vite 8. MySQL is the application database; H2 is test-only.

Updated .env.example to explicit private-value placeholders. No real JWT secret,
database password, merchant secret or access token was added. Spring Boot does not
automatically load .env; the README explains shell/IDE environment setup.

docs/README.md now links actual files and distinguishes historical feature reports
from the current API reference.

## Verification

The new OpenApiIntegrationTest uses a real HTTP server, the production application
configuration (including disabled static mappings), an isolated H2 database and
the actual JWT filter. It checks:

- Swagger HTML, JS/CSS, initializer, JSON, YAML and swagger-config return HTTP 200.
- Generated operations match all 55 actual controller mappings.
- Every operation has summary, tags, access description and concrete success schema.
- All local schema/response references resolve; public/protected requirements match.
- DTO validation, write-only password documentation and concrete VNPay contracts.
- Literal Bearer header works; anonymous gets 401, USER gets 403 on ADMIN routes,
  ADMIN is allowed; unrelated routes and non-GET documentation access stay protected.

Full suite command from backend (environment-specific dependency cache only):

~~~powershell
mvn '-Dmaven.repo.local=C:/Users/DVT/.m2/repository' clean test
~~~

Final run on 2026-09-12: **BUILD SUCCESS, exit code 0; 584 tests across 32 suites,
0 failures, 0 errors, 0 skipped**. Duration: 1 minute 58 seconds. Surefire XML
reports were independently summed to confirm the totals. Log: backend/swagger-clean-test.log
(ignored); reports: backend/target/surefire-reports/.

The full suite includes existing Register/Login/JWT/roles, profile, catalog,
cart, checkout, order/cancellation, ADMIN lifecycle, COD, VNPay, wishlist and
review regressions. No tests or assertions were disabled.

During documentation refinement, adding an explicit response media type caused
springdoc to lose inferred schemas; the new contract tests caught this. The extra
content override was removed and all schema assertions retained.

A separate mvn spring-boot:run session started on port 18080 with temporary H2,
test classpath solely to provide the H2 driver, main application.yml selected,
a process-local random JWT key, and VNPay disabled. It reached readiness and served
health, Swagger HTML/JS/CSS, OpenAPI JSON/YAML/config with HTTP 200. Additional HTTP
smoke checks passed register/login, authenticated profile/cart/orders/wishlist,
anonymous 401 and USER-on-ADMIN 403. The isolated application was stopped afterward;
its in-memory database was discarded.

Browser discovery returned no available browser, so visual rendering/clicking the
Authorize dialog was not verified. Resource loading and Authorization header
behavior were verified over real HTTP.

## Git and limitations

Final branch: docs/backend-api. 35 modified files and 4 new files, all unstaged.
Final git status and git diff were reviewed; git diff --check is clean.
No pull/switch/add/commit/push/merge/reset/clean was run. No frontend changes.
The raw final tracked diff is saved in backend/swagger-final.diff.log (ignored).

- This feature's tests/startup used H2, not live MySQL; live gateway acceptance and
  real money transactions were not run. VNPay regression tests use signed simulations.
- No browser UI interaction or screenshot verification was available.
- Existing MVP limitations are recorded in README: no JWT refresh/revocation,
  refunds, image upload, VNPay retry/reconciliation worker, paid-before-shipping
  gate, shipping calculation or migration pipeline.
- No new backend feature is included beyond this documentation scope.
