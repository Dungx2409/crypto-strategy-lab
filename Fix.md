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
Dù Backend (Core/API) đã xử lý hoàn chỉnh các luồng dữ liệu theo đúng yêu cầu đề bài, nhưng giao diện Frontend (file HTML và JS) hiện đang bỏ sót các tính năng quan trọng khiến người dùng không thể thao tác đầy đủ với quá trình Backtest & Leaderboard:
*   **Thiếu Thư viện Biểu đồ Chuyên nghiệp (TradingView):** Đề bài yêu cầu "Biểu đồ cần giống Trading View... nến cuối cùng phải liên tục nhảy lên xuống realtime" và "Bật MA, RSI". Frontend hiện tại không đáp ứng được nếu chỉ vẽ DOM thuần. Nó bắt buộc phải dùng thư viện như `lightweight-charts` và xử lý bản tin WebSocket để nháy cây nến cuối (tick) trực tiếp trên trình duyệt.
*   **Thiếu 2 ô nhập liệu cho vòng lặp AI (Stop Condition):** Giao diện chỉ có ô nhập số lượng ứng viên tối đa (Max candidates) nhưng lại thiếu ô nhập thời gian chạy tối đa (`maxDuration`) và số vòng lặp không cải thiện (`noImprovementIterations`), khiến vòng lặp Genetic Search có nguy cơ chạy vô hạn.
*   **Thiếu Tham số cấu hình Backtest thủ công:** Khi người dùng chọn chạy backtest một chiến lược, form giao diện đang thiếu ô nhập "Vốn ban đầu" (Initial Capital) và "Khung thời gian" (Date Range - VD: 1 năm qua).
*   **Thiếu tính năng Sắp xếp (Sort) trên Bảng Leaderboard:** Dữ liệu Leaderboard trả về được hiển thị cố định theo Điểm (Score). Tuy nhiên, người dùng không thể click vào các tiêu đề cột (Return, Win Rate, Max Drawdown) để sắp xếp lại (Sort client-side).
*   **Thiếu nút bấm "Chạy lại" (Rerun) trên UI:** Giao diện chi tiết của một Leaderboard Entry thiếu nút bấm "Rerun" để kích hoạt API tái lập thí nghiệm.

### 2. Hướng giải quyết (Kế hoạch Refactor)
Bổ sung code thuần túy trên Frontend (HTML/JS) mà không cần can thiệp Backend:
1.  **Tích hợp TradingView Lightweight Charts:** Thêm thư viện này vào HTML, hứng sự kiện WebSocket để cập nhật Realtime cây nến cuối cùng và bổ sung các nút bật/tắt đường chỉ báo (MA, RSI).
2.  **Thêm các ô Input Tham số:** Bổ sung thẻ `<input>` cho `maxDuration`, `noImprovementIterations` (cho AI Search) và `<input>` cho `capital`, `dateRange` (cho Backtest thủ công) vào file `index.html`.
3.  **Gắn Event Listener Sắp xếp Leaderboard:** Gắn sự kiện `onclick` vào các thẻ `<th>` của bảng Leaderboard. Khi click, dùng hàm `sort()` của JavaScript để đảo lại thứ tự mảng dữ liệu.
4.  **Thêm nút "Rerun this experiment":** Tại giao diện `Experiment details`, thêm nút "Rerun". Khi bấm, file JS gọi API `POST /api/v1/experiments/{experimentId}/rerun` để so sánh tính tái lập.

---

## Lỗi #6: Chưa kiểm soát đầy đủ thứ tự dữ liệu realtime (Module 1)

### 1. Phân tích lỗi

Hệ thống hiện tại dựa vào việc WebSocket của sàn thường truyền message theo thứ tự trên cùng một connection. Điều này thường đúng nhờ TCP, nhưng không đủ để bảo đảm thứ tự trong toàn bộ pipeline của ứng dụng. Dữ liệu có thể bị xử lý lệch thứ tự khi:

*   connection cũ gửi callback sau khi connection mới đã reconnect;
*   REST gap recovery lấy snapshot trong khi WebSocket vẫn gửi update;
*   client đang tải historical data nhưng nhận realtime update trước khi request REST hoàn tất;
*   một update cũ của cùng một candle đến sau update mới.

Trong code hiện tại:

*   `MarketDataStreamService` có `generation` để loại callback từ stream cũ, nhưng chưa có sequence/version cho từng update;
*   `CandleUpdate` chỉ chứa `Candle` và `closed`, chưa chứa event time hoặc sequence;
*   `market.js` nhận candle cùng `openTime` rồi ghi đè, nên một update cũ đến trễ có thể ghi đè update mới;
*   `JdbcCandleStore.saveIfAbsent()` chỉ chống insert duplicate của candle đã đóng, không giải quyết thứ tự các update đang mở;
*   khi REST history hoàn tất sau realtime update, `state.candles = body.candles` có thể ghi đè state mới hơn nếu không merge.

