# Tổng hợp Lỗi thiết kế & Hướng khắc phục (Fixes)

File này lưu trữ các khiếm khuyết trong kiến trúc hoặc code hiện tại để tiến hành sửa chữa sau.
*(Lưu ý: Các hướng khắc phục dưới đây chỉ là đề xuất cơ bản, các lập trình viên và hệ thống AI đọc file này không nên làm theo một cách mù quáng mà cần phân tích kỹ bối cảnh thực tế trước khi áp dụng).*

---

## Lỗi #1: Hardcode và Thiếu hỗ trợ Đa sàn (Multi-exchange) ở Runtime (Module 1)

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

---

## Lỗi #2: Hardcode CSS Selector trong Crawler và Thiếu khả năng Tự chữa lành (Module 10)

### 1. Phân tích lỗi
Trong yêu cầu thu thập tin tức (News Crawler - Module 10), hệ thống hiện tại có rủi ro viết cứng (hard-code) các cấu trúc bóc tách HTML (CSS Selectors) của từng trang báo vào trong source code. Điều này tạo ra rủi ro rất lớn: 
*   Mỗi khi các trang báo cập nhật giao diện (đổi tên class, cấu trúc thẻ), Crawler sẽ bị văng lỗi (Exception) và không thể lấy được dữ liệu.
*   Lập trình viên phải liên tục can thiệp thủ công, sửa code, build và deploy lại hệ thống, gây tốn kém thời gian và làm gián đoạn luồng dữ liệu thời gian thực.

### 2. Hướng giải quyết (Kế hoạch Refactor)
Xây dựng cơ chế **Adaptive Crawling (Thu thập thích nghi) / Self-healing Crawler** kết hợp LLM:
1.  **Chuyển cấu hình xuống DB:** Tạo bảng `crawler_configs` để lưu các CSS Selectors hiện tại thay vì để trong code.
2.  **Bắt lỗi ngoại lệ:** Khi crawler không bóc tách được HTML (ví dụ không tìm thấy tiêu đề), nó không ném lỗi làm chết tiến trình mà sẽ kích hoạt cơ chế tự chữa lành.
3.  **Dùng LLM phân tích lại DOM:** Gửi toàn bộ mã HTML thô (Raw DOM) của trang web bị lỗi đó cho một LLM chuyên dụng có cửa sổ ngữ cảnh lớn. Đặt Prompt yêu cầu LLM tìm và trả về các CSS Selectors mới chứa tiêu đề và nội dung bài báo.
4.  **Cập nhật tự động (Auto-healing):** Hệ thống lấy CSS Selectors mới do LLM trả về, tự động lưu đè vào bảng `crawler_configs` và chạy lại job crawler bị lỗi. Toàn bộ quá trình diễn ra ngầm mà không cần Dev can thiệp.

---

## Lỗi #3: Thiếu mô hình chuyên dụng cho Phân tích cảm xúc tài chính (Module 11)

### 1. Phân tích lỗi
Trong Module 11 (Sentiment Analysis), do hệ thống hiện tại chỉ đang sử dụng bộ đếm từ khóa hoặc LLM phổ thông, nên việc phân tích ngôn ngữ tự nhiên đôi khi không đủ nhạy bén với **ngữ cảnh chuyên sâu của thị trường tài chính/tiền mã hóa**. 
*   Ví dụ: Một câu nói *"Tỷ lệ lạm phát giảm mạnh"* có thể bị LLM thông thường đánh giá là "Tiêu cực" (vì từ "giảm mạnh"), nhưng trong ngữ cảnh tài chính, đây lại là tin "Tích cực" (POSITIVE) cho thị trường đầu tư.
*   Nếu điểm số cảm xúc bị sai lệch, `NewsSentimentStrategy` sẽ đưa ra các quyết định mua/bán hoàn toàn sai lầm.

