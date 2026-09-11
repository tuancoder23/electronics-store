# FEATURE 8 - Backend core testing

## Scope and review

Started on `test/backend-core-flows` with a clean working tree. No production or
frontend changes. Existing integration suites already cover the order transition
matrix, JWT/roles, ownership, wishlist idempotence, review validation, concurrent
requests, stock rollback and VNPay callbacks. Those scenarios are retained rather
than copied into new integration suites.

New unit tests check service boundaries: rejected operations must not reach writes,
payment work or token generation. They do not claim to prove transaction rollback;
rollback assertions run against committed database state through real HTTP requests.

## Test files

Paths below are relative to `backend/src/test/java/com/electronics/store/`.

| File | Change | Additional coverage |
| --- | --- | --- |
| `service/AuthServiceTest.java` | New | Locale-independent login normalization, unchanged password whitespace, authentication failure without token issuance, exact BCrypt UTF-8 byte boundary |
| `service/CartServiceTest.java` | New | Decimal totals recalculated without writes, overflow rejected before mutation, foreign item rejected before update/delete |
| `service/OrderServiceTest.java` | New | Invalid transition and ownership rejected before inventory/payment writes; disabled VNPay fails before cart locking |
| `service/PaymentServiceTest.java` | New | Mismatched amount/method rejected before writes, scale-independent amount equality, terminal payment idempotence, missing VNPay payment cannot use COD fallback |
| `service/WishlistServiceTest.java` | New | Invalid IDs without database access, duplicate without insert, removal scoped to current user, deleted current user rejected |
| `service/ReviewServiceTest.java` | New | Service validation without HTTP validation, invalid updates preserve original fields, scoped ownership, duplicate stops before purchase queries |
| `util/ProductPricingTest.java` | New | Exact shared cart/checkout price rule, zero/invalid discounts, invalid base price, catalog values unchanged |
| `service/ProductServiceTest.java` | Extended | Duplicate name during update preserves product fields without saving |
| `order/OrderIntegrationTest.java` | Extended | Register and login tokens used through real APIs; admin creates product; customer checks out; COD delivery enables review; recursive password-field checks; checkout rollback also leaves no payment |
| `order/PaymentIntegrationTest.java` | Extended | Six amount/method mismatch scenarios across delivery, user cancellation and admin cancellation; database state and timestamps unchanged on rejection; successful retry after fixture repair |
| `order/UserOrderCancellationIntegrationTest.java` | Extended | Second-product restoration failure also preserves one PENDING payment without paidAt |

The inconsistent-payment fixture uses JDBC because payment amount and method are
immutable through JPA. It asserts that the fixture corruption actually reached the
database before exercising the HTTP endpoint.

## Retained integration coverage

| Requirement | Existing coverage |
| --- | --- |
| Missing/invalid JWT, USER forbidden, ADMIN allowed, safe authentication responses | `security/AuthenticationIntegrationTest`, `order/AdminOrderIntegrationTest` |
| Password change, failed password validation, no password leakage | `user/UserProfileIntegrationTest` |
| Cart quantity, stock, user isolation and concurrent mutations | `cart/CartIntegrationTest` |
| Multi-product checkout rollback, current prices, immutable order snapshots | `order/OrderIntegrationTest` |
| PENDING user cancellation, restoration failure, concurrent cancellation | `order/UserOrderCancellationIntegrationTest` |
| Full status transition matrix and delivery lifecycle | `order/AdminOrderIntegrationTest` |
| COD PENDING to PAID, cancellation, persistence failure rollback | `order/PaymentIntegrationTest` |
| Wishlist add/duplicate/remove/ownership | `wishlist/WishlistIntegrationTest` |
| Verified purchase, DELIVERED requirement, ratings 1..5, duplicates, ownership | `review/ReviewIntegrationTest` |
| VNPay signature, exact amount, duplicate/concurrent IPN, callback rollback | `util/VnPaySignerTest`, `order/VnPayIntegrationTest` |

## Running and limitations

Final verification on 2026-09-11: **580 tests, 580 passed, 0 failures, 0 errors,
0 skipped; BUILD SUCCESS, exit code 0**. This adds 51 test executions to the
existing 529. The complete clean build took 1 minute 25 seconds. Log:
`backend/core-clean-test.log` (ignored). No production bug required a source fix.

Run `mvn clean test` from `backend`. This environment uses the existing dependency
cache via `-Dmaven.repo.local=C:\Users\DVT\.m2\repository`; the final run does not
filter, disable or skip tests. Surefire reports are in `backend/target/surefire-reports`.

Integration tests use H2 in MySQL mode, not a live MySQL server. VNPay tests simulate
signed callbacks locally; they do not contact a live gateway or move money. No
frontend, load testing or coverage percentage measurement is included.