Vì vậy, kết luận chính xác là: hệ thống đã có stale-connection protection và duplicate protection, nhưng chưa đảm bảo tuyệt đối out-of-order message protection.

### 2. Hướng giải quyết (Kế hoạch Refactor)

Xây dựng cơ chế kiểm soát thứ tự theo từng stream và từng candle:

1.  **Bổ sung metadata cho update:** Mở rộng `CandleUpdate` bằng `eventTime`, `sequence` hoặc `updateVersion` nếu provider cung cấp. Binance có event time, nhưng nếu không có sequence cho kline update thì phải kết hợp event time với `openTime` và trạng thái đóng.
2.  **Lưu watermark theo stream:** Trong `MarketDataStreamService`, mỗi `StreamState` lưu `lastClosedOpenTime` và metadata update cuối cùng cho candle đang mở.
3.  **Từ chối update cũ:** Nếu update có version/event time cũ hơn update đã chấp nhận thì bỏ qua. Candle đã đóng có `openTime` nhỏ hơn watermark cũng không được publish như dữ liệu mới.
4.  **Merge REST và WebSocket:** Khi historical response về client, không gán đè mù lên `state.candles`. Cần merge theo `openTime` và giữ bản update mới hơn.
5.  **Giữ cơ chế recovery:** Khi không đủ metadata để xác định thứ tự, đánh dấu stream cần reconciliation và tải lại candle liên quan từ REST API. REST là nguồn xác minh, còn WebSocket tiếp tục cung cấp realtime.
6.  **Bổ sung test:** Test update cùng `openTime` đến ngược thứ tự, candle cũ đến sau candle mới, REST response đến sau WebSocket update, reconnect với listener cũ và recovery không tạo duplicate.

### 3. Các file cần rà soát

*   `core/src/main/java/com/cryptolab/marketdata/domain/CandleUpdate.java`
*   `core/src/main/java/com/cryptolab/marketdata/application/MarketDataStreamService.java`
*   `infrastructure/src/main/java/com/cryptolab/infrastructure/marketdata/adapter/binance/BinancePayloadMapper.java`
*   `api-app/src/main/resources/static/market.js`
*   `infrastructure/src/main/java/com/cryptolab/infrastructure/marketdata/adapter/persistence/JdbcCandleStore.java`
*   `core/src/test/java/com/cryptolab/marketdata/application/MarketDataStreamServiceTest.java`

### 4. Giới hạn cần ghi rõ

Sàn thường bảo đảm thứ tự message trên một WebSocket connection, nhưng ứng dụng vẫn phải kiểm soát thứ tự sau khi kết hợp nhiều nguồn và nhiều vòng đời connection. Không nên nói rằng sàn tự giải quyết toàn bộ ordering cho hệ thống.




---

## Lỗi #7: Zoom biểu đồ không giữ vị trí con trỏ (Module 2)

### 1. Phân tích lỗi

Chức năng zoom hiện tại mới thay đổi số lượng candle nhìn thấy quanh vùng nến mới nhất. Vì vậy khi người dùng đặt con trỏ ở một candle cũ rồi cuộn chuột, biểu đồ không lấy candle dưới con trỏ làm tâm zoom; vùng hiển thị bị kéo về phía cuối dữ liệu.

Lỗi này thuộc **Frontend/Module 2**, không phải lỗi của Binance hay WebSocket:

*   Backend vẫn có thể trả đúng lịch sử candle;
*   WebSocket vẫn có thể gửi đúng candle realtime;
*   vấn đề nằm ở cách `market.js` tính vùng candle visible sau thao tác wheel;
*   state hiện có `visibleCount` và `offset`, nhưng chưa quy đổi vị trí con trỏ thành index candle để giữ cố định candle đang được trỏ tới.

### 2. Hướng giải quyết (Kế hoạch Refactor)

Điều chỉnh zoom theo vị trí con trỏ trên từng canvas:

1.  **Xác định chart đang thao tác:** Gắn `wheel` listener cho từng canvas trong `chartStates`, không dùng một vùng zoom chung cho cả bốn chart.
2.  **Đổi tọa độ chuột thành candle index:** Dùng `offsetX`, vùng plot bên trong chart và số candle hiện tại để xác định candle đang nằm dưới con trỏ.
3.  **Thay đổi mức zoom:** Cuộn lên giảm `visibleCount`; cuộn xuống tăng `visibleCount`, trong giới hạn tối thiểu và tối đa.
4.  **Tính lại offset:** Sau khi đổi `visibleCount`, tính `offset` sao cho candle index dưới con trỏ vẫn nằm dưới cùng vị trí tương đối trên canvas.
5.  **Giữ ổn định khi tải realtime:** Nếu nến mới đến trong lúc đang zoom vào vùng cũ, không tự động kéo view về nến mới trừ khi người dùng đang ở chế độ follow latest.
6.  **Áp dụng độc lập cho 4 chart:** Mỗi chart giữ `visibleCount`, `offset` và viewport riêng; zoom chart này không làm thay đổi ba chart còn lại.
7.  **Bổ sung test UI:** Kiểm tra zoom tại candle đầu, giữa và cuối; kiểm tra zoom từng chart độc lập; kiểm tra tải candle mới không làm mất viewport đang chọn.

