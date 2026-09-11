FEATURE 6 — PRODUCT REVIEW + RATING

1. **Branch:** `feature/product-review`. Trước khi code đã kiểm tra branch, status và diff; working tree sạch. Chỉ dùng Git read-only, với `-c safe.directory=E:/electronics-store` cho từng lệnh do sandbox chạy bằng tài khoản khác chủ repository. Không sửa Git config.

2. **File mới:** các đường dẫn Java dưới `src/main/java/com/electronics/store/`:

   | Nhóm | File |
   | --- | --- |
   | Controller | `controller/ReviewController.java` |
   | Request | `dto/request/CreateReviewRequest.java`, `dto/request/UpdateReviewRequest.java` |
   | JSON validation | `dto/request/ReviewRatingDeserializer.java` |
   | Response | `dto/response/ReviewResponse.java`, `dto/response/ProductRatingSummaryResponse.java` |
   | Entity | `entity/ReviewEntity.java` |
   | Mapper | `mapper/ReviewMapper.java` |
   | Repository | `repository/ReviewRepository.java` |
   | Service | `service/ReviewService.java`, `service/impl/ReviewServiceImpl.java` |

   Test mới: `src/test/java/com/electronics/store/review/ReviewIntegrationTest.java`.
   Báo cáo mới: `PRODUCT-REVIEW-REPORT.md`.
   Tổng cộng 13 file mới: 11 production Java, 1 test Java, 1 báo cáo.

3. **File sửa:** `src/main/java/com/electronics/store/config/SecurityConfig.java` và `src/main/java/com/electronics/store/repository/OrderItemRepository.java`.

4. **ReviewEntity:** bảng `reviews`, khóa chính Long IDENTITY; hai quan hệ ManyToOne LAZY bắt buộc tới User và Product; rating Integer; comment tối đa 1000 ký tự; createdAt/updatedAt dùng LocalDateTime và lifecycle callbacks với độ chính xác microsecond. User, product và createdAt không được cập nhật qua cột. Xóa cứng User/Product sẽ dọn review qua database cascade theo convention Wishlist; xóa review không xóa User/Product.

5. **Unique constraint:** `uk_review_user_product(user_id, product_id)`. Service kiểm tra duplicate trước khi lưu và trả 409 với `You have already reviewed this product.`. Các thao tác ghi cùng user dùng lại user-row lock để tuần tự hóa, kể cả khi chưa có review. Database vẫn chặn duplicate khi bypass service.

6. **Validation:** cả create/update có `@NotNull`, `@Min(1)`, `@Max(5)` cho rating; `@NotBlank`, `@Size(min=1,max=1000)` cho comment. Comment được `strip()` trước validation/lưu. Service kiểm tra lại rating/comment; entity có Bean Validation và database CHECK `rating between 1 and 5`. Deserializer chỉ nhận JSON integer, tránh tự đổi `1.5` thành `1`. Request từ chối field ngoài rating/comment, bao gồm userId, productId, id và timestamps.

7. **Verified purchase:** current user lấy qua `UserService.getCurrentUser()`, tái sử dụng JWT → SecurityContext hiện có. Product phải tồn tại. Query OrderItem phải khớp productId, Order thuộc current user và trạng thái DELIVERED. Không nhận userId trong body để xác định chủ review. Chưa mua trả 403 với `You have not purchased this product.`.

8. **Kiểm tra DELIVERED:** `existsByOrderUserIdAndProductIdAndOrderStatus(userId, productId, OrderStatus.DELIVERED)`. OrderItem hiện lưu productId lịch sử, không có quan hệ tới live Product; query giữ nguyên thiết kế này. Nếu có OrderItem nhưng chưa có Order DELIVERED phù hợp, trả 403 với thông báo chỉ được review sau khi giao hàng. Không kiểm tra trạng thái payment thay cho trạng thái Order. Có ít nhất một Order DELIVERED phù hợp là đủ.

9. **Ownership:** update/delete dùng `findByIdAndUserId(reviewId, currentUserId)`. Review không tồn tại hoặc thuộc người khác đều trả 404 `Review not found for current user with id: ...`, không thay đổi database. ADMIN cũng chỉ sửa/xóa review của chính mình; không thêm moderation vào scope.

10. **APIs:** tất cả dùng ApiResponse và GlobalExceptionHandler hiện có; Controller chỉ gọi Service.

    | Method | URL | Quyền | Thành công |
    | --- | --- | --- | --- |
    | GET | `/api/products/{productId}/reviews` | Public | 200, PagedResponse |
    | GET | `/api/products/{productId}/rating-summary` | Public | 200, ProductRatingSummaryResponse |
    | POST | `/api/products/{productId}/reviews` | Authenticated + verified purchase | 201, ReviewResponse |
    | PUT | `/api/reviews/{reviewId}` | Authenticated + owner | 200, ReviewResponse |
    | DELETE | `/api/reviews/{reviewId}` | Authenticated + owner | 200 |

    Product không tồn tại khi GET reviews, GET summary hoặc POST trả 404 ResourceNotFoundException. ID không hợp lệ trả 400. JWT thiếu/sai hoặc user inactive bị chặn trước thao tác ghi.

11. **Pagination:** `page=0`, `size=12` mặc định theo Wishlist; page không âm, size từ 1 đến 100. Sắp xếp `createdAt DESC, id DESC` để ổn định khi trùng thời gian. Query phân trang tại database, có index `idx_reviews_product_created(product_id, created_at, id)`. EntityGraph fetch user cùng trang review, tránh query user riêng cho từng dòng; mapping chạy trong transaction.

