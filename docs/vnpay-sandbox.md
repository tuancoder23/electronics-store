# VNPAY sandbox — Feature 4

## Official references reviewed

Reviewed online on 2026-09-09; PAY and response-code pages rechecked on 2026-09-10:

- [PAY integration](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
- [Algorithm migration](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html)
- [Response codes](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/)
- [Technical specification 2.1.0, transaction status table section 2.5.7.2](https://sandbox.vnpayment.vn/apis/files/VNPAY%20Payment%20Gateway_Techspec%202.1.0-VN.pdf)

Protocol: PAY 2.1.0, GET redirect to
`https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`. Sign sorted parameter names
and form-URL-encoded values with HMAC-SHA512. Exclude empty and signature fields;
do not send `vnp_SecureHashType`. Send VND amount multiplied by 100; maximum 12
digits. Create/expiry times use GMT+7. IPN uses GET and HTTPS; its URL is registered
with VNPAY, not a payment URL parameter. Return verifies/displays only; IPN updates.

Documentation discrepancies: the Java sample uses `Etc/GMT+7`, whose Java offset
has the opposite sign from the documented GMT+7. This implementation follows the
table using `Asia/Ho_Chi_Minh`. A PHP IPN example uses OR for the two success codes;
this implementation requires BOTH `vnp_ResponseCode=00` and
`vnp_TransactionStatus=00`, consistent with the field meanings and C# example.
UTF-8 form encoding matches the PHP/Node examples; generated order information
is ASCII, so its encoding also matches the Java US-ASCII sample.

## Configuration

All runtime configuration comes from environment variables referenced by
`application.yml`. `.env.example` contains placeholders only for VNPAY.
Spring Boot does not automatically load a root `.env`: export these values in the
process environment or configure the IDE run environment.

| Variable | Value / purpose |
| --- | --- |
| VNPAY_ENABLED | false by default; enable only with sandbox credentials |
| VNPAY_TMN_CODE | 8-character sandbox merchant code from VNPAY |
| VNPAY_HASH_SECRET | Sandbox signing secret from VNPAY |
| VNPAY_PAYMENT_URL | Exact sandbox PAY URL above; production/other hosts rejected |
| VNPAY_RETURN_URL | This backend's `/api/payments/vnpay/return`; HTTPS, or localhost HTTP for development |
| VNPAY_IPN_URL | Public HTTPS URL ending `/api/payments/vnpay/ipn`; register with VNPAY |
| VNPAY_VERSION | 2.1.0 |
| VNPAY_EXPIRY_MINUTES | Default 15; accepted range 1–60 |

An unconfigured integration leaves COD available. VNPAY checkout/create requests
fail before committing gateway state if the sandbox configuration is missing or
invalid. Configuration errors do not include property values. No credentials are
provided or invented for a real merchant in this change.

## Checkout and create URL

1. Authenticate and use the existing cart and `POST /api/orders` checkout with
   `paymentMethod: VNPAY`. It creates one PENDING Payment and reserves stock in the
   existing checkout transaction. COD keeps its existing behavior.
2. Call `POST /api/payments/vnpay/create/{orderId}` with Bearer JWT, no body required.
   The authenticated user's ID is resolved from SecurityContext. Foreign/missing
   orders return 404; absent/invalid JWT returns 401.
3. Order must not be CANCELLED/DELIVERED. Its existing Payment and Order must both
   use VNPAY, with Payment PENDING and matching amount. Payment PAID/FAILED/CANCELLED
   is rejected; COD cannot be changed into VNPAY through this API.
4. The server builds parameters from configuration, Order, connection IP and time.
   It creates a random 32-character hexadecimal reference and stores it under a
   unique constraint, alongside the signed URL and expiry. Payment.amount stays
   in VND BigDecimal. Unsupported zero, negative, oversized or fractional gateway
   representations are rejected without rounding.
5. Response uses existing ApiResponse with data:

```json
{
  "orderId": 10,
  "paymentId": 20,
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?..."
}
```

Repeated/concurrent calls return the same persisted URL while it is valid, without
creating a second Payment/reference. Expired URLs and FAILED payments do not start
new attempts in this feature. Do not rotate references until retry/reconciliation
is designed; otherwise late notifications could be associated with the wrong attempt.
The gateway reference/URL fields are not included in normal PaymentResponse.

No forwarded-header trust is configured. Client IP uses `request.getRemoteAddr()`;
arbitrary X-Forwarded-For/Forwarded headers do not override it. A deployment behind
a proxy needs infrastructure-managed trusted-proxy configuration before changing this.

## Return and IPN

`GET /api/payments/vnpay/return` is public and read-only. It verifies signature,
merchant, reference, amount and callback fields, then returns a small DTO with
order/payment IDs, persisted paymentStatus and the two gateway result codes.
Browser success alone does not mean the database Payment is PAID. Invalid callback
data returns HTTP 400. The frontend can subsequently read its authenticated Order.

`GET /api/payments/vnpay/ipn` is public and returns raw protocol JSON (no ApiResponse
wrapper), HTTP 200:

```json
{"RspCode":"00","Message":"Confirm Success"}
```

Implemented protocol responses:

| RspCode | Handling |
| --- | --- |
| 00 | Payment update committed |
| 02 | Already finalized or order cancelled; no repeated side effects |
| 01 | Merchant/reference/payment not found or method mismatch |
| 04 | Amount format/value mismatch |
| 97 | Invalid/missing signature or duplicate signed parameters |
| 99 | Malformed/inconclusive result, configuration or database failure; retry/reconcile |

Both public GET callbacks bypass the JWT filter, including stale Bearer headers,
but must pass signature verification. Other methods/routes retain normal security.
Duplicate `vnp_*` parameters are rejected rather than silently choosing one value.
Signatures are compared as bytes with `MessageDigest.isEqual`. No client Order ID
is used to resolve a callback: only a stored gateway reference is accepted.

## State and transactions

IPN first verifies the signature, then selects only the Order ID by reference and
acquires the same `PESSIMISTIC_WRITE` Order lock used by user/admin cancellation.
Payment is read after the lock; this avoids cached stale state during contention.
The update runs under READ_COMMITTED with saveAndFlush. Exceptions are caught by
the controller outside the transactional proxy, including commit failures, so a
failed update rolls back and VNPAY receives code 99.

| Confirmed result | Payment transition |
| --- | --- |
| Response 00 and transaction status 00 | PENDING → PAID, store transactionCode and paidAt |
| Documented failure response and transaction status 02 | PENDING → FAILED; paidAt stays null |
| Pending, suspicious, reversed or contradictory result | Keep PENDING; return 99 for reconciliation |
| Repeated notification for terminal Payment | Keep state/timestamps; return 02 |

paidAt uses validated vnp_PayDate when present, otherwise current GMT+7 time.
A supplied nonempty date must contain exactly 14 digits and represent a valid
calendar date/time; signed or extended years are rejected by both callbacks.
Both Return and IPN reject an all-zero transaction number for a success result.
A confirmed failure may use transaction number `0`.
IPN never changes Order.status, stock or cart. A success may arrive after local
URL expiry; expiry is not used to discard an otherwise valid notification.

Order cancellation continues through existing APIs. Payment PAID blocks cancellation;
an unpaid cancelled Order is never resurrected by a late IPN. A late success is
logged with order/payment IDs for reconciliation, returns 02, and does not refund
automatically. This case requires merchant review of the actual gateway transaction.
FAILED payment leaves Order pending until the user/admin explicitly cancels it.

There is no refund, querydr, gateway retry, production integration or frontend work.
Do not log credentials, JWTs, raw callback queries or complete signed URLs. Application
logs contain only safe IDs/error class names; keep web/bind parameter logging disabled
and configure reverse-proxy access logs to redact callback queries during deployment.

## Tests and manual sandbox run

`VnPaySignerTest` checks canonical encoding and a fixed HMAC-SHA512 vector computed
independently with .NET, malformed signatures, exact amount conversion and sandbox
configuration restrictions. `VnPayIntegrationTest` uses real HTTP/JWT/H2 transactions
with a fake local merchant to exercise create, ownership, Return/IPN, invalid signatures,
amount/reference mismatch, duplicates, failure states, rollback, concurrency, COD and
stock/cart preservation. It does not contact VNPAY. Run `mvn clean test` in `backend`.

Continuation review added regression coverage for callback date format and zero
success transaction numbers, optional date fallback, success after URL expiry,
malformed signed amounts, stored Order/Payment inconsistencies, callback JWT
exemption boundaries, and admin cancellation competing with IPN. These checks use
the existing service and integration-test classes; no parallel payment model is added.
The existing Payment rollback test now starts with a fixed historical cart timestamp,
so its database failure does not depend on the operating system clock advancing
between cart setup and checkout.

Manual test remains pending until real sandbox credentials and a reachable HTTPS
IPN URL are supplied/configured:

1. Export sandbox variables, database and JWT configuration; register the IPN URL.
2. Start backend, log in, add to cart and checkout VNPAY.
3. Request the URL and open it in a browser. Use the official sandbox test instruments.
4. Complete the test payment; observe verified Return and committed IPN separately.
5. Read Order through the authenticated API: Payment PAID with transactionCode/paidAt;
   Order lifecycle and stock/cart remain consistent.
6. Check failure/cancel at gateway, duplicate IPN and late-callback reconciliation.

H2 MySQL mode tests do not substitute for a live MySQL or full VNPAY sandbox test.

## Continuation review and file inventory

The resumed workspace was already on `feature/payment-vnpay` with 12 modified
tracked files and 12 untracked files. Checkout integration, gateway configuration,
URL creation, signing, callbacks, locking, and the initial test suite already existed.
The saved reports had 37 VNPAY integration cases and 10 signer cases passing.
There was no saved task checkpoint identifying an unfinished method; continuation
used the current source, diff, documentation and regression tests to identify gaps.

This continuation changes only `VnPayServiceImpl.java`, `VnPayIntegrationTest.java`,
`PaymentIntegrationTest.java` and this document. It tightens shared callback
validation, adds 20 integration cases and makes the existing rollback test
deterministic. It does not create another class, DTO, Service or Repository.

The complete feature inventory below includes files inherited from the previous run.
Java paths in the first two rows are relative to
`backend/src/main/java/com/electronics/store/`.

| Git state | Files |
| --- | --- |
| Modified production Java (9) | `config/SecurityConfig.java`, `dto/request/CheckoutRequest.java`, `entity/PaymentEntity.java`, `entity/PaymentMethod.java`, `exception/GlobalExceptionHandler.java`, `repository/PaymentRepository.java`, `security/JwtAuthenticationFilter.java`, `service/impl/OrderServiceImpl.java`, `service/impl/PaymentServiceImpl.java` |
| Untracked production Java (9) | `config/VnPayProperties.java`, `controller/VnPayController.java`, `dto/response/VnPayCreatePaymentResponse.java`, `dto/response/VnPayIpnResponse.java`, `dto/response/VnPayReturnResponse.java`, `exception/VnPayCallbackException.java`, `service/VnPayService.java`, `service/impl/VnPayServiceImpl.java`, `util/VnPaySigner.java` |
| Modified configuration (2) | `.env.example`, `backend/src/main/resources/application.yml` |
| Modified test (1) | `backend/src/test/java/com/electronics/store/order/PaymentIntegrationTest.java` |
| Untracked tests (2) | `backend/src/test/java/com/electronics/store/order/VnPayIntegrationTest.java`, `backend/src/test/java/com/electronics/store/util/VnPaySignerTest.java` |
| Untracked documentation (1) | `docs/vnpay-sandbox.md` |

Only read-only Git commands were used. The per-command `safe.directory` option
handles the sandbox account's different ownership without changing Git configuration.
Maven needed to run outside the sandbox to read the user's existing dependency
cache; no build configuration or dependency was changed for this environment issue.

Final validation on 2026-09-10: `mvn clean test` in `backend` reported
**BUILD SUCCESS: 407 tests, 0 failures, 0 errors, 0 skipped** (about 81 seconds).
This includes 57 VNPAY integration cases, 10 signer cases and 39 Payment cases.
The four newly reproduced callback regression failures pass after the validation
fix; the complete Payment suite also passes with the deterministic rollback setup.
Log: `backend/vnpay-clean-test-final.log` (ignored by Git); per-suite reports are
under `backend/target/surefire-reports/`.

The feature still requires a manual test with real sandbox credentials and a public
HTTPS IPN endpoint, plus verification against MySQL. Refunds, retry attempts,
querydr reconciliation, production gateway support and frontend work remain outside
this feature. The working tree is left unstaged and uncommitted for review.
