# Electronics Store

## Project Overview

Electronics Store is a backend MVP for an electronics shop. The repository contains a Java REST API and a React/Vite frontend scaffold. The backend implements customer accounts, catalog browsing, cart checkout, order management and payments. API contracts are available through Swagger/OpenAPI.

## Features

- Health endpoint; register/login with JWT; profile and password changes.
- Public categories, brands, products, specifications and image metadata; ADMIN catalog management.
- Product search, category/brand/status/price filters, sorting and pagination.
- Personal shopping cart, atomic checkout, stock checks and immutable order item snapshots.
- Personal order history and cancellation; ADMIN order search and status transitions.
- COD payment lifecycle and signed VNPay sandbox Return/IPN processing.
- Personal wishlist and verified-purchase reviews with rating summaries.

## Architecture

Request flow: **Controller -> Service interface -> ServiceImpl -> Repository -> MySQL**.

Spring MVC binds request DTOs. Bean Validation and service checks validate inputs. Spring Security verifies JWTs and roles; services enforce resource ownership. Service transactions coordinate cart, inventory, orders and payments. Mappers return response DTOs rather than JPA entities. Pessimistic locks and READ_COMMITTED isolation protect checkout/cancellation and related writes. Open-in-view is disabled.

The normal response is `ApiResponse<T>`: `success`, `message`, optional `data`, and `timestamp`. Null data is omitted. VNPay IPN uses its own `RspCode`/`Message` object. Monetary values use BigDecimal; timestamps are local date-times without an offset.

## Tech Stack

| Area | Technology |
| --- | --- |
| Runtime | Java 21 |
| Backend | Spring Boot 3.4.3, Spring MVC |
| Persistence | Spring Data JPA, Hibernate, MySQL |
| Security | Spring Security, BCrypt, JWT (JJWT 0.13.0) |
| Build | Maven |
| API documentation | Swagger UI / OpenAPI, springdoc-openapi 2.8.13 |
| Tests | JUnit 5, Mockito, Spring Boot Test, H2 in MySQL mode |
| Frontend scaffold | React 19, TypeScript 6, Vite 8 (see frontend/package.json) |

springdoc 2.8.x supports Spring Boot 3.4.x according to the [official compatibility matrix](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).

## Project Structure

```text
backend/
  pom.xml
  src/main/java/com/electronics/store/
    config/       Security, CORS, OpenAPI and VNPay configuration
    controller/   HTTP routes and API documentation annotations
    dto/          Request/response contracts and input deserializers
    entity/       JPA entities and enums
    exception/    Common API error handling
    mapper/       Entity-to-DTO mapping
    repository/   JPA queries and search specifications
    security/     JWT filter, token service and user lookup
    service/      Service interfaces and implementations
    util/         Pricing, slug and VNPay signing helpers
  src/main/resources/application.yml
  src/test/       Unit, MVC, repository and HTTP integration tests
frontend/         React/Vite scaffold
docs/             Current API reference and feature verification notes
.env.example      Placeholder environment reference
```

## Database

The application uses **MySQL**. Core tables include users, categories, brands, products, product_specifications, product_images, carts, cart_items, orders, order_items, payments, wishlist_items and reviews.

Use a dedicated local database named `electronics_store`, with a local account permitted to manage its schema. Hibernate currently uses `ddl-auto: update`; the repository has no versioned migration system. Order items keep product ID/name/price snapshots so subsequent catalog edits do not rewrite historical orders. Payments are associated with orders, with one payment per order.

Automated integration tests use isolated H2 databases in MySQL compatibility mode and recreate their schemas. They do not require a running MySQL server and do not prove all MySQL-specific locking/SQL behavior.

## Authentication / Authorization

