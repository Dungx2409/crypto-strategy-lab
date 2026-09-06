# REST API Endpoints

Tài liệu này liệt kê các REST API chính được cung cấp bởi `api-app`. Giao tiếp được thực hiện qua giao thức HTTP(S) sử dụng định dạng dữ liệu JSON.

## 1. Domain: Market Data
Cung cấp dữ liệu nến lịch sử (K-lines) cho biểu đồ Frontend.

- **GET `/api/v1/market-data/candles`**
  - **Mô tả:** Lấy danh sách nến đóng (closed candles).
  - **Query Params:** `symbol` (VD: BTCUSDT), `timeframe` (VD: 1m, 1h, 1d), `limit` (int, mặc định 1000).
  - **Response (200 OK):** Mảng đối tượng `Candle` (`[{"open": 50000, "close": 51000, "high": 52000, "low": 49000, "timestamp": 1718223...}, ...]`).

## 2. Domain: Strategy Plugin & Search
Cung cấp thông tin cấu hình cho giao diện động và tự động sinh chiến lược.

- **GET `/api/v1/strategies`**
  - **Mô tả:** Quét Registry để lấy danh sách toàn bộ các Strategy Plugin khả dụng (Ví dụ: MACD, RSI, Bollinger Bands).
  - **Response:** JSON Schema đặc tả các trường cần nhập liệu để Frontend tự động vẽ Form.

- **POST `/api/v1/search/run`**
  - **Mô tả:** Yêu cầu Backtester tự động tổ hợp (Random hoặc Genetic) các chiến lược với nhau.
  - **Payload:** Cấu hình search (Số lượng generation, kích thước quần thể).

## 3. Domain: Experiment (Backtest) & Leaderboard
Chấm điểm chiến lược.

- **POST `/api/v1/experiments`**
  - **Mô tả:** Đẩy (Queue) một Backtest Job vào RabbitMQ để Worker xử lý bất đồng bộ.
  - **Payload:** `{"strategyName": "MACD", "parameters": {"fastLength": 12, "slowLength": 26}, "timeframe": "1h"}`.
  - **Response (202 Accepted):** Trả về `experimentId` (UUID) để Frontend theo dõi trạng thái.

- **GET `/api/v1/experiments/{id}`**
  - **Mô tả:** Lấy kết quả P&L (Lợi nhuận, Win Rate, Drawdown, danh sách các giao dịch Trade) của một Experiment đã chạy xong.

- **GET `/api/v1/leaderboard`**
  - **Mô tả:** Lấy bảng xếp hạng các chiến lược tốt nhất (Sort theo P&L hoặc Sharpe Ratio). Trả kết quả cực nhanh nhờ Query Projection.

## 4. Domain: System Status
- **GET `/api/v1/status`**
  - **Mô tả:** Dùng để kiểm tra Healthcheck của toàn bộ hệ thống (PostgreSQL, RabbitMQ, Worker availability).