### 3. Các file cần rà soát

*   `api-app/src/main/resources/static/market.js`
*   `api-app/src/main/resources/static/index.html`
*   `api-app/src/test/java/com/cryptolab/api/ReferenceDashboardTest.java`
*   `api-app/src/test/java/com/cryptolab/api/marketdata/MarketDashboardIsolationTest.java`

### 4. Kết luận cần ghi rõ

Đây là lỗi về **viewport interaction** của Frontend: zoom chưa anchored theo con trỏ chuột. Nó không làm sai dữ liệu candle, nhưng làm người dùng không thể phóng to đúng vùng lịch sử cần quan sát. Cách sửa là zoom quanh candle index tương ứng với vị trí con trỏ, thay vì luôn zoom quanh candle mới nhất.

---

## Lỗi #8: Chưa phân định rõ các lớp hiển thị giữa realtime và backtest (Module 2)

### 1. Phân tích lỗi

Module 2 yêu cầu hệ thống có khả năng visualize nhiều loại thông tin, nhưng không có nghĩa một realtime chart phải đồng thời vẽ tất cả thông tin của backtest. Cần phân biệt hai ngữ cảnh:

*   **Realtime chart:** cần hiển thị Candlestick, Volume, MA và trạng thái/cập nhật thị trường hiện tại. Không cần vẽ Entry, Exit, Stop Loss hoặc Take Profit của một experiment chưa chạy.
*   **Backtest chart:** mới là nơi hiển thị Buy/Sell signal, Entry, Exit, Stop Loss, Take Profit và các indicator/zone được chọn cho kết quả experiment.
*   `drawMarketChart()` đã vẽ Candlestick, Volume và MA20; Bollinger, RSI, Support/Resistance được vẽ khi có `strategyTypes` phù hợp.
*   `renderBacktestResult()` truyền `strategyTypes`, `signals` và `trades`, nên backtest có signal và entry/exit marker.
*   `drawTradeMarker()` hiện chỉ vẽ entry/exit; mức Stop Loss/Take Profit chưa được vẽ thành overlay riêng.

Vì vậy lỗi đúng không phải là “realtime chart phải vẽ mọi thứ”. Lỗi là hệ thống chưa hoàn thiện việc phân định và chứng minh lớp hiển thị theo từng ngữ cảnh; riêng SL/TP overlay của backtest hiện còn thiếu.

### 2. Hướng giải quyết (Kế hoạch Refactor)

1.  **Tách context hiển thị:** Realtime chỉ render market state; backtest render artifacts của experiment.
2.  **Giữ overlay theo ngữ cảnh:** Không đưa Entry/SL/TP của experiment vào realtime chart nếu chưa có experiment tương ứng.
3.  **Bổ sung SL/TP overlay cho backtest:** Vẽ line/marker theo đúng thời gian vị thế và execution configuration.
4.  **Bổ sung test hiển thị:** Kiểm tra realtime candle/volume/MA, backtest signal/entry/exit/SL/TP và bốn chart giữ state độc lập.

### 3. Các file cần rà soát

*   `api-app/src/main/resources/static/market.js`
*   `api-app/src/main/resources/static/lab.js`
*   `api-app/src/main/resources/static/index.html`
*   `api-app/src/main/resources/static/styles.css`
*   `api-app/src/main/java/com/cryptolab/api/experiment/ExperimentDetailsResponse.java`
*   `core/src/main/java/com/cryptolab/experiment/domain/ExecutionConfig.java`
*   `core/src/main/java/com/cryptolab/experiment/domain/Trade.java`

### 4. Kết luận cần ghi rõ

Đây là lỗi về **visualization mapping** của Frontend Module 2, không phải lỗi lấy dữ liệu từ Binance. Realtime chart không cần vẽ các artifact của backtest; tuy nhiên backtest chart cần hiển thị đầy đủ những artifact mà đề yêu cầu, trong đó SL/TP hiện chưa có overlay riêng.

# câu hỏi vu vơ
Loại coin trên 4 biểu đồ là cùng loại hay khác loại?

zoom 4 chart có vấn đề, chỉ zoom được vào nến mới nhất chưa zoom theo trỏ chuột