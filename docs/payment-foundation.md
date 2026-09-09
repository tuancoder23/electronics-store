# Payment foundation + COD

Feature 3 bổ sung Payment vào vòng đời Order. Checkout hiện chỉ nhận COD.
`PaymentMethod` dùng chung với Order có `COD`, `VNPAY`; giá trị `VNPAY`
được khai báo cho bước tích hợp sau và vẫn bị checkout từ chối với HTTP 400.
Không có gateway, callback, IPN, refund hoặc API thanh toán riêng trong feature này.

## Dữ liệu

`PaymentEntity` ánh xạ bảng `payments`:

| Field | Kiểu / ràng buộc |
| --- | --- |
| id | Long, identity |
| order | OneToOne tới Order, `order_id` bắt buộc, FK và unique `uk_payments_order` |
| method | PaymentMethod, EnumType.STRING |
| status | PaymentStatus: PENDING, PAID, FAILED, CANCELLED; EnumType.STRING |
| amount | BigDecimal, decimal(30,2), không âm; snapshot từ Order.totalAmount |
| transactionCode | String tối đa 255 ký tự, nullable; COD không sinh mã gateway |
| paidAt | LocalDateTime nullable |
| createdAt / updatedAt | LocalDateTime, callbacks giống các entity hiện có |

Order có liên kết ngược `payment`, không cascade xóa Payment. Service quản lý
vòng đời Payment; Mapper chỉ chuyển entity sang DTO. `order`, `method`, `amount`
của Payment không được cập nhật bằng JPA sau khi insert.

Schema tiếp tục dùng cấu hình `spring.jpa.hibernate.ddl-auto=update` hiện có.
Không thêm công cụ migration hoặc thay cấu hình database.

## API và quyền truy cập

Giữ nguyên các API Order. `OrderResponse` giữ nguyên `paymentMethod` và các field
cũ, bổ sung `payment: PaymentResponse` ở checkout, detail, history, admin list,
admin detail và response cập nhật trạng thái/hủy đơn. Ví dụ phần dữ liệu:

```json
{
  "id": 10,
  "status": "PENDING",
  "paymentMethod": "COD",
  "totalAmount": 23990000,
  "payment": {
    "id": 1,
    "method": "COD",
    "status": "PENDING",
    "amount": 23990000,
    "transactionCode": null,
    "paidAt": null,
    "createdAt": "2026-09-09T12:00:00",
    "updatedAt": "2026-09-09T12:00:00"
  }
}
```

User xem qua `GET /api/orders/{orderId}` và `GET /api/orders/my-orders`.
Ownership tiếp tục lấy từ JWT/SecurityContext: đơn của user khác trả 404.
Admin xem qua `/api/admin/orders` và `/api/admin/orders/{orderId}` với ROLE_ADMIN.
PaymentResponse không chứa entity, user, order, password hay credential.
Checkout không nhận amount, paymentStatus, paidAt, transactionCode hoặc userId từ client.

## Vòng đời COD

| Sự kiện hợp lệ | Order | Payment |
| --- | --- | --- |
| Checkout COD | PENDING | Tạo PENDING, amount = totalAmount, paidAt/transactionCode = null |
| Admin xác nhận | PENDING → CONFIRMED | Giữ PENDING |
| Admin giao hàng | CONFIRMED → SHIPPING | Giữ PENDING |
| Admin giao thành công | SHIPPING → DELIVERED | PENDING → PAID, ghi paidAt |
| User hủy | PENDING → CANCELLED | PENDING → CANCELLED, restore stock theo OrderItems |
| Admin hủy | PENDING/CONFIRMED → CANCELLED | PENDING → CANCELLED, restore stock theo OrderItems |

Payment PAID làm yêu cầu hủy bị từ chối (HTTP 400), rollback cả Order và stock;
không refund hoặc đổi Payment thành CANCELLED. Nếu Payment đã FAILED/CANCELLED,
hủy Order hợp lệ giữ nguyên trạng thái Payment; delivery bị từ chối.
Gọi lại nội bộ `markCodAsPaid` cho COD DELIVERED/PAID không ghi lại paidAt.
API Order vẫn từ chối transition lặp theo rule hiện có.

## Transaction và duplicate

OrderService tiếp tục dùng `@Transactional(isolation = READ_COMMITTED)`.
PaymentService dùng `Propagation.MANDATORY`: bắt buộc tham gia transaction của
Order, không commit riêng. Các method là nội bộ, được gọi sau authorization.

PaymentService khóa Order bằng `PESSIMISTIC_WRITE` trước khi đọc/tạo/cập nhật
Payment. Đây cũng là khóa dùng trong admin update và user cancel. Tạo Payment
kiểm tra `existsByOrderId` dưới khóa và báo DuplicateResourceException nếu đã có;
unique constraint chặn duplicate ngay cả khi code bypass service.

Checkout bao gồm Order + OrderItems + Payment + stock + clear cart trong một
transaction. Lỗi insert Payment hoặc lỗi flush cart sau đó rollback tất cả.
Delivery đồng bộ Order DELIVERED với Payment PAID. Cancellation đồng bộ Order,
stock và Payment; lỗi stock hoặc Payment rollback tất cả. `saveAndFlush` đưa lỗi
database ra trong transaction. Product locks và thứ tự restore stock giữ nguyên.

## Order cũ chưa có Payment

Không tự sửa dữ liệu trong API GET. Order từ trước feature này vẫn đọc được,
`payment` trả null. Khi COD Order cũ được giao thành công hoặc hủy qua transition
hợp lệ, service tạo Payment từ totalAmount đã lưu rồi đồng bộ PAID/CANCELLED
trong cùng transaction và dưới khóa Order. Đơn lịch sử đã ở trạng thái cuối
không được backfill hàng loạt trong feature này.

## Kiểm thử

`PaymentIntegrationTest` dùng HTTP thật, JWT filter thật và H2 MySQL mode;
không có transaction rollback ở cấp test che giấu lỗi commit. Bao phủ:

- COD checkout, amount 23,990,000 / zero / phần thập phân, Payment tồn tại trong DB.
- Từ chối field payment do client gửi và checkout VNPAY.
- Delivery chỉ PAID khi DELIVERED; paidAt không bị ghi lại.
- User/admin cancel, giữ stock đúng; chặn cancel Payment PAID.
- Unique constraint, duplicate service, hai lời gọi tạo Payment đồng thời.
- Lỗi database khi tạo Payment, sau insert Payment, khi delivery và khi cancel.
- Lỗi restore stock; ownership, history/admin detail; Order cũ chưa có Payment.
- Concurrent user/admin cancel và concurrent delivery.

Chạy tại `backend`:

```text
mvn clean test
```

Các test Order/checkout/admin/user cancel trước đó chỉ bổ sung bước xóa Payment
trong setup trước khi xóa Order để tuân theo FK mới; giữ nguyên assertions.
Toàn bộ suite bao gồm regression authentication, profile, catalog, search và cart.
Kết quả H2 không thay thế kiểm chứng trên MySQL thật.
