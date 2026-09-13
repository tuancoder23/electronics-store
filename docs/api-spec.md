# Backend API reference

Base URL: http://localhost:8080. Interactive schemas: /swagger-ui/index.html; OpenAPI JSON: /v3/api-docs.

## Endpoint inventory

Full request/response schemas, validation and defaults are available in Swagger. Responses wrap DTOs in ApiResponse<T>, except VNPay IPN. DELETE success is HTTP 200, not 204.

| Module | Method | URL | Access | Function | Success | Common errors |
| --- | --- | --- | --- | --- | --- | --- |
| Admin Order Management | GET | /api/admin/orders | ROLE_ADMIN | Search all orders | 200 | 400,401,403,500 |
| Admin Order Management | GET | /api/admin/orders/{orderId} | ROLE_ADMIN | Get any order | 200 | 400,401,403,404,500 |
| Admin Order Management | PUT | /api/admin/orders/{orderId}/status | ROLE_ADMIN | Update order status | 200 | 400,401,403,404,500 |
| Authentication | POST | /api/auth/register | PUBLIC | Register a customer | 201 | 400,401,409,500 |
| Authentication | POST | /api/auth/login | PUBLIC | Log in | 200 | 400,401,500 |
| Brand | GET | /api/brands | PUBLIC | List brands | 200 | 401,500 |
| Brand | GET | /api/brands/{id} | PUBLIC | Get brand | 200 | 400,401,404,500 |
| Brand | POST | /api/admin/brands | ROLE_ADMIN | Create brand | 201 | 400,401,403,409,500 |
| Brand | PUT | /api/admin/brands/{id} | ROLE_ADMIN | Update brand | 200 | 400,401,403,404,409,500 |
| Brand | DELETE | /api/admin/brands/{id} | ROLE_ADMIN | Delete brand | 200 | 400,401,403,404,500 |
| Shopping Cart | GET | /api/cart | AUTHENTICATED | Get my cart | 200 | 400,401,404,500 |
| Shopping Cart | POST | /api/cart/items | AUTHENTICATED | Add cart item | 201 | 400,401,404,500 |
| Shopping Cart | PUT | /api/cart/items/{cartItemId} | AUTHENTICATED | Set cart item quantity | 200 | 400,401,403,404,500 |
| Shopping Cart | DELETE | /api/cart/items/{cartItemId} | AUTHENTICATED | Remove cart item | 200 | 400,401,403,404,500 |
| Shopping Cart | DELETE | /api/cart | AUTHENTICATED | Clear my cart | 200 | 400,401,404,500 |
| Category | GET | /api/categories | PUBLIC | List categories | 200 | 401,500 |
| Category | GET | /api/categories/{id} | PUBLIC | Get category | 200 | 400,401,404,500 |
| Category | POST | /api/admin/categories | ROLE_ADMIN | Create category | 201 | 400,401,403,409,500 |
| Category | PUT | /api/admin/categories/{id} | ROLE_ADMIN | Update category | 200 | 400,401,403,404,409,500 |
| Category | DELETE | /api/admin/categories/{id} | ROLE_ADMIN | Delete category | 200 | 400,401,403,404,500 |
| Health | GET | /api/health | PUBLIC | Check API health | 200 | 401,500 |
| Checkout | POST | /api/orders | AUTHENTICATED | Checkout current cart | 201 | 400,401,404,409,500 |
| Order | GET | /api/orders/my-orders | AUTHENTICATED | List my orders | 200 | 400,401,404,500 |
| Order | GET | /api/orders/{orderId} | AUTHENTICATED | Get my order | 200 | 400,401,404,500 |
| User Order Cancellation | PUT | /api/orders/{orderId}/cancel | AUTHENTICATED | Cancel my pending order | 200 | 400,401,404,500 |
| Product Search / Filter / Sort / Pagination | GET | /api/products | PUBLIC | Search products | 200 | 400,401,500 |
| Product | GET | /api/products/{id} | PUBLIC | Get product | 200 | 400,401,404,500 |
| Product | POST | /api/admin/products | ROLE_ADMIN | Create product | 201 | 400,401,403,404,409,500 |
| Product | PUT | /api/admin/products/{id} | ROLE_ADMIN | Update product | 200 | 400,401,403,404,409,500 |
| Product | DELETE | /api/admin/products/{id} | ROLE_ADMIN | Delete product | 200 | 400,401,403,404,500 |
| Product Images | GET | /api/products/{productId}/images | PUBLIC | List product images | 200 | 400,401,404,500 |
| Product Images | GET | /api/product-images/{imageId} | PUBLIC | Get image | 200 | 400,401,404,500 |
| Product Images | POST | /api/admin/products/{productId}/images | ROLE_ADMIN | Add image | 201 | 400,401,403,404,500 |
| Product Images | PUT | /api/admin/product-images/{imageId} | ROLE_ADMIN | Update image | 200 | 400,401,403,404,500 |
| Product Images | PUT | /api/admin/product-images/{imageId}/primary | ROLE_ADMIN | Select primary image | 200 | 400,401,403,404,500 |
| Product Images | DELETE | /api/admin/product-images/{imageId} | ROLE_ADMIN | Delete image | 200 | 400,401,403,404,500 |
| Product Specification | GET | /api/products/{productId}/specifications | PUBLIC | List product specifications | 200 | 400,401,404,500 |
| Product Specification | GET | /api/product-specifications/{id} | PUBLIC | Get specification | 200 | 400,401,404,500 |
| Product Specification | POST | /api/admin/products/{productId}/specifications | ROLE_ADMIN | Create specification | 201 | 400,401,403,404,409,500 |
| Product Specification | PUT | /api/admin/product-specifications/{id} | ROLE_ADMIN | Update specification | 200 | 400,401,403,404,409,500 |
| Product Specification | DELETE | /api/admin/product-specifications/{id} | ROLE_ADMIN | Delete specification | 200 | 400,401,403,404,500 |
| Review / Rating | GET | /api/products/{productId}/reviews | PUBLIC | List product reviews | 200 | 400,401,404,500 |
| Review / Rating | GET | /api/products/{productId}/rating-summary | PUBLIC | Get rating summary | 200 | 400,401,404,500 |
| Review / Rating | POST | /api/products/{productId}/reviews | AUTHENTICATED | Review purchased product | 201 | 400,401,403,404,409,500 |
| Review / Rating | PUT | /api/reviews/{reviewId} | AUTHENTICATED | Update my review | 200 | 400,401,404,500 |
| Review / Rating | DELETE | /api/reviews/{reviewId} | AUTHENTICATED | Delete my review | 200 | 400,401,404,500 |
| User Profile | GET | /api/users/me | AUTHENTICATED | Get my profile | 200 | 401,404,500 |
| User Profile | PUT | /api/users/me | AUTHENTICATED | Update my profile | 200 | 400,401,404,500 |
| User Profile | PUT | /api/users/me/password | AUTHENTICATED | Change my password | 200 | 400,401,404,500 |
| VNPay | POST | /api/payments/vnpay/create/{orderId} | AUTHENTICATED | Create VNPay payment URL | 200 | 400,401,404,500 |
| VNPay | GET | /api/payments/vnpay/return | PUBLIC | Verify VNPay browser return | 200 | 400,500 |
| VNPay | GET | /api/payments/vnpay/ipn | PUBLIC | Process VNPay notification | 200 |  |
| Wishlist | GET | /api/wishlist | AUTHENTICATED | List my wishlist | 200 | 400,401,404,500 |
| Wishlist | POST | /api/wishlist/{productId} | AUTHENTICATED | Add product to wishlist | 200 | 400,401,404,500 |
| Wishlist | DELETE | /api/wishlist/{productId} | AUTHENTICATED | Remove wishlist product | 200 | 400,401,404,500 |

