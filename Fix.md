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
*   **Sai lệch Kiến trúc (V19 & AD-30):** Thiết kế lưu cấu hình `crawler_templates` theo từng `account_id` là phân mảnh vô lý vì cấu trúc trang web là tài nguyên dùng chung (Global). Việc bắt buộc phải có con người bấm nút "Xác nhận" (Promote) thì bộ thẻ HTML mới do AI sinh ra mới được áp dụng đã đi ngược lại tinh thần Tự động hóa 100% của Đề bài.

### 2. Hướng giải quyết (Kế hoạch Refactor)
Xây dựng cơ chế **Adaptive Crawling (Thu thập thích nghi) / Self-healing Crawler** kết hợp LLM (Tự động 100%):
1.  **Chuyển cấu hình xuống DB Dùng chung (Global):** Tạo bảng `crawler_configs` (hoặc xóa cột `account_id` khỏi `crawler_templates`) để lưu cấu hình cấp hệ thống thay vì để trong code hay lưu theo từng User.
2.  **Bắt lỗi ngoại lệ & Tiền xử lý DOM:** Khi crawler bị văng lỗi, không làm chết tiến trình mà sẽ gọi Headless Browser (Playwright) tải lại mã nguồn HTML. Hệ thống sẽ tự động rút gọn DOM (bỏ rác, script, thuộc tính thừa) để chống tràn bộ nhớ AI.
3.  **Dùng LLM phân tích lại DOM:** Gửi mã HTML đã rút gọn cho một LLM chuyên dụng có cửa sổ ngữ cảnh lớn. Đặt Prompt yêu cầu LLM tìm và trả về các CSS Selectors mới chứa tiêu đề và nội dung bài báo.
4.  **Cập nhật tự động (Zero-Touch Auto-healing):** Hệ thống nhận cấu hình Selector mới từ LLM, **tự động lưu đè vào DB và chạy tiếp công việc cào tin tức**. Lược bỏ hoàn toàn bước cần con người vào kiểm duyệt/xác nhận.

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
*   **Sai lệch Kiến trúc Tích hợp AI (AD-26):** Thiết kế cũ cấm AI sinh code thực thi, bắt AI chỉ trả về một file JSON tĩnh. Điều này vi phạm yêu cầu "phải chạy code tạo ra nó". Ngoài ra, nếu ném thẳng URL bài báo cho LLM, AI sẽ không lướt web được và sẽ "ảo giác" (bịa nội dung).

### 2. Hướng giải quyết (Kế hoạch Refactor)
Xây dựng **Kiến trúc Scripting động (Dynamic Scripting Architecture)**, **Tenant Isolation** và **Bộ định tuyến đầu vào**:
1.  **Bộ định tuyến Đầu vào (Input Router) & Web Fetcher Proxy:** 
    *   Nếu User nhập Text mô tả: Lấy thẳng đoạn Text đó làm Context gửi cho AI.
    *   Nếu User dán Link bài báo (URL): Backend kích hoạt thư viện lướt web (Playwright/Jsoup) tải HTML về, vứt bỏ quảng cáo, trích xuất Văn bản thuần (Plain text) và nhét phần Text đó vào Prompt thay cho Link khô khan.
2.  **Thiết lập Sandbox Scripting & Sinh Code Động:** Cởi trói cho LLM, yêu cầu dịch ý tưởng (hoặc nội dung bài báo) thành một đoạn code thực thi hoàn chỉnh (Groovy/JS). Sử dụng môi trường Sandbox ảo ngay trên JVM để chuẩn bị chạy thử.
3.  **Vòng lặp Generate - Test tự động:** Đưa code do AI sinh ra vào Sandbox để chạy test với 250 nến. Nếu bị Exception, hệ thống ném lỗi lại bắt AI tự sửa. Chạy ngầm lặp lại cho đến khi ra đoạn Code chuẩn xác không lỗi mới hiển thị ra màn hình cho User kiểm tra và xác nhận lưu.
4.  **Dynamic Loading (Zero-downtime):** Sử dụng `GroovyClassLoader` để nạp class mới vào RAM và đưa thẳng vào `StrategyRegistry` để hoạt động tức thì mà không cần tắt Server.
5.  **Tenant Isolation (Cách ly dữ liệu):** Tạo bảng `user_strategies` (chứa code động) bắt buộc có khóa ngoại `user_id`. Mọi truy vấn chiến lược phải filter theo ID người dùng đang đăng nhập để bảo vệ tuyệt đối tài sản trí tuệ (chiến lược) của từng user.

---

## Lỗi #5: Thiếu các tính năng điều khiển và phân tích trên giao diện Backtest (Frontend Module 6-8)

### 1. Phân tích lỗi
Dù Backend (Core/API) đã xử lý hoàn chỉnh các luồng dữ liệu theo đúng yêu cầu đề bài, nhưng giao diện Frontend (file HTML và JS) hiện đang bỏ sót 3 tính năng quan trọng khiến người dùng không thể thao tác đầy đủ với quá trình Backtest & Leaderboard:
*   **Thiếu 2 ô nhập liệu cho "Điều kiện dừng" (Stop Condition):** Giao diện chỉ có ô nhập số lượng ứng viên tối đa (Max candidates) nhưng lại thiếu ô nhập thời gian chạy tối đa (maxDuration) và số vòng lặp không cải thiện (noImprovementIterations). Điều này khiến vòng lặp có nguy cơ chạy không kiểm soát nếu người dùng muốn giới hạn theo thời gian.
*   **Thiếu tính năng Sắp xếp (Sort) trên Bảng Leaderboard:** Dữ liệu Leaderboard trả về được hiển thị cố định theo Điểm (Score). Tuy nhiên, người dùng không thể click vào các tiêu đề cột (Return, Win Rate, Max Drawdown) để sắp xếp lại (Sort client-side) nhằm so sánh các chiến lược theo tiêu chí an toàn hoặc lợi nhuận.
*   **Thiếu nút bấm "Chạy lại" (Rerun) trên UI:** Hệ thống Backend thiết kế theo chuẩn Immutable Provenance (thí nghiệm không thể ghi đè) và đã cung cấp API để Rerun (chạy lại cấu hình cũ trên dữ liệu mới). Tuy nhiên, trên giao diện chi tiết của một Leaderboard Entry lại không có nút bấm "Rerun" để kích hoạt API này.

### 2. Hướng giải quyết (Kế hoạch Refactor)
Bổ sung code thuần túy trên Frontend (HTML/JS) mà không cần can thiệp Backend:
1.  **Thêm các ô Input Điều kiện dừng:** Bổ sung thẻ `<input>` cho `maxDuration` và `noImprovementIterations` vào form Search (file `index.html`). Cập nhật file `lab.js` để đọc giá trị từ 2 ô này và truyền vào object `stopConditions` trong JSON request gửi lên API.
2.  **Gắn Event Listener Sắp xếp Leaderboard:** Gắn sự kiện `onclick` vào các thẻ `<th>` của bảng Leaderboard. Khi click, dùng hàm `sort()` của JavaScript để đảo lại thứ tự mảng dữ liệu `data.items` theo trường (field) tương ứng và gọi hàm render lại bảng.
3.  **Thêm nút "Rerun this experiment":** Tại giao diện `Experiment details` (sau khi người dùng click vào một dòng Leaderboard), thêm một nút "Rerun". Khi bấm nút này, file JS sẽ gọi API `POST /api/v1/experiments/{experimentId}/rerun` và hiển thị kết quả so sánh trực quan kiểm chứng tính Tái lập (Reproducibility).