| Access | Requirement |
| --- | --- |
| PUBLIC | Health, register/login, catalog GET routes, product reviews/ratings, signed VNPay GET callbacks |
| AUTHENTICATED | Active USER or ADMIN with valid JWT; personal profile/cart/orders/wishlist/review writes and VNPay URL creation |
| ROLE_ADMIN | All /api/admin/** routes; ADMIN only |

Register creates an ACTIVE USER. There is no public role assignment or ADMIN bootstrap endpoint; an operator must provision an ADMIN through a trusted database/administrative process.

Use `Authorization: Bearer <JWT>`. Login/register return the token in `data.accessToken`. The JWT subject is the normalized email; user status and role are loaded from the database on authenticated requests. Passwords are BCrypt hashes and are excluded from response DTOs.

Missing/invalid/expired JWT returns 401 on protected routes. USER access to ADMIN routes returns 403. Personal order/review endpoints return 404 for foreign resources; foreign cart items return 403. ADMIN callers remain subject to personal endpoint ownership. An invalid Bearer header also fails on public APIs except VNPay callbacks, which skip JWT verification and verify gateway signatures.

JWTs are stateless: no refresh, logout revocation or password-change revocation is implemented. Existing tokens remain valid until expiry. CORS currently allows http://localhost:5173 and http://localhost:3000 for /api/**.

## Order lifecycle

Checkout is `POST /api/orders`, using the current user's cart. The server validates stock and product status, uses current effective prices, snapshots items, creates a PENDING payment, deducts stock and clears the cart atomically. Shipping fee is currently zero.

| Current status | Allowed ADMIN transition |
| --- | --- |
| PENDING | CONFIRMED, CANCELLED |
| CONFIRMED | SHIPPING, CANCELLED |
| SHIPPING | DELIVERED |
| DELIVERED | None |
| CANCELLED | None |

A customer may cancel only their own PENDING order. Repeating a transition/cancellation returns 400. Cancellation restores stock and cancels a PENDING payment; a PAID payment blocks cancellation because refunds are unsupported. Failure to restore any item rolls back the operation. Payment and order status are separate; VNPay orders currently have no paid-before-shipping gate.

## Payment methods

**COD:** select `"paymentMethod": "COD"` during checkout. Payment starts PENDING and becomes PAID when ADMIN marks the order DELIVERED. There is no standalone COD charge endpoint or client-controlled payment status endpoint. Inspect payment through order responses.

**VNPay:** sandbox only, disabled by default. Configure all required settings, checkout with VNPAY, then call `POST /api/payments/vnpay/create/{orderId}` as the order owner. A valid pending URL is reused. The browser Return endpoint validates and displays results without changing payment state. Only signed IPN processing marks PAID/FAILED. Gateway amount must exactly match the order's VND amount multiplied by 100.

IPN acknowledges with HTTP 200 and `RspCode`/`Message`, including business failures. The registered IPN URL must be publicly reachable over HTTPS. See [VNPay sandbox setup](docs/vnpay-sandbox.md) and [callback contract](docs/api-spec.md#vnpay-callbacks). Do not use production merchant credentials.

## Environment Variables

Set variables in the shell or IDE run configuration. Spring Boot does **not** automatically load a copied .env file. [.env.example](.env.example) contains placeholders only; replace them privately. Never commit local credentials or tokens.

| Variable | Requirement / default |
| --- | --- |
| DB_URL | MySQL JDBC URL; defaults to local electronics_store with database creation enabled |
| DB_USERNAME | Local MySQL username; application fallback is root |
| DB_PASSWORD | Local MySQL password; application fallback is empty |
| JWT_SECRET | Required private random signing secret, at least 32 UTF-8 bytes; no usable default |
| JWT_EXPIRATION_MS | Positive lifetime in milliseconds; default 86400000 |
| VNPAY_ENABLED | Default false; true enables sandbox operations |
| VNPAY_TMN_CODE | Required when enabled; assigned 8-character alphanumeric sandbox merchant code |
| VNPAY_HASH_SECRET | Required when enabled; private sandbox signing secret |
| VNPAY_PAYMENT_URL | Required when enabled; exactly https://sandbox.vnpayment.vn/paymentv2/vpcpay.html |
| VNPAY_RETURN_URL | Required when enabled; HTTPS, or HTTP on localhost/127.0.0.1; route /api/payments/vnpay/return |
| VNPAY_IPN_URL | Required when enabled; public HTTPS route /api/payments/vnpay/ipn; register with gateway |
| VNPAY_VERSION | Default and supported value 2.1.0 |
| VNPAY_EXPIRY_MINUTES | 1..60, default 15 |
| SERVER_PORT | Optional standard Spring Boot override; default 8080 |

Callback URLs have a maximum length of 255 and cannot contain user credentials or fragments. VNPay configuration is checked when used; the application can start with VNPay disabled. JWT_SECRET is checked during startup.

## Local Setup

Prerequisites: JDK 21, Maven 3.9+, a running MySQL instance. For the optional frontend scaffold, use a Node.js version supported by the installed Vite 8 release.

1. Create a dedicated local MySQL database/account, or grant the local account permission to create the configured database.
2. Set DB_URL, DB_USERNAME, DB_PASSWORD and JWT_SECRET in your shell/IDE. Keep VNPay disabled unless sandbox credentials/callbacks are configured.
3. Run backend tests and start the backend as below.
4. Open Swagger and register/login to obtain a customer JWT.

## How to run backend

From the repository root:

```shell
cd backend
mvn clean test
mvn spring-boot:run
```

Health: http://localhost:8080/api/health. Test reports: `backend/target/surefire-reports/`. If Maven uses a different dependency cache, supply `-Dmaven.repo.local=<local-cache-path>`; this does not change test selection.

Optional frontend:

```shell
cd frontend
npm install
npm run dev
```

## API Documentation

- [Swagger UI](http://localhost:8080/swagger-ui/index.html), also accessible through /swagger-ui.html.
- [OpenAPI JSON](http://localhost:8080/v3/api-docs), [OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml).
- [Complete endpoint inventory and request examples](docs/api-spec.md).
- [Documentation index and verification records](docs/README.md).

Swagger covers all 55 backend operations. Each operation identifies PUBLIC, AUTHENTICATED or ROLE_ADMIN and documents its success response, validation, parameters and common errors.

To test authentication:

1. Execute register or login under Authentication.
2. Copy `data.accessToken`.
3. Click **Authorize**, enter the complete value **Bearer <JWT>**, then Authorize.
4. Execute a protected operation such as GET /api/users/me. Use an ADMIN account for /api/admin/**.
5. Clear authorization when finished. Authorization is not persisted across reloads.

The OpenAPI security scheme is an `apiKey` in the Authorization header so Swagger sends the **complete Bearer header verbatim**. It still authenticates using the existing JWT filter. This avoids an extra Bearer prefix when pasting the requested format. Public operations have no security requirement.

Only GET access to /swagger-ui.html, /swagger-ui/**, /v3/api-docs, /v3/api-docs/** and /v3/api-docs.yaml was added to the security allowlist. Existing API authorization rules remain in place. Swagger's own resource handlers work with the existing disabled general static resource mappings.

## Git workflow

The repository owner manages Git. Work is reviewed on the owner-prepared feature/documentation branch, starting from a clean working tree. Backend core testing was merged into develop before this documentation work on `docs/backend-api`.

Before and after work, inspect branch/status/diff. Changes remain unstaged for owner review. This workflow does not automate pull, switch, add, commit, push, merge, reset or clean.

## Known limitations

- Backend MVP scope; frontend remains a scaffold and has not been completed or changed by this feature.
- No token refresh/revocation, password reset, email verification or self-service role management.
- Image URLs/metadata only; no binary upload or file storage integration.
- Catalog listing does not implicitly hide non-ACTIVE products. Product keyword search retains SQL LIKE wildcard behavior for % and _.
- No shipping calculation, tax/coupon engine, refunds or returns.
- VNPay sandbox only; no new attempt after URL expiry/terminal payment, automatic expiry cancellation, refund or reconciliation worker. Late successful callbacks after cancellation need operator reconciliation.
- Order status transitions do not enforce payment completion before shipping/delivery for VNPay.
- Hibernate schema update is for local development; no migration pipeline. Some relationship-conflicting deletes surface as generic 500 errors.
- H2 automated tests and simulated signed VNPay callbacks do not replace live MySQL or gateway acceptance testing. Load testing and production deployment hardening are outside this MVP.