## Access and response conventions

PUBLIC requires no JWT or role. AUTHENTICATED accepts active USER or ADMIN with a valid JWT; personal resource ownership applies. ROLE_ADMIN requires ADMIN. In Swagger, Authorize with the complete `Bearer <JWT>` value from register/login. An apiKey security scheme transmits the literal Authorization header; authentication still uses JWT. Public operations have no security requirement. Invalid supplied Bearer tokens can still cause 401 except on VNPay callbacks.

Success example:

```json
{"success":true,"message":"Operation-specific message","data":{},"timestamp":"2026-09-12T10:00:00"}
```

Each Swagger operation shows the concrete data DTO. Products, orders, wishlist and reviews use data.content/page/size/totalElements/totalPages/first/last. Categories, brands, images and specifications return lists within data. All paged endpoints: page >= 0, size 1..100, default size 12 except ADMIN orders (20). Non-catalog paged lists use createdAt DESC then id DESC.

Error example:

```json
{"success":false,"message":"Invalid request body or unsupported field value","timestamp":"2026-09-12T10:00:00"}
```

Null data is omitted, including errors and void successes. Dates are server local date-times without offset; money uses decimal values.

| Status | Meaning |
| --- | --- |
| 400 | Invalid body/field/type/enum/pagination/range or business rule; validation reports first field error |
| 401 | Missing/invalid/expired JWT, wrong login credentials, inactive account |
| 403 | ADMIN role required, foreign cart item, or review purchase/delivery requirement not met |
| 404 | Missing resource; personal order/review lookup also hides foreign resources |
| 409 | Duplicate email/catalog name/specification name within product/review by same user and product |
| 500 | Unexpected server/persistence error, generic message |

