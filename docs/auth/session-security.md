# Bảo mật Xác thực (Authentication & Security)

Tài liệu này giải thích cơ chế xác thực và bảo mật phiên đăng nhập của người dùng trong hệ thống Crypto Strategy Lab. Mặc dù là đồ án môn học, hệ thống vẫn áp dụng các tiêu chuẩn bảo mật doanh nghiệp (Enterprise Security Standards).

## 1. Cơ chế Xác thực: Server-side Session & Cookie

### Tại sao không dùng JWT (JSON Web Token)?
Nhiều sinh viên thường lạm dụng JWT và ném token vào `localStorage` của trình duyệt. Điều này mở ra lỗ hổng bảo mật chết người là **XSS (Cross-Site Scripting)**, nơi các mã độc JavaScript có thể dễ dàng đánh cắp Token của người dùng. Hơn nữa, JWT rất khó để thu hồi (Revoke) ngay lập tức khi user muốn đăng xuất.

### Giải pháp của hệ thống (Session-based)
Hệ thống sử dụng **Spring Security** kết hợp với **Server-side Session**.
- Khi user đăng nhập thành công (`POST /api/v1/auth/login`), máy chủ sẽ tạo một Session ID lưu trong bộ nhớ (hoặc Redis nếu scale), và gửi Session ID đó về cho trình duyệt dưới dạng một **HTTP-Only Cookie**.
- Thuộc tính **`HttpOnly`** ngăn chặn mọi mã độc JavaScript truy cập vào Cookie.
- Thuộc tính **`SameSite=Strict`** ngăn chặn lỗ hổng **CSRF (Cross-Site Request Forgery)**, đảm bảo Cookie chỉ được gửi đi nếu request xuất phát từ đúng tên miền của Dashboard.

## 2. Lưu trữ Mật khẩu An toàn (Password Hashing)

Hệ thống TUYỆT ĐỐI KHÔNG lưu mật khẩu dạng rõ (Plain Text) trong database.
- Sử dụng thuật toán băm **BCrypt** (thuật toán tiêu chuẩn công nghiệp).
- Cost factor (Work factor) được đặt mặc định là **12**. Mức cost này đảm bảo đủ chậm để chống lại các cuộc tấn công Brute-force hoặc Rainbow Tables bằng GPU, nhưng vẫn đủ nhanh để không làm nghẽn CPU của Server khi người dùng đăng nhập.
- Mỗi mật khẩu sẽ tự sinh một "Muối" (Salt) ngẫu nhiên, giúp cùng một mật khẩu nhưng chuỗi Hash trong DB sẽ hoàn toàn khác nhau.

## 3. Phân quyền API (Authorization)

Các API được chia làm 2 nhóm rõ rệt:
- **Public API:** Bất kỳ ai cũng có thể gọi.
  - `/api/v1/status`: Health check.
  - `/api/v1/leaderboard`: Bảng xếp hạng chiến lược công khai.
  - Các đường dẫn tĩnh phục vụ SPA (Single Page Application).
- **Protected API:** Yêu cầu phải có Session Cookie hợp lệ.
  - `/api/v1/experiments`: Chạy backtest.
  - `/api/v1/strategies`: Liệt kê các chiến lược.
  - `/ws`: Kết nối STOMP cũng yêu cầu Cookie hợp lệ trong quá trình Handshake (Bắt tay ban đầu) để ngăn chặn các máy chủ lạ kết nối trái phép vào nguồn cấp dữ liệu Realtime.
