# Feature 1 — User Profile + Change Password

Ngày thực hiện: 2026-09-09. Branch: `feature/user-profile`.

## Git và phạm vi

Trước khi sửa, đã chạy `git branch --show-current`, `git status`, `git diff`.
Working tree sạch. HEAD, develop và origin/develop cùng commit
`f63db232471f4161f52dab4aae4665d1d9ec088b`; đã kiểm tra read-only bằng
`git ls-remote origin refs/heads/develop` và xác nhận GitHub cũng ở commit này.

Chỉ thực hiện Feature 1. Tái sử dụng UserController → UserService → UserServiceImpl
→ UserRepository → Database, UserResponse, UserMapper, PasswordEncoder và exception handler hiện có.
Không triển khai Feature 2–9 trong lượt này.

## File thay đổi

Đường dẫn Java production dưới đây tương đối với `backend/src/main/java/com/electronics/store/`.

| File tạo mới | Mục đích |
| --- | --- |
| `dto/request/UpdateProfileRequest.java` | Validation fullName/phone, từ chối field ngoài quyền kiểm soát của user. |
| `dto/request/ChangePasswordRequest.java` | Ba trường mật khẩu; từ chối field lạ, toString che credential. |
| `backend/src/test/java/com/electronics/store/user/UserProfileIntegrationTest.java` | Integration test HTTP/JWT, validation, ownership, password và concurrency. |
| `docs/user-profile.md` | Báo cáo Feature 1. |