12. **averageRating:** tính trực tiếp bằng SQL AVG qua JPQL constructor projection, dùng Double, không chia số nguyên. Không lưu cache/giá trị tổng hợp trên Product. Không có review trả `0.0`; thống kê phản ánh dữ liệu đã commit sau create/update/delete.

13. **reviewCount:** Long, tính COUNT trong cùng query với AVG; không có review trả `0`. Không load toàn bộ reviews để thống kê.

14. **ProductResponse:** giữ nguyên. Chọn DTO và endpoint rating summary riêng vì ProductResponse đang được dùng trong Product và Wishlist; cách này tránh thêm query rating cho từng Product và giữ mapper chỉ làm mapping.

15. **Repository queries:** ReviewRepository cung cấp `findByProductIdOrderByCreatedAtDescIdDesc`, `existsByUserIdAndProductId`, `findByIdAndUserId`, `findByUserIdAndProductId`, `countByProductId`, `getProductRatingSummary`. OrderItemRepository bổ sung hai exists query theo user/product, có và không có status. SQL kiểm tra purchase thực tế dùng JOIN Order và giới hạn một kết quả; không load tất cả Orders. Không tạo RepositoryImpl.

16. **Transactions:** ServiceImpl mặc định `@Transactional(readOnly=true)`; create/update/delete dùng `@Transactional(isolation=READ_COMMITTED)`. Dùng lại `UserRepository.findByEmailForUpdate` cho các thao tác ghi. READ_COMMITTED giúp request đang chờ lock nhìn thấy review vừa commit; `saveAndFlush`/`flush` hoàn tất persistence và timestamps trước khi trả response. Lỗi persistence rollback transaction.

17. **Create review test: PASS.** Rating 1 và 5 với Order DELIVERED tạo 201; trim comment đúng; user summary chỉ có id/fullName; timestamps và response public đúng. Có test Cart → Checkout COD → Admin CONFIRMED/SHIPPING/DELIVERED → Review.

18. **Not purchased test: PASS.** Order DELIVERED của user khác hoặc OrderItem của product khác không cấp quyền review. Không tạo dữ liệu review.

19. **Not delivered test: PASS.** PENDING, CONFIRMED, SHIPPING và CANCELLED đều bị từ chối. Có Order DELIVERED phù hợp cùng các Order chưa giao vẫn được review.

20. **Duplicate test: PASS.** POST lần hai trả 409 và giữ nguyên review cũ; unique constraint chặn bypass service. Hai HTTP requests đồng thời trả đúng một 201, một 409 và database chỉ có một review.

21. **Rating/comment validation test: PASS.** Rating null/-1/0/6 bị từ chối; 1/5 được chấp nhận. JSON số lẻ, string, boolean, object, array và integer overflow bị từ chối. Comment null/rỗng/chỉ whitespace/quá 1000 bị từ chối; chấp nhận biên 1/1000 sau strip. Kiểm tra cả create/update và dữ liệu cũ được giữ nguyên khi validation fail. Database CHECK cũng được test qua JDBC.

22. **Update ownership test: PASS.** Owner sửa rating/comment thành công, giữ id/user/createdAt và cập nhật updatedAt. User khác và ADMIN không sửa được, kể cả thêm query userId giả mạo. Body không được đổi trường bất biến.

23. **Delete ownership test: PASS.** Owner xóa thành công; user khác/ADMIN không xóa được. Review không tồn tại trả 404. Xóa review giữ Product/User; có thể tạo review mới sau khi đã xóa nếu vẫn đủ điều kiện.

24. **Rating summary test: PASS.** Rating 5/4/3 → average 4.0, count 3; xóa rating 3 → 4.5, count 2; xóa hết → 0.0, 0. Update rating cập nhật summary; review thuộc Product khác không ảnh hưởng. Có test phân trang/thứ tự và test rollback khi persistence lỗi.

25. **Regression: PASS.** 438 test có sẵn cho Auth/JWT, User Profile, Category, Brand, Product/Search, Cart, Checkout/Orders, cancellation, Admin Orders, COD, VNPay và Wishlist vẫn pass. Test tích hợp mới xác nhận review không thay đổi Order/Payment/stock và xóa cứng Product có review vẫn giữ OrderItem lịch sử. Tests dùng H2 MySQL mode; chưa chạy integration trên MySQL thật.

26. **Build/test cuối: BUILD SUCCESS, exit code 0.** Tổng 496 tests = 438 có sẵn + 58 Review; failures 0, errors 0, skipped 0. Lệnh chạy tại backend: `mvn -o '-Dmaven.repo.local=C:\Users\DVT\.m2\repository' clean test`. Đây là lifecycle clean test đầy đủ; `-o` dùng cache dependency hiện có. Lần đầu sandbox không tạo được cache mặc định và lần kế tiếp không truy cập được một dependency JAR khi compile; lần cuối chạy ngoài sandbox với cache sẵn có đã thành công. Log: `backend/review-clean-test.log` (ignored); Surefire reports trong `backend/target/surefire-reports/` (ignored).

27. **Git status cuối:** 2 file modified và 13 file mới untracked nêu trên; không staged. `git diff --check` không báo lỗi whitespace. Không có frontend changes, `.env`, `target/`, log hoặc file ngoài Feature 6 trong danh sách thay đổi. Không chạy add/commit/push/pull/fetch/switch/checkout/merge/rebase/reset/clean.

28. **Secrets:** không thêm password thật, JWT secret, VNPay secret, access token thật hoặc `.env` thật vào các file thay đổi. Test dùng chuỗi password giả `unused-test-password` và JWT sinh động từ cấu hình test có sẵn; không hardcode token/secret mới. Không có gì staged để commit.

Đã dừng tại FEATURE 6. Không triển khai FEATURE 7; người dùng tự review và xử lý Git.
