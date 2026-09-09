# Checkout + Order review

Ngày kiểm tra: 2026-09-06. Branch: `feature/checkout-order`.

Phạm vi: Checkout, Order, OrderItem; chỉ thanh toán COD. Giữ kiến trúc
Controller → Service → ServiceImpl → Repository → Database.

## File tạo mới

Các đường dẫn dưới đây tương đối với `backend/src/main/java/com/electronics/store/`:

| Nhóm | File |
| --- | --- |
| Controller | `controller/OrderController.java` |
| Request | `dto/request/CheckoutRequest.java` |
| Response | `dto/response/OrderResponse.java`, `dto/response/OrderItemResponse.java` |
| Entity | `entity/OrderEntity.java`, `entity/OrderItemEntity.java` |
| Enum | `entity/OrderStatus.java`, `entity/PaymentMethod.java` |
| Mapper | `mapper/OrderMapper.java`, `mapper/OrderItemMapper.java` |
| Repository | `repository/OrderRepository.java`, `repository/OrderItemRepository.java` |
| Service | `service/OrderService.java`, `service/impl/OrderServiceImpl.java` |

Ngoài ra:

- `backend/src/test/java/com/electronics/store/order/OrderIntegrationTest.java`
- `docs/checkout-order.md`

## File sửa

Các đường dẫn tương đối với `backend/src/main/java/com/electronics/store/`:

| File | Thay đổi |
| --- | --- |
| `config/SecurityConfig.java` | Khai báo rõ `/api/orders` và `/api/orders/**` cần authentication. |
| `exception/GlobalExceptionHandler.java` | Trả ApiResponse 400 cho JSON không hợp lệ, field không hỗ trợ và paymentMethod không hợp lệ. |
| `repository/ProductRepository.java` | Thêm truy vấn product với `PESSIMISTIC_WRITE`. |
| `repository/CartRepository.java` | Thêm truy vấn cart theo user với `PESSIMISTIC_WRITE`. |
| `service/impl/CartServiceImpl.java` | Dùng cùng khóa cart và isolation READ_COMMITTED để phối hợp với checkout. |

## Entity và snapshot

`OrderEntity` dùng bảng `orders`, ID auto increment, quan hệ ManyToOne LAZY tới user
bắt buộc, không cascade sang user. Lưu receiverName, phone, shippingAddress, note,
subtotal, shippingFee, totalAmount, status, paymentMethod, createdAt, updatedAt.
Thông tin nhận hàng bắt buộc; note tùy chọn. Validation độ dài khớp cột database.
Tiền dùng BigDecimal, không âm. Tổng tiền dùng DECIMAL(30,2) để chứa tích giá × số lượng.
Status và paymentMethod lưu EnumType.STRING; đơn mới luôn PENDING, shippingFee bằng 0.
Timestamp theo convention LocalDateTime với PrePersist/PreUpdate.

`OrderItemEntity` dùng bảng `order_items`, ID auto increment, ManyToOne LAZY tới order.
Order quản lý items bằng cascade ALL và orphanRemoval. Item lưu productId, productName,
unitPrice, quantity > 0 và lineTotal = unitPrice × quantity.
productId là giá trị lịch sử, không có FK tới Product: đổi giá, đổi tên hoặc xóa product
không làm thay đổi order cũ. Mapper chỉ đọc snapshot, API không trả entity hoặc dữ liệu security của user.

## Checkout, transaction và locking

1. Lấy email từ Authentication trong SecurityContext, tìm user bằng UserRepository.
2. Khóa cart của user. Cart thiếu hoặc rỗng trả 400 với `Cart is empty.`.
3. Đọc CartItems; khóa từng product theo ID tăng dần để giảm nguy cơ deadlock.
4. Kiểm tra product tồn tại, ACTIVE, số lượng hợp lệ và stock đủ. Không giảm số lượng khách yêu cầu.
5. Lấy giá hiện tại từ product trong database: discountPrice >= 0 và < price thì dùng discountPrice;
   các trường hợp khác dùng price. Giá không đến từ client hoặc CartResponse.
6. Tạo snapshot items, cộng subtotal; shippingFee = 0, totalAmount = subtotal + shippingFee.
7. Trừ stock; nếu còn 0 thì cập nhật OUT_OF_STOCK.
8. Lưu order và items, xóa các CartItems đã checkout, giữ CartEntity rỗng, trả OrderResponse.

Toàn bộ thao tác chạy trong một `@Transactional(isolation = READ_COMMITTED)`.
Lỗi business hoặc persistence rollback stock, Order, OrderItems và cart cùng nhau.
Flush được thực hiện bên trong transaction.

Khóa cart được giữ tới commit và cũng được các thao tác cart sử dụng. Checkout thứ hai
cho cùng cart phải đọc lại cart sau khi chờ. READ_COMMITTED tránh đọc snapshot cũ sau khi chờ khóa.
Product được tải dưới khóa PESSIMISTIC_WRITE; việc sắp xếp CartItems chỉ đọc ID proxy,
không tải trạng thái product trước khóa. Hai user mua cùng stock phải xử lý tuần tự tại product.
Không có retry tự động hoặc API chuyển trạng thái trong feature này.

## API và ownership