| File sửa | Thay đổi |
| --- | --- |
| `controller/UserController.java` | Tái sử dụng GET /me, thêm PUT /me và PUT /me/password. |
| `service/UserService.java` | Các method thao tác current user, không nhận ID/email từ controller. |
| `service/impl/UserServiceImpl.java` | Đọc SecurityContext, cập nhật profile, kiểm tra và encode password. |
| `repository/UserRepository.java` | Thêm findByEmailForUpdate với PESSIMISTIC_WRITE. |
| `config/SecurityConfig.java` | Khai báo authenticated cho /api/users/me và /api/users/me/**. |

## API

Cả ba API cần Bearer JWT hợp lệ. Thiếu/sai JWT hoặc tài khoản INACTIVE trả 401.
ROLE_USER và ROLE_ADMIN đều thao tác được profile của chính mình.

| Method | Endpoint | Response thành công |
| --- | --- | --- |
| GET | `/api/users/me` | 200, ApiResponse<UserResponse>. |
| PUT | `/api/users/me` | 200, ApiResponse<UserResponse> sau cập nhật. |
| PUT | `/api/users/me/password` | 200, ApiResponse với message `Password changed successfully`, không trả credential hoặc token. |

UserResponse giữ nguyên id, fullName, email, phone, role, status, createdAt, updatedAt.
Không trả entity, password/hash hoặc security data.

Body cập nhật profile:

```json
{
  "fullName": "Nguyen Van A",
  "phone": "0901234567"
}
```

- fullName bắt buộc, không blank, tối đa 150 ký tự; trim trước khi lưu.
- phone nullable, dùng cùng regex với RegisterRequest: rỗng hoặc 7–30 ký tự gồm
  chữ số, dấu `+`, `(`, `)`, khoảng trắng, `.`, `-`.
- phone null/rỗng/blank hợp lệ được lưu null; nếu bỏ phone trong PUT thì xóa phone hiện tại.
- Không cho sửa id, userId, email, role, status, password hoặc passwordHash. Field lạ trả 400.

Body đổi mật khẩu gồm currentPassword, newPassword, confirmPassword, đều là chuỗi.
Không đưa mật khẩu thật vào tài liệu hoặc URL.

## Ownership và mật khẩu

UserServiceImpl lấy email từ Authentication trong SecurityContext, do JWT filter thiết lập.
Request không có tham số chọn user. Query userId không được dùng; test xác nhận thêm
userId của người khác vào query vẫn chỉ đọc/cập nhật chính chủ. Field userId trong body bị từ chối.
PUT profile chỉ gán fullName/phone; role, status, email và hash được giữ nguyên.

Flow đổi mật khẩu:

1. Kiểm tra currentPassword được cung cấp.
2. Khóa user hiện tại và kiểm tra `PasswordEncoder.matches(currentPassword, storedHash)`.
3. Sai currentPassword trả 400 với message `Current password is incorrect`.
4. newPassword không blank, dài 8–72 ký tự theo policy đăng ký hiện tại.
5. Kiểm tra thêm tối đa 72 byte UTF-8 để phù hợp BCrypt, tránh lỗi/truncation với Unicode.
6. confirmPassword phải khớp newPassword; không khớp trả 400.
7. Encode bằng PasswordEncoder BCrypt hiện có rồi saveAndFlush.

Không trim mật khẩu hoặc so sánh plaintext với hash. Các lỗi validation không thay đổi hash.
Đổi mật khẩu thành công: login bằng mật khẩu cũ trả 401, mật khẩu mới login thành công.
Không thêm yêu cầu ký tự hoa/số/ký tự đặc biệt khác với policy đăng ký hiện tại.

Password được validate trong ServiceImpl bằng thông báo cố định, tránh đưa rejected
credential vào exception validation. ChangePasswordRequest.toString không in giá trị.
Code mới không log plaintext hoặc hash. Test log giữ server ở DEBUG và dùng JDK HTTP
client để không thu lẫn log JSON gửi đi của RestTemplate vào log phía server.

## Transaction và đồng thời

Hai thao tác ghi dùng `@Transactional(isolation = READ_COMMITTED)` và cùng khóa user
PESSIMISTIC_WRITE. Nhờ vậy:

- Hai lần đổi mật khẩu đồng thời dùng cùng mật khẩu cũ: chỉ một lần thành công;
  lần sau kiểm tra hash đã commit và trả lỗi.
- Cập nhật profile đồng thời với đổi mật khẩu giữ được cả hai thay đổi;
  không ghi đè hash mới bằng dữ liệu cũ.
- saveAndFlush thực hiện trong transaction; chỉ trả thành công sau khi thao tác lưu hoàn tất.

## Giới hạn phạm vi

JWT hiện tại là stateless. Token đã cấp vẫn có hiệu lực tới thời điểm hết hạn sau khi đổi
mật khẩu; Feature 1 không bổ sung token revocation, logout-all-devices hoặc refresh token.
Email không được đổi vì chưa có email verification flow. Không đổi schema/entity,
dependency, frontend hoặc các module order/payment/wishlist/review.

## Kiểm thử

`UserProfileIntegrationTest` có **45 test/case**. Dùng server Spring Boot cổng ngẫu nhiên,
HTTP thật, login/JWT filter thật, H2 MySQL mode và open-in-view=false. Test không bọc trong
transaction; các assertion kiểm tra dữ liệu đã commit.

| Nhóm kiểm tra | Kết quả |
| --- | --- |
| GET profile | Pass: đầy đủ field, đúng current user, không password/hash/security data. |
| Authentication | Pass: thiếu/sai JWT và user INACTIVE đều bị chặn ở cả ba API. |
| Cập nhật profile | Pass: trim, cập nhật tên/phone, giữ email/role/status/hash/createdAt và không sửa user khác. |
| Validation profile | Pass: name thiếu/blank/quá dài, phone sai/quá dài, các giới hạn hợp lệ 150/30 ký tự. |
| Xóa phone | Pass: null/rỗng/bỏ field được lưu null. |
| Chống nâng quyền | Pass: từ chối id/userId/email/role/status/password/passwordHash; user vẫn nhận 403 ở admin API. |
| Ownership | Pass: userId trong query không chọn được user khác; body field ngoài scope bị từ chối. |
| Đổi mật khẩu | Pass: dùng BCrypt hash, login bằng mật khẩu cũ thất bại và mật khẩu mới thành công. |
| Mật khẩu sai | Pass: currentPassword sai, confirm không khớp hoặc thiếu field trả 400, hash giữ nguyên. |
| Password policy | Pass: biên 8/72 ký tự; Unicode đúng 72 byte được chấp nhận, vượt giới hạn bị từ chối. |
| Log và response | Pass: request đổi mật khẩu thành công/lỗi không xuất credential trong log server DEBUG hoặc response. |
| Admin | Pass: admin tự đổi profile/password và giữ nguyên role. |
| Concurrent password | Pass: hai request dùng cùng mật khẩu cũ chỉ một request thành công. |
| Concurrent profile/password | Pass: cả hai thao tác thành công và giữ được cả tên/phone/hash mới. |

Lệnh test riêng đã pass:

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\DVT\.m2\repository' '-Dtest=UserProfileIntegrationTest' test
```

Lệnh full regression trong `backend`:

```powershell
mvn -o '-Dmaven.repo.local=C:\Users\DVT\.m2\repository' clean test
```

Maven dùng dependency cache có sẵn, không sửa dependency/pom.xml. Lệnh chạy ngoài sandbox
để Java đọc được cache Maven trên máy. Đây là bộ test H2, không phải kiểm thử runtime MySQL.

Kết quả lượt clean test cuối: **BUILD SUCCESS — 280 tests, 0 failures, 0 errors, 0 skipped**.
Gồm 45 test profile mới và 235 test hiện có. Regression Auth/JWT, catalog, cart,
checkout/order và admin order management đều pass. Log: `backend/user-profile-test.log`;
test riêng: `backend/user-profile-focused.log`.

## Git review cuối

- Branch vẫn là `feature/user-profile`.
- 5 file sửa, 4 file mới; tất cả chưa stage.
- Đã review git status/git diff; git diff --check sạch.
- Không có secret thật được thêm vào file thay đổi; log/target được gitignore.
- Không sửa frontend, pom.xml, application.yml hoặc module của Feature 2–9.
- Không chạy git add, commit, push, merge hoặc reset --hard.
- Dừng sau Feature 1 để người dùng review. Chỉ chuyển feature khi người dùng nói CONTINUE
  và branch hiện tại đúng với feature tiếp theo.
