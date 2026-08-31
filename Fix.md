# Tổng hợp Lỗi thiết kế & Hướng khắc phục (Fixes)

File này lưu trữ các khiếm khuyết trong kiến trúc hoặc code hiện tại để tiến hành sửa chữa sau.

---

## Lỗi #1: Hardcode và Thiếu hỗ trợ Đa sàn (Multi-exchange) ở Runtime

### 1. Phân tích lỗi
Trong yêu cầu thiết kế ban đầu (AD-24), hệ thống chỉ cho phép chọn 1 sàn giao dịch (Binance hoặc OKX) khi khởi động thông qua biến môi trường. Tuy nhiên, nếu muốn mở rộng để Server **chạy đồng thời nhiều sàn cùng lúc**, hệ thống hiện tại sẽ bị vỡ (crash) ở nhiều tầng:

*   **Tầng Database (Lỗi nghiêm trọng nhất):** Bảng `candles` hiện có khóa chính (Primary Key) là `(symbol, timeframe, open_time)`. Nếu Server nhận cùng lúc 1 cây nến BTCUSDT 5m lúc 10:00 từ Binance và 1 cây nến tương tự từ OKX, Database sẽ báo lỗi trùng khóa (Conflict) vì nó không phân biệt được nến của sàn nào.
*   **Tầng Repository:** Trong file `JdbcCandleStore.java` (dòng 24), biến `PROVIDER` đang bị hardcode cứng ngắc là `"BINANCE"` (`private static final String PROVIDER = "BINANCE";`). Nghĩa là dù chạy OKX thì DB vẫn lưu tên sàn là Binance.
*   **Tầng Service & API:** Các hàm trong `MarketDataService`, `CandleStore` và các đường dẫn WebSocket STOMP (`/topic/market/...`) hoàn toàn không có tham số nào để chỉ định tên sàn (provider).
*   **Tầng Dependency Injection:** Hệ thống đang dùng `@ConditionalOnProperty` để chỉ tạo đúng 1 Bean `MarketDataProvider`. Sẽ không thể tiêm (inject) cả 2 sàn cùng lúc theo cách hiện tại.

### 2. Hướng giải quyết (Kế hoạch Refactor)
Để sửa lỗi này và nâng cấp hệ thống lên Multi-exchange Runtime, ta cần làm các bước sau:

1.  **Sửa Database (Migration):**
    *   Tạo script Flyway (V20) để DROP Primary Key cũ của bảng `candles`.
    *   Thêm cột `provider` vào làm một phần của Khóa chính mới: `PRIMARY KEY (provider, symbol, timeframe, open_time)`.
2.  **Sửa tầng Repository:**
    *   Xóa biến hardcode `"BINANCE"` trong `JdbcCandleStore`.
    *   Cập nhật các câu lệnh SQL `INSERT` và `SELECT` để lọc và lưu theo tham số `provider` được truyền vào.
3.  **Sửa tầng Service (Áp dụng Registry Pattern):**
    *   Tạo một `MarketDataProviderRegistry` chứa một `Map<String, MarketDataProvider>` để quản lý tất cả các sàn được bật.
    *   Sửa các hàm của `MarketDataService` (và các service liên quan) để nhận thêm tham số `providerName`.
    *   Sửa logic đếm tham chiếu (Tracker) để cô lập state theo từng sàn.
4.  **Sửa API & WebSocket:**
    *   REST API `/api/v1/market/candles` cần thêm query param `?provider=...`.
    *   Đường dẫn WebSocket cần thêm tiền tố: `/topic/market/{provider}/{symbol}/{timeframe}`.
5.  **Cập nhật Test:**
    *   Sửa lại toàn bộ Unit Test và Integration Test để truyền đúng `provider` vào các mock/hàm.