Per-operation errors appear in the inventory and Swagger. VNPay IPN uses gateway codes under HTTP 200 instead of the normal envelope.

## Request body contracts

All bodies are JSON. Full types, required fields, constraints and response DTOs are in Swagger Schemas.

| Request | Fields and validation |
| --- | --- |
| RegisterRequest | fullName required/nonblank/max 150; email required/valid/max 255, normalized and unique; password required 8..72 characters and <=72 UTF-8 bytes; phone optional |
| LoginRequest | nonblank valid email and nonblank password; password whitespace preserved |
| UpdateProfileRequest | only fullName (required/nonblank/max 150) and optional phone; unknown fields rejected |
| ChangePasswordRequest | currentPassword/newPassword/confirmPassword required/nonblank; current must match; new matches confirmation, 8..72 characters, <=72 UTF-8 bytes; unknown fields rejected |
| CategoryRequest | name required/nonblank/2..100, unique after trim; description optional/max 500 |
| BrandRequest | same name rules; description/logoUrl optional/max 500 each |
| ProductRequest | name required/nonblank/2..200/unique; price required >=0; discountPrice optional >=0 and <=price; quantity required JSON integer >=0; thumbnailUrl max 500; categoryId/brandId required/existing; status optional/default ACTIVE even on update; description optional |
| ProductSpecificationRequest | specName required/nonblank/1..100/unique per product; specValue required/nonblank/1..500; displayOrder optional >=0/default 0 |
| ProductImageRequest | imageUrl required/nonblank/max 500; altText optional/max 255; primary optional; displayOrder optional >=0/default 0; metadata only |
| AddCartItemRequest | productId required/existing; quantity required JSON integer >=1, within stock and total integer limits |
| UpdateCartItemRequest | quantity required JSON integer >=1 within stock; replaces quantity |
| CheckoutRequest | receiverName required/nonblank/max 150; phone required/nonblank/max 30; shippingAddress required/nonblank/max 500; note optional/max 2000; paymentMethod required COD or VNPAY; unknown fields rejected |
| UpdateOrderStatusRequest | only required exact status enum string; ordinals rejected; transition matrix enforced |
| CreateReviewRequest / UpdateReviewRequest | only rating/comment; rating required JSON integer 1..5; stripped comment required/nonblank/1..1000 |

Profile/registration phone pattern: `^$|^[0-9+() .-]{7,30}$`; null is permitted. Quantity/rating strings and fractions are rejected, as are out-of-range integers. Password validation also occurs in services; a password change does not revoke existing JWTs.

### Login and authentication example

```http
POST /api/auth/login
Content-Type: application/json

{"email":"<registered_email>","password":"<account_password>"}
```

```http
GET /api/users/me
Authorization: Bearer <JWT>
```

Register/login return data.accessToken, data.tokenType and safe data.user. Registration always creates USER/ACTIVE, never ADMIN.

### Catalog search example

```http
GET /api/products?keyword=phone&categoryId=1&brandId=1&minPrice=1000000&maxPrice=20000000&status=ACTIVE&page=0&size=12&sort=price,asc
```

Filters combine with AND. Keyword matches product name case-insensitively after trim; existing SQL LIKE wildcard behavior for % and _ remains. Price filters/sort use regular price. No implicit ACTIVE filter. Sort fields: price/name/createdAt, direction asc/desc, default createdAt,desc; id breaks ties in same direction. Price bounds are inclusive, nonnegative and minPrice <= maxPrice.

ADMIN order query supports page/size/status/userId/keyword/fromDate/toDate. Keyword matches receiverName or phone with escaped wildcard characters. Date bounds are inclusive ISO local date-times; fromDate <= toDate. userId positive. Sort is fixed, not a query parameter.

### Cart and checkout / COD example

IDs and customer details below are synthetic; substitute existing local resources.

```http
POST /api/cart/items
Authorization: Bearer <JWT>
Content-Type: application/json

{"productId":1,"quantity":2}
```

```http
POST /api/orders
Authorization: Bearer <JWT>
Content-Type: application/json

{"receiverName":"Example Customer","phone":"0900000000","shippingAddress":"Example delivery address","note":"Call before delivery","paymentMethod":"COD"}
```

