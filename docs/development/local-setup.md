# Hướng dẫn Khởi chạy Local (Local Development Setup)

Tài liệu này hướng dẫn các Lập trình viên mới cách cài đặt và chạy hệ thống Crypto Strategy Lab trên máy tính cá nhân.

## 1. Yêu cầu Hệ thống (Prerequisites)
Để chạy được mã nguồn của dự án này, máy bạn cần cài đặt sẵn:
- **Java Development Kit (JDK) 21** (Khuyên dùng Eclipse Temurin hoặc Amazon Corretto).
- **Maven** 3.8+ (Công cụ build code).
- **Docker Desktop** (Để chạy nhanh Database và Message Broker).
- Git.

## 2. Các bước Cài đặt (Installation Steps)

### Bước 1: Clone mã nguồn
```bash
git clone <đường-dẫn-repo>
cd crypto-strategy-lab
```

### Bước 2: Thiết lập Biến môi trường
Dự án không lưu cấu hình trực tiếp vào code mà dùng file `.env` (Chuẩn Twelve-Factor App).
- Hãy copy file `.env.example` thành file `.env` thực sự:
```bash
cp .env.example .env
```
- *(Lưu ý: Không bao giờ commit file `.env` lên Git).* Xem chi tiết cấu hình tại `environment-variables.md`.

### Bước 3: Dựng Hạ tầng bằng Docker Compose
Hệ thống cần PostgreSQL (Database) và RabbitMQ (Message Broker). Thay vì cài đặt thủ công, bạn chỉ cần dùng Docker:
```bash
docker-compose up -d postgres rabbitmq
```
- Lệnh này sẽ kéo Image từ Docker Hub về và chạy nền (`-d`).
- Chờ khoảng 10 giây để DB và RabbitMQ khởi động hoàn tất (Healthy).

### Bước 4: Chạy Ứng dụng Spring Boot
Dự án này là một Multi-module Maven project. Bạn cần mở 2 Terminal riêng biệt để chạy 2 tiến trình:

**Terminal 1 (Chạy Lõi API):**
```bash
mvn spring-boot:run -pl api-app
```
- API sẽ chạy ở cổng `http://localhost:8080`. Frontend sẽ gọi vào đây.

**Terminal 2 (Chạy Worker Backtest):**
```bash
mvn spring-boot:run -pl worker-app
```
- Worker không có cổng Web. Nó sẽ chạy ngầm, kết nối thẳng vào RabbitMQ để chờ nhận các Job (Nhiệm vụ Backtest) được đẩy từ API.

---

## 3. Cách Verify (Kiểm tra xem hệ thống đã chạy đúng chưa)

Sau khi cả 2 Terminal báo "Started...", hãy mở trình duyệt và truy cập:
`http://localhost:8080/api/v1/status`

Bạn sẽ nhận được một JSON phản hồi báo Status là `UP`. Giao diện Frontend (Dashboard) cũng sẽ được phục vụ tự động tại `http://localhost:8080`. Cấu hình thành công!
