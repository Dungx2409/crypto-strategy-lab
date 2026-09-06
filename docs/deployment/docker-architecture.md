# Kiến trúc Triển khai Container (Docker Architecture)

Hệ thống Crypto Strategy Lab được đóng gói (Containerized) hoàn toàn bằng Docker, giúp quá trình triển khai từ Local (Máy cá nhân) lên Server Production (AWS, VPS) là hoàn toàn giống nhau, tránh lỗi "Works on my machine" (Chạy được trên máy tôi nhưng lỗi trên server).

Quá trình cấu hình toàn bộ hạ tầng được định nghĩa trong file `docker-compose.yml`. Dưới đây là phân tích kiến trúc của các Container.

## 1. Thành phần Hạ tầng (Infrastructure Containers)

1. **`postgres` (PostgreSQL 17.6)**: 
   - Container lưu trữ dữ liệu vĩnh viễn (Persistent Data) bằng cách mount volume `postgres-data:/var/lib/postgresql/data`.
   - Lưu ý: Trong môi trường Production thực tế, khuyến cáo sử dụng các dịch vụ Database được quản lý (Managed Database) như AWS RDS thay vì chạy PostgreSQL trong Docker để đảm bảo an toàn dữ liệu.

2. **`rabbitmq` (RabbitMQ 4.1 + Management Plugin)**:
   - Đóng vai trò là Message Broker (Người đưa thư). Nhận Job (Lệnh chạy Backtest) từ API và phân phối cho các Worker.
   - Có sẵn cổng `15672` để truy cập Giao diện Quản trị (Dashboard) của RabbitMQ.

## 2. Thành phần Ứng dụng (Application Containers)

Hệ thống không chạy như một cục Monolith duy nhất mà tách làm 2 Container riêng biệt, chung một Source code (Chỉ khác cờ build `APP_MODULE`):

1. **`api` (`api-app`)**:
   - Mở cổng `8080`. Chứa các Controller giao tiếp với Frontend.
   - Nhiệm vụ: Xử lý HTTP Request, Phân phối nến STOMP Realtime, tổ hợp sinh chiến lược (Search).
   - Khi có yêu cầu chạy Backtest, API sẽ **Không** chạy trực tiếp (vì quá nặng). Nó chỉ ghi 1 dòng sự kiện (Outbox Event) vào DB và ném thông điệp sang RabbitMQ.

2. **`worker` (`worker-app`)**:
   - **Không mở cổng Web nào cả**. Chạy ngầm hoàn toàn.
   - Nhiệm vụ: Lắng nghe RabbitMQ. Khi có thông điệp đến, Worker sẽ kéo mã nguồn/tham số chiến lược về, tải lịch sử nến từ PostgreSQL và bắt đầu chạy vòng lặp giả lập giao dịch khổng lồ (Backtester).
   - Khi chạy xong, Worker ghi kết quả P&L vào Database và xóa thông điệp khỏi RabbitMQ (ACK).

## 3. Lợi ích của Kiến trúc này
- **Cô lập lỗi (Fault Isolation):** Thuật toán Backtest của người dùng có thể là 1 vòng lặp vô hạn (Infinite Loop) hoặc tiêu thụ sạch RAM. Nếu điều kiện tồi tệ đó xảy ra, Container `worker` sẽ bị Crash (Chết) do hết RAM. **Tuy nhiên**, Container `api` vẫn sống khỏe! Trải nghiệm của hàng ngàn user đang xem biểu đồ Realtime không hề bị ảnh hưởng.
- **Auto-Recovery:** Nếu `worker` Crash khi đang xử lý dở Job A, Job A sẽ KHÔNG bị mất. RabbitMQ thấy kết nối đứt sẽ tự động ném Job A về lại Queue để giao cho một Worker khác khi nó khởi động lại.
