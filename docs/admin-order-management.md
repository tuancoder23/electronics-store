# Admin Order Management

Ngày kiểm tra: 2026-09-09. Branch: `feature/admin-order-management`.

## Phạm vi và Git

Trước khi sửa: đã chạy `git branch --show-current`, `git status`, `git diff`.
Branch đúng, working tree sạch. Chỉ triển khai quản lý đơn hàng cho admin theo
Controller → Service → ServiceImpl → Repository → Database.

### File tạo mới

Các đường dẫn Java bên dưới tương đối với `backend/src/main/java/com/electronics/store/`:

| File | Mục đích |
| --- | --- |
| `controller/AdminOrderController.java` | Ba API admin, tách khỏi API đơn hàng của user. |
| `dto/request/UpdateOrderStatusRequest.java` | Chỉ nhận status hợp lệ, bắt buộc; từ chối field lạ và enum dạng số. |
| `dto/request/OrderSearchCriteria.java` | Các điều kiện tìm đơn hàng. |
| `repository/OrderSpecification.java` | Filter tại database bằng JPA Specification. |

Ngoài ra:

- `backend/src/test/java/com/electronics/store/order/AdminOrderIntegrationTest.java`
- `docs/admin-order-management.md`

### File sửa

| File, tương đối với package gốc | Thay đổi |
| --- | --- |
| `repository/OrderRepository.java` | Thêm JpaSpecificationExecutor và truy vấn khóa order bằng PESSIMISTIC_WRITE. |
| `service/OrderService.java` | Thêm getAllOrders, getOrderByIdForAdmin, updateOrderStatus. |
| `service/impl/OrderServiceImpl.java` | Validation/filter, đọc đơn cho admin, quy tắc trạng thái và hoàn kho. |

## API và security

| Method | Endpoint | Kết quả thành công |
| --- | --- | --- |
| GET | `/api/admin/orders` | 200, ApiResponse chứa PagedResponse<OrderResponse>. |
| GET | `/api/admin/orders/{orderId}` | 200, ApiResponse chứa OrderResponse. |
| PUT | `/api/admin/orders/{orderId}/status` | 200, ApiResponse chứa OrderResponse sau cập nhật. |

SecurityConfig đã có `/api/admin/**` → `hasRole("ADMIN")`, bao phủ cả ba API.
Không cần sửa cấu hình security. ROLE_USER nhận 403; không JWT hoặc JWT sai nhận 401.
API `/api/orders/**` vẫn cần authentication và giữ ownership theo user.
Admin detail không áp ownership; đơn không tồn tại gây ResourceNotFoundException và trả 404.

Tái sử dụng OrderResponse/OrderMapper/ApiResponse: thông tin giao hàng, items snapshot,
totals, status, paymentMethod, createdAt, updatedAt. Không trả entity, user entity,
password, authorities hoặc token; không thêm user summary trong phạm vi này.

Body cập nhật:

```json
{"status":"CONFIRMED"}
```

Thiếu/null status, tên trạng thái không hợp lệ, số thứ tự enum, JSON sai và field lạ
(ví dụ totalAmount, userId, stock, items) đều trả 400. Deserializer chỉ áp dụng cho
DTO này, không đổi quy tắc JSON của các API khác.

## Pagination và filter

- Mặc định page=0, size=20; page >= 0 và size từ 1 đến 100.
- Sort cố định createdAt DESC, id DESC để ổn định khi trùng thời gian tạo.
- Optional: status, userId, keyword, fromDate, toDate; kết hợp bằng AND.
- keyword tìm chuỗi con không phân biệt hoa thường ở receiverName OR phone,
  bỏ khoảng trắng đầu/cuối. Các ký tự `%`, `_`, `!` được escape để tìm theo nghĩa đen.
- fromDate/toDate là ISO local date-time, ví dụ `2026-09-09T00:00:00`, cùng cách biểu diễn
  LocalDateTime hiện tại của project; hai đầu mút được tính bao gồm.
- fromDate > toDate, userId không dương, enum/ngày/tham số sai trả 400.
- JPA Specification thực hiện filter, sort, count và pagination trong database.
  Không tải toàn bộ đơn hàng để lọc bằng Java Stream.

Ví dụ: `/api/admin/orders?status=PENDING&userId=5&keyword=Nguyen&page=0&size=20`.

## Quy tắc trạng thái

| Hiện tại | Được chuyển tới |
| --- | --- |
| PENDING | CONFIRMED, CANCELLED |
| CONFIRMED | SHIPPING, CANCELLED |
| SHIPPING | DELIVERED |
| DELIVERED | Không có |
| CANCELLED | Không có |

ServiceImpl dùng switch kiểm tra whitelist trên trạng thái vừa đọc dưới khóa.
Các cặp khác, bao gồm gửi lại cùng trạng thái ở cả năm trạng thái, trả business error 400:
`Cannot change order status from <current> to <desired>`.
Lựa chọn này nhất quán, không tạo update timestamp vô nghĩa và không hoàn kho lại.
DELIVERED là trạng thái cuối, không cho hủy và không hoàn kho.

## Hoàn kho và transaction

`updateOrderStatus` dùng `@Transactional(isolation = READ_COMMITTED)`:

1. Khóa Order bằng PESSIMISTIC_WRITE, sau đó mới kiểm tra transition.
2. Chỉ khi chuyển sang CANCELLED, lấy productId snapshot của từng OrderItem.
3. Khóa các Product theo productId tăng dần, cùng thứ tự với checkout.
4. Cộng quantity của item vào tồn kho hiện tại; kiểm tra dữ liệu không hợp lệ và tràn Integer.
5. Nếu OUT_OF_STOCK và tồn kho sau cộng > 0, chuyển ACTIVE. INACTIVE được giữ nguyên.
6. Cập nhật status và saveAndFlush trong transaction trước khi trả response.