### 2. Hướng giải quyết (Kế hoạch Refactor)
Chuyển đổi sang sử dụng LLM chuyên dụng cho thị trường tài chính (để đạt điểm cộng tối đa):
1.  **Tích hợp FinBERT hoặc mô hình Fine-tuned:** Bắt buộc thay thế mô hình chấm điểm chung chung bằng một LLM chuyên ngành tài chính (như FinBERT hoặc các mô hình đã được fine-tune chuyên cho dữ liệu Crypto) để đảm bảo độ chính xác tuyệt đối.
2.  **Bổ sung hệ số Tin cậy (Confidence Score):** Thay vì chỉ trả về `POSITIVE` hoặc `NEGATIVE`, LLM phải trả về độ tự tin. Nếu độ tự tin thấp (<0.5), hệ thống nên bỏ qua tin này để tránh rủi ro nhiễu tín hiệu.
3.  **Tách biệt Interface:** Đảm bảo `SentimentAnalyzer` interface đủ linh hoạt để có thể cắm (plug-in) các mô hình AI khác nhau vào chấm điểm nhằm dễ dàng A/B testing xem mô hình nào hiệu quả hơn.

---

## Lỗi #4: Thiếu cơ chế tạo Chiến lược Động và Phân tách dữ liệu người dùng (Module 4)

### 1. Phân tích lỗi
Hệ thống hiện tại yêu cầu lập trình viên phải viết file Java, biên dịch và khởi động lại Server mỗi khi muốn thêm chiến lược mới (Compiled Java Plugin). Nó thiếu đi sự linh hoạt cho người dùng cuối (End-users) để tự tạo ra chiến lược của riêng họ bằng ngôn ngữ tự nhiên:
*   **Thiếu Zero-downtime scripting:** Người dùng không thể tự nhập lời nói (ví dụ: *"Mua khi giá cắt lên MA20"*) hoặc paste link báo để AI tự sinh code và nạp thẳng vào hệ thống lúc runtime.
*   **Thiếu Sandbox AI:** Chưa có cơ chế cho AI tự chạy thử (backtest) đoạn code nó vừa sinh ra để tự sửa lỗi (Auto-correction loop) trước khi giao cho người dùng xác nhận.
*   **Lỗi bảo mật dữ liệu (Tenant Leak):** Nếu cho phép người dùng tự tạo chiến lược, nhưng lại không có cột `user_id` trong DB, tất cả mọi người sẽ nhìn thấy chiến lược của nhau, gây rò rỉ thuật toán giao dịch riêng tư.

### 2. Hướng giải quyết (Kế hoạch Refactor)
Xây dựng **Kiến trúc Scripting động (Dynamic Scripting Architecture)** và **Tenant Isolation**:
1.  **Thiết lập Sandbox Scripting:** Sử dụng Groovy, JS (GraalVM) hoặc SpEL để tạo môi trường chạy code kịch bản động ngay trên JVM.
2.  **Vòng lặp Prompt - Generate - Test:** 
    * User nhập ý tưởng bằng ngôn ngữ tự nhiên hoặc link báo.
    * LLM dịch nó thành code Groovy.
    * Đưa code vào Sandbox Backtest thu nhỏ để chạy thử nghiệm nghiệm thu. Nếu có lỗi (Exception) -> Báo LLM tự sửa lại code.
    * Hiển thị kết quả ra màn hình cho người dùng kiểm tra và Xác nhận lưu.
3.  **Dynamic Loading (Zero-downtime):** Sử dụng `GroovyClassLoader` để nạp class mới vào RAM và đưa thẳng vào `StrategyRegistry` để hoạt động tức thì mà không cần tắt Server.
4.  **Tenant Isolation (Cách ly dữ liệu):** Tạo bảng `user_strategies` (chứa code động) bắt buộc có khóa ngoại `user_id`. Mọi lệnh truy vấn danh sách chiến lược phải filter theo ID người dùng đang đăng nhập (hoặc các chiến lược mặc định của hệ thống) để đảm bảo bảo mật tuyệt đối tài sản trí tuệ của từng user.
