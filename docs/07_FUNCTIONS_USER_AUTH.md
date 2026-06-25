# 🔐 Tài liệu các hàm tài khoản & bảo mật (User & Token Services)

Tài liệu này giải thích chi tiết hoạt động của các hàm liên quan đến quản lý người dùng, đăng ký, đăng nhập và bảo mật phân quyền sử dụng JWT trong hai file: [UserService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/UserService.java) và [TokenService.java](file:///D:/Ki7/SWP391/Index/Index/src/main/java/Parking/Service/TokenService.java).

---

## 🛠️ Phần I: Dịch vụ người dùng (UserService)

### 1. `register(UserRequest registerRequest)`
* **Chức năng:** Tạo tài khoản quản trị/nhân viên mới trong hệ thống.
* **Tại sao phải làm vậy?** Phục vụ tính năng thêm tài khoản nhân viên soát vé hoặc ban quản trị bãi xe.
* **Quy trình hoạt động:**
  1. Kiểm tra xem Email hoặc Số điện thoại đăng ký đã tồn tại trong hệ thống chưa. Nếu rồi, quăng lỗi để chặn đăng ký trùng lặp.
  2. Mã hóa mật khẩu của người dùng bằng thuật toán bảo mật của Spring Security (`BCryptPasswordEncoder`) trước khi lưu trữ (Không bao giờ lưu mật khẩu dạng thô/text phẳng).
  3. Lưu thông tin người dùng vào database và trả về DTO `UserResponse` (Không trả về trường mật khẩu bảo mật).
* **Đầu vào:** `UserRequest` (Email, Số điện thoại, Họ tên, Mật khẩu, Vai trò,...).
* **Đầu ra:** `UserResponse`.

---

### 2. `login(LoginRequest loginRequest)`
* **Chức năng:** Kiểm tra thông tin đăng nhập và cấp mã Token truy cập cho người dùng.
* **Quy trình hoạt động:**
  1. Sử dụng Email hoặc Số điện thoại để tìm kiếm tài khoản người dùng trong database.
  2. Dùng bộ mã hóa kiểm tra mật khẩu nhập vào có trùng khớp với mật khẩu đã mã hóa lưu trong database không.
  3. Nếu khớp, gọi `TokenService.generateToken` để sinh chuỗi JWT đại diện cho phiên làm việc.
  4. Trả về thông tin người dùng kèm theo token JWT.
* **Đầu vào:** `LoginRequest` (Tài khoản định danh, Mật khẩu).
* **Đầu ra:** DTO phản hồi chứa thông tin đăng nhập thành công và JWT Token.

---

### 3. `loadUserByUsername(String identifier)`
* **Chức năng:** Hàm bắt buộc khi triển khai giao diện `UserDetailsService` của Spring Security.
* **Tại sao cần?** Giúp cấu hình bảo mật Spring Security biết cách xác minh thông tin tài khoản khi có request gửi tới. Hệ thống cho phép người dùng đăng nhập bằng cả Email hoặc Số điện thoại (`identifier`).

---

### 4. Các hàm quản lý (CRUD) khác
* **`getAllUsers()` / `getUserById(Long userId)`:** Lấy danh sách hoặc chi tiết nhân viên phục vụ quản lý nhân sự.
* **`updateUser(...)`:** Cập nhật thông tin cơ bản của nhân viên (Họ tên, SĐT, Email).
* **`updatePassword(...)`:** Đổi mật khẩu của người dùng, xác minh mật khẩu cũ chính xác trước khi cho phép cập nhật mật khẩu mới.
* **`resetPassword(...)`:** Cho phép quản trị viên khôi phục mật khẩu mặc định cho nhân viên trong trường hợp quên mật khẩu.
* **`deleteUser(Long userId)`:** Xóa mềm hoặc xóa cứng tài khoản khỏi bãi xe khi nhân viên nghỉ việc.

---

## 🔑 Phần II: Dịch vụ mã khóa Token (TokenService)

Dịch vụ này chịu trách nhiệm cấp phát và giải mã **JSON Web Token (JWT)** để định danh người dùng trong các API đòi hỏi đăng nhập.

### 1. `getSecretKey()`
* **Chức năng:** Giải mã chuỗi khóa bí mật `SECRET_KEY` dạng Base64 và tạo đối tượng khóa HMAC dùng để ký số.
* **Tại sao cần?** Ký số giúp đảm bảo Token do chính hệ thống của chúng ta tạo ra và không ai có thể làm giả hoặc sửa đổi thông tin bên trong Token.

---

### 2. `generateToken(User user)`
* **Chức năng:** Sinh chuỗi Token JWT khi người dùng đăng nhập thành công.
* **Tại sao làm vậy?** Để Client lưu trữ lại (trong LocalStorage hoặc Cookie) và đính kèm vào Header của các Request tiếp theo.
* **Quy trình hoạt động:**
  * Gán Subject của Token bằng ID người dùng (`userId`).
  * Ghi nhận thời gian tạo (`issuedAt`) và đặt thời hạn hết hạn (`expiration`) là **10 tiếng**.
  * Sử dụng khóa HMAC ở bước trên để ký và mã hóa Token thành một chuỗi nén gọn.

---

### 3. `extractToken(String token)`
* **Chức năng:** Giải mã Token nhận được từ Client để lấy lại thông tin người dùng tương ứng.
* **Tại sao làm vậy?** Khi Client gọi các API bảo mật (ví dụ: Check-in, Check-out), họ gửi kèm Token. Backend cần giải mã Token để biết chính xác nhân viên nào đang thực hiện thao tác đó.
* **Quy trình hoạt động:** Lấy ID người dùng ra khỏi Subject của Token và truy vấn ngược lại Database để lấy ra thông tin thực thể `User`.
