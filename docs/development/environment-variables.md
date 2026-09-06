# Đặc tả Biến Môi trường (Environment Variables)

Hệ thống tuân thủ nguyên tắc **"Store config in the environment"** (Lưu cấu hình trong môi trường) của chuẩn ứng dụng hiện đại (Twelve-Factor App). Tất cả thông tin nhạy cảm (Mật khẩu DB, API Keys) tuyệt đối không được viết cứng (hardcode) trong mã nguồn.

Khi chạy Local, các biến này được định nghĩa trong file `.env`. Khi chạy trên Server, chúng được định nghĩa trong OS Environment hoặc Docker Config.

Dưới đây là danh sách các biến quan trọng nhất được định nghĩa trong `.env.example`:

## 1. Hạ tầng (Infrastructure)

| Tên biến | Giải thích | Giá trị mặc định |
| :--- | :--- | :--- |
| `POSTGRES_DB` | Tên CSDL PostgreSQL cho dự án | `crypto_strategy_lab` |
| `POSTGRES_USER` | Tên người dùng kết nối DB | `crypto_lab` |
| `POSTGRES_PASSWORD` | Mật khẩu truy cập DB | `change-me` |
| `DATABASE_URL` | Chuỗi kết nối JDBC đầy đủ cho Spring Boot | `jdbc:postgresql://localhost:55432/...` |
| `RABBITMQ_HOST` | Địa chỉ máy chủ Message Broker | `localhost` (Dùng tên service nếu chạy trong docker) |

## 2. API Của Bên Thứ Ba (3rd Party Providers)

### Market Data (Dữ liệu Giá)
Hệ thống linh hoạt trong việc chọn nguồn dữ liệu nến (Binance hoặc OKX).
| Tên biến | Giải thích | Giá trị mặc định |
| :--- | :--- | :--- |
| `CRYPTO_MARKET_PROVIDER` | Sàn giao dịch mặc định (`binance` hoặc `okx`) | `binance` |
| `CRYPTO_MARKET_SUPPORTED_SYMBOLS` | Các cặp tiền hỗ trợ (phân cách bằng dấu phẩy) | `BTCUSDT,ETHUSDT,SOLUSDT...` |
| `BINANCE_REST_URL` | Endpoint lấy nến lịch sử Binance | `https://api.binance.com/...` |
| `BINANCE_WEBSOCKET_URL` | Endpoint nến Realtime Binance | `wss://stream.binance.com:9443/ws` |

### Trí tuệ Nhân tạo (AI & Sentiment)
Phục vụ cho các chiến lược đánh theo Tin Tức (News).
| Tên biến | Giải thích | Giá trị mặc định |
| :--- | :--- | :--- |
| `GEMINI_API_KEY` | Khóa API để dùng AI Google Gemini sinh Code/DSL | *(Để trống, cần đăng ký)* |
| `HUGGINGFACE_API_KEY` | Khóa API để dùng mô hình FinBERT chấm điểm cảm xúc | *(Để trống, cần đăng ký)* |
| `HUGGINGFACE_URL` | Đường dẫn đến model dự đoán | `.../models/ProsusAI/finbert` |
| `NEWS_API_URL` | Endpoint lấy dữ liệu bài báo Crypto | `https://min-api.cryptocompare...` |

## 3. Thông số Hệ thống (System Tunables)

| Tên biến | Giải thích | Giá trị mặc định |
| :--- | :--- | :--- |
| `DEFAULT_ACCOUNT_ENABLED` | Có tự động tạo 1 tài khoản Demo khi khởi động Server không? | `true` |
| `CRYPTO_SEARCH_GENERATOR` | Thuật toán sinh chiến lược mặc định (`random` hoặc `genetic`) | `random` |
| `CRAWLER_CHECK_INTERVAL` | Tần suất bot News đi cào bài báo mới | `15m` (15 phút) |
