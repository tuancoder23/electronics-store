# Backend documentation

Start with [project setup and architecture](../README.md), then use the
[API reference](api-spec.md) and [Swagger UI](http://localhost:8080/swagger-ui/index.html).

| Document | Contents |
| --- | --- |
| [api-spec.md](api-spec.md) | Current 55-operation inventory, authentication, payloads, errors and VNPay callbacks |
| [backend-api-documentation.md](backend-api-documentation.md) | Feature 9 changes and final verification results |
| [backend-core-testing.md](backend-core-testing.md) | Feature 8 regression test coverage and results |
| [quantity-validation.md](quantity-validation.md) | Strict integer quantities, stock limits and rollback |
| [user-profile.md](user-profile.md) | Profile/password behavior and verification |
| [checkout-order.md](checkout-order.md) | Checkout transactions, snapshots and original implementation review |
| [admin-order-management.md](admin-order-management.md) | Order filtering, transitions and inventory restoration |
| [payment-foundation.md](payment-foundation.md) | Payment model and COD lifecycle |
| [vnpay-sandbox.md](vnpay-sandbox.md) | VNPay sandbox setup, signatures and callback behavior |

Feature review documents are historical records; their test counts and scope reflect
the feature at that time. For the current contract, use api-spec.md and generated
OpenAPI. The current checkout supports both COD and configured VNPay sandbox payments.
