# Quantity validation

Cart and product APIs require `quantity` to be a JSON integer.

| Endpoint | Allowed quantity |
| --- | --- |
| `POST /api/cart/items` | 1 to 2147483647, subject to available stock |
| `PUT /api/cart/items/{id}` | 1 to 2147483647, subject to available stock |
| `POST /api/admin/products` | 0 to 2147483647 |
| `PUT /api/admin/products/{id}` | 0 to 2147483647 |

For example, `{"quantity":2}` is valid. Fractions (`1.5`), decimal notation
(`1.0`), strings (`"2"`), booleans, objects, arrays, null, missing quantities,
and values outside the allowed range return HTTP 400. Clients must send integer
JSON values instead of relying on automatic conversion.

Adding the same product increments its quantity without integer overflow.
The total quantity across the cart cannot exceed 2147483647. Rejected mutations
roll back, preserving the previous cart contents and totals. Cart changes do not
reserve stock; checkout validates current stock while holding product locks.

Regression coverage is in `CartIntegrationTest` and `ProductControllerTest`,
including rejection of invalid input for both create/update routes and rollback
when either an item quantity or the cart total exceeds its limit.