Product đã bị xóa: trả business error 400, từ chối toàn bộ lần hủy. Không bỏ qua item,
không tự tạo lại Product. Stock, ProductStatus và OrderStatus cùng rollback khi có lỗi;
lỗi persistence trả 500 theo GlobalExceptionHandler hiện tại. Snapshot items, totals và
cart không bị sửa bởi thao tác hủy.

Khóa order giữ tới commit bảo vệ cả hai request hủy đồng thời: request sau đọc trạng thái
đã commit và bị từ chối. Khóa product phối hợp với checkout và hủy các đơn khác dùng chung
sản phẩm, tránh mất cập nhật tồn kho. READ_COMMITTED tránh đọc snapshot cũ sau khi chờ khóa.

## Kết quả test

`AdminOrderIntegrationTest`: **62 test/case pass**. Server Spring Boot cổng ngẫu nhiên,
HTTP thật qua TestRestTemplate, admin login thật và JWT filter thật. H2 MySQL mode,
open-in-view=false; test không bọc trong transaction, assertion đọc dữ liệu đã commit.

| Nhóm | Kết quả |
| --- | --- |
| Admin list | Pass: mọi user, page/size/default, tổng số trang, newest first và id khi trùng thời gian. |
| Filter | Pass: status/userId/keyword/ngày kết hợp, receiver/phone, ký tự wildcard theo nghĩa đen, khoảng ngày bao gồm hai đầu. |
| USER authorization | Pass: GET list/detail và PUT status đều 403; JWT thiếu/sai đều 401. |
| Detail | Pass: admin xem đơn của user khác, snapshot/totals đầy đủ, không security data; đơn thiếu 404. |
| Status transition | Pass: toàn bộ 25 cặp từ 5 trạng thái, gồm 5 transition hợp lệ và 20 bị từ chối. |
| Delivery | Pass: PENDING → CONFIRMED → SHIPPING → DELIVERED; cấm hủy sau giao, không hoàn kho. |
| Same status | Pass: 400, không sửa updatedAt hay stock. |
| Request validation | Pass: null/thiếu/sai status, numeric enum, JSON lỗi, field do backend quản lý. |
| Cancel stock | Pass: stock 8 + quantity 2 = 10; hủy từ PENDING hoặc CONFIRMED. |
| Nhiều item/double cancel | Pass: hoàn đúng từng sản phẩm, lần hủy thứ hai không tăng stock, snapshot/totals giữ nguyên. |
| ProductStatus | Pass: OUT_OF_STOCK → ACTIVE; INACTIVE vẫn INACTIVE. |
| Rollback | Pass: product thứ hai đã xóa, tràn stock hoặc lỗi constraint database đều rollback toàn bộ. |
| Concurrent | Pass: cùng đơn chỉ hoàn một lần; hai đơn cùng product không mất stock; cancel cùng checkout cho tồn kho chính xác. |
| Regression | Pass: health, public catalog/search/filter/sort, auth, users/me, cart, checkout, my-orders, user order detail/ownership. |

Lệnh đã chạy trong `backend` (offline dùng dependency cache có sẵn):

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\DVT\.m2\repository' clean test
```

**BUILD SUCCESS: 235 tests, 0 failures, 0 errors, 0 skipped**
(62 test admin mới + 173 test hiện có). Log: `backend/admin-order-test.log`.
Lệnh chạy ngoài sandbox do Java trong sandbox không đọc được một số file Maven cache.
Không sửa pom.xml hoặc dependency để khắc phục môi trường.

Sau đó đã chạy `mvn -o '-Dmaven.repo.local=C:\Users\DVT\.m2\repository' spring-boot:run`
trên cổng 18081 với MySQL 9.5.0-commercial, database riêng cho lượt kiểm thử,
JWT key ngẫu nhiên qua environment và open-in-view=false.

Script HTTP runtime đã qua **474 assertion**: admin/user login, cả ba admin API,
pagination/filter, cả 25 cặp trạng thái, validation, stock/status restoration, double cancel,
product đã xóa, lỗi constraint khi hoàn kho, concurrent cancel và checkout, regression API user.
Lỗi constraint được tạo riêng trong database test và gỡ sau kiểm tra.
Đây là lượt kiểm thử MySQL riêng, không phải một phần MySQL tự động của Maven suite.
Log kết quả: `backend/admin-order-smoke.log`; log server: `backend/admin-order-runtime.log`.
Server đã dừng và database test đã xóa sau khi hoàn tất.

## Git review cuối

- Branch: `feature/admin-order-management`.
- 3 file sửa, 6 file mới (4 production Java, 1 integration test, 1 báo cáo); chưa stage.
- `git diff --check` sạch; cảnh báo LF/CRLF chỉ là cấu hình line ending trên Windows.
- Không sửa frontend, entity/schema, pom.xml, cấu hình ứng dụng hoặc module ngoài scope.
- Không thêm password/JWT secret/DB secret thật, .env thật hoặc target vào thay đổi.
  Test tạo password ngẫu nhiên; test dùng cấu hình JWT test có sẵn. Log và target được gitignore.
- application.yml có DB password fallback từ trước task; file này không thay đổi và
  giá trị đó không được chép vào file mới hoặc báo cáo.
- Không chạy git add, git commit, git push hoặc git merge. Dừng để người dùng review.