Checkout derives user/items/prices/totals from the database. It uses a nonnegative discount below regular price, including zero; otherwise regular price. Atomically snapshots items, deducts stock, creates PENDING order/payment and clears cart. shippingFee=0. Rejects empty carts, inactive products and insufficient stock.

COD has no standalone charge endpoint. Inspect payment through order responses; ADMIN delivery marks COD PAID. Customer cancellation permits only owned PENDING orders. ADMIN transitions: PENDING -> CONFIRMED/CANCELLED; CONFIRMED -> SHIPPING/CANCELLED; SHIPPING -> DELIVERED. Other/repeated transitions return 400. Cancellation restores stock and cancels PENDING payment; PAID blocks cancellation (no refunds). Restoration failure rolls back the whole cancellation.

### Review and wishlist

```http
POST /api/products/1/reviews
Authorization: Bearer <JWT>
Content-Type: application/json

{"rating":5,"comment":"Works as expected."}
```

Review requires a DELIVERED purchase by the current user. One review per user/product; duplicate 409. Owner-only update/delete; ADMIN has no moderation bypass. Public summary returns averageRating/reviewCount, both zero when empty.

Wishlist add is idempotent (200); removing an absent entry succeeds. Product image writes store URL/metadata only. First image becomes primary; selecting primary clears previous primary; deleting it selects another image when available. Passing primary=false on update does not clear an existing primary flag.

## VNPay callbacks

Creation: POST /api/payments/vnpay/create/{orderId}, authenticated owner, no body. Checkout must use VNPAY. Amount must be positive, at most two fractional digits and fit 12 digits after multiplying by 100. Configuration must be valid, payment PENDING and order not CANCELLED/DELIVERED. A valid URL is reused; new attempts after expiry/terminal status are unsupported.

Return: GET /api/payments/vnpay/return, PUBLIC signed query. Returns ApiResponse<VnPayReturnResponse>: orderId, paymentId, persisted paymentStatus, gatewayResponseCode, gatewayTransactionStatus. Read-only, never marks PAID. Invalid callback/configuration returns 400.

IPN: GET /api/payments/vnpay/ipn, PUBLIC signed query. Records PAID/FAILED. HTTP 200 with a bare object, e.g. `{"RspCode":"00","Message":"Confirm Success"}`. Both callbacks skip JWT filtering even when an Authorization header is supplied; gateway signature validation remains mandatory.

| Query field | Contract |
| --- | --- |
| vnp_TmnCode | Required configured sandbox merchant |
| vnp_TxnRef | Required persisted gateway reference; 1..100 alphanumeric characters |
| vnp_Amount | Required exact order/payment VND amount multiplied by 100, 1..12 digits |
| vnp_ResponseCode | Required two digits |
| vnp_TransactionStatus | Required two digits |
| vnp_TransactionNo | Required 1..15 digits; successful payment cannot be all zero |
| vnp_BankCode | Required 3..20 alphanumeric characters |
| vnp_OrderInfo | Required 1..255 printable ASCII characters |
| vnp_PayDate | Optional valid yyyyMMddHHmmss in Asia/Ho_Chi_Minh; absent/empty uses current gateway-zone time when recording success |
| vnp_SecureHash | Required 128-hex-character HMAC-SHA512 signature |
| vnp_SecureHashType | Optional signature metadata, excluded from signature |
| vnp_CardType | Optional gateway field, included in signature when nonempty |

Preserve all supplied vnp_ fields, including additional gateway fields. Repeated vnp_ keys are rejected; non-vnp_ fields ignored. Signature calculation sorts keys, form-URL-encodes keys/values, omits empty values and excludes SecureHash/SecureHashType. Changing signed values invalidates verification; unsigned examples cannot simulate a successful callback.

| RspCode | Meaning |
| --- | --- |
| 00 | Payment result committed |
| 01 | Unknown merchant/reference/order/payment |
| 02 | Already terminal or cancelled; no duplicate mutation |
| 04 | Invalid/mismatched amount |
| 97 | Invalid signature/signature parameters |
| 99 | Invalid fields/configuration, contradictory/unrecognized result or processing/persistence failure |

Gateway result 00/00 marks PAID. Recognized failure response codes with transaction status 02 mark FAILED; other combinations require reconciliation. Callbacks do not advance order status. Late success after cancellation needs operator reconciliation. Automated tests do not contact the live gateway.

## Maintaining documentation

Update controller Operation/Parameter annotations and request Schema descriptions when contracts change. OpenApiConfig contains common error components and literal Bearer header handling. SecurityConfig remains the runtime authority. OpenApiIntegrationTest compares generated operations with actual mappings, checks local references and access labels, validates schemas and exercises real HTTP/JWT authorization.