| Method | Endpoint | Kết quả |
| --- | --- | --- |
| POST | `/api/orders` | 201, ApiResponse<OrderResponse> |
| GET | `/api/orders/my-orders?page=0&size=12` | 200, ApiResponse<PagedResponse<OrderResponse>> |
| GET | `/api/orders/{orderId}` | 200, ApiResponse<OrderResponse> |

Ví dụ body checkout:

```json
{
  "receiverName": "Nguyen Van A",
  "phone": "0901234567",
  "shippingAddress": "123 Nguyen Trai",
  "note": "Goi truoc khi giao",
  "paymentMethod": "COD"
}
```

Request không nhận userId, cartId, subtotal, totalAmount, orderStatus hoặc giá.
Field không thuộc CheckoutRequest bị từ chối 400; không thay đổi cấu hình JSON toàn project.
PaymentMethod không hỗ trợ hoặc thiếu, thông tin nhận hàng blank và pagination sai trả 400.

Các API cần JWT hợp lệ; thiếu/sai JWT trả 401. Đọc order bằng truy vấn id + current user ID;
order của user khác và order không tồn tại đều trả 404. List luôn lọc current user,
sắp xếp createdAt DESC rồi id DESC, page >= 0 và size từ 1 đến 100.

## Kết quả kiểm tra

`OrderIntegrationTest` chạy 34 test/case với server Spring Boot cổng ngẫu nhiên,
HTTP thật qua TestRestTemplate, JWT filter thật và H2 MySQL mode. Không bọc test trong transaction;
các assertion kiểm tra dữ liệu đã commit. Chạy với open-in-view=false.

| Trường hợp | Kết quả |
| --- | --- |
| Checkout quantity 3, stock 10 | Pass: stock 7, đơn PENDING/COD, cart rỗng; cart user khác được giữ. |
| Stock 2, cart quantity 3 | Pass: 400, stock 2, cart giữ nguyên, không có order/items. |
| 100000 × 2 + 50000 × 3 | Pass: subtotal và totalAmount 350000, shippingFee 0, hai items đúng. |
| Product A đủ, Product B thiếu | Pass: rollback toàn bộ, stock A không giảm, cart giữ nguyên. |
| Lỗi persistence khi insert OrderItem | Pass: không để lại order hoặc stock bị trừ. |
| Snapshot 23990000, sau đó đổi giá thành 30000000 | Pass: order cũ vẫn 23990000; H2 còn kiểm tra đổi tên và xóa product. |
| Giá thay đổi sau khi thêm vào cart | Pass: checkout dùng giá mới từ database. |
| Discount âm, bằng/lớn hơn giá gốc; discount 0 | Pass: fallback đúng; discount 0 được chấp nhận. |
| INACTIVE/OUT_OF_STOCK, cart thiếu/rỗng | Pass: từ chối checkout. |
| Validation, paymentMethod, field do backend quản lý | Pass: 400. |
| Auth và ownership | Pass: thiếu/sai JWT 401, user B xem order A 404, list user B không lộ order A. |
| List và pagination | Pass: mới nhất trước, giới hạn page/size hoạt động. |
| Hai user tranh stock 1 | Pass: một 201, một 400; stock 0, chỉ một order. |
| Hai checkout cùng cart | Pass: chỉ một order, stock chỉ trừ một lần. |
| Regression | Pass: health, categories, brands, products, search/filter/sort, register/login, users/me, cart GET/POST. |

Lệnh build trong `backend`:

```powershell
mvn '-Dmaven.repo.local=C:\Users\DVT\.m2\repository' clean test
```

Kết quả: **BUILD SUCCESS — 173 tests, 0 failures, 0 errors, 0 skipped**.
Tham số local repository cần thiết vì Maven trong sandbox mặc định trỏ tới `C:\.m2\repository`
không ghi được; sử dụng cache dependency có sẵn, không sửa pom.xml.

Sau build đã chạy `mvn spring-boot:run` với cổng 18080, JWT key tạm trong environment và
database MySQL 9.5 local riêng cho lượt kiểm thử. Script HTTP tạm kiểm tra **61 assertion, tất cả pass**:
checkout, cart rỗng, stock, nhiều product, thiếu stock/rollback, snapshot, JWT/ownership,
pagination, validation, regression, hai user tranh stock cuối và hai checkout cùng cart.
Server đã dừng và database tạm đã xóa sau kiểm thử.

H2 test chạy tự động khi `mvn test`; lượt MySQL là kiểm tra runtime riêng, không phải test MySQL tự động trong suite.

## Git review

Trước thay đổi: đúng branch, working tree sạch. Sau thay đổi: 5 file sửa và 16 file mới
(14 source production, 1 integration test, 1 báo cáo); tất cả chưa stage.
`git diff --check` không báo lỗi whitespace.

Không thay đổi frontend, pom.xml hoặc module ngoài scope. Không thêm .env thật, password,
JWT key hoặc DB credential vào thay đổi. target/ và log vẫn được gitignore loại trừ.
application.yml đã có sẵn DB password fallback từ trước task; file này không được sửa
và giá trị đó không được chép vào báo cáo hay file mới.

Không chạy git add, commit, push hoặc merge. Các thay đổi được để nguyên để review.
