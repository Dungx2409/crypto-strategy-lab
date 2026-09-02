# Giải thích thuật ngữ & Kiến trúc (Dựa trên KienTruc_TiengViet.md)

## 1. Giải nghĩa các thuật ngữ cơ bản

*   **OKX là gì?**
    *   OKX là tên một sàn giao dịch tiền điện tử (tương tự như Binance). Trong hệ thống này, OKX và Binance đóng vai trò là "Nguồn cấp dữ liệu thị trường" (Market Data Provider). Hệ thống lấy dữ liệu giá (nến) thời gian thực từ các sàn này.
*   **Tracker Sub (Subscription Tracker) làm gì?**
    *   Nó giống như một "người gác cổng đếm số lượng người xem". Giả sử có 1000 người dùng cùng mở biểu đồ BTC/USDT khung 5 phút. Thay vì mở 1000 kết nối trực tiếp đến sàn Binance (sẽ bị sàn chặn vì DDoS), **Tracker** sẽ đếm: "Đang có 1000 người xem luồng này". Sau đó, nó chỉ duy trì **đúng 1 kết nối duy nhất** đến sàn Binance, lấy dữ liệu về và "nhân bản" (fanout) phát lại cho 1000 người dùng thông qua server của mình.
*   **Điểm kết nối (Reference-count) là sao?**
    *   Đây là thuật toán đếm số người xem của Tracker.
        *   Khi người dùng đầu tiên vào xem → đếm = 1 → Mở kết nối với sàn.
        *   Khi người thứ 2, 3... 1000 vào xem → đếm = 2, 3... 1000 → Vẫn dùng lại kết nối cũ.
        *   Khi có người tắt web → đếm trừ dần.
        *   Khi đếm = 0 (không còn ai xem) → Ngắt kết nối với sàn để tiết kiệm băng thông.
*   **Các Adapter có bao nhiêu loại? Sao không thấy liệt kê chi tiết trong file kiến trúc?**
    *   Adapter (Bộ chuyển đổi) là một mẫu thiết kế (Design Pattern) đóng vai trò làm "phiên dịch viên" giữa Core của ứng dụng và thế giới bên ngoài.
    *   Hệ thống có rất nhiều loại Adapter phân theo chức năng:
        1.  **Market Data Adapters:** Chuyên dịch dữ liệu sàn (Binance, OKX).
        2.  **Persistence Adapters:** Chuyên giao tiếp với Database (PostgreSQL).
        3.  **Messaging Adapters:** Chuyên giao tiếp với Hàng đợi (RabbitMQ).
        4.  **News Adapters:** Chuyên lấy tin tức từ web ngoài.
    *   Trong file `KienTruc_TiengViet.md`, chúng ta **không liệt kê mọi file Adapter** vì sẽ làm tài liệu biến thành danh sách source code khô khan. Tài liệu kiến trúc chỉ trích xuất các **Quyết định (ADRs)** quan trọng nhất. Ví dụ, quyết định AD-03 và AD-24 (trong Yêu cầu 1) giải thích lý do tồn tại của các Market Data Adapter: để cô lập API rắc rối của sàn, không cho Frontend biết.
*   **STOMP là gì?**
    *   STOMP (Simple Text Oriented Messaging Protocol) là một giao thức nhắn tin.
    *   Hãy tưởng tượng WebSocket là một "đường ống nước" thông thẳng từ Trình duyệt xuống Server. Nước chảy qua đó rất lộn xộn. STOMP là cách chúng ta đóng gói nước thành từng "bưu kiện" có dán nhãn địa chỉ rõ ràng (gọi là **Topic**).
    *   Ví dụ: Trình duyệt đăng ký nhận bưu kiện ở địa chỉ `/topic/market/BTCUSDT/5m`. Khi Server có cây nến 5 phút mới, nó gửi bưu kiện vào đúng địa chỉ đó. Trình duyệt nhận được và vẽ lên biểu đồ.

---

## 2. Giải nghĩa chi tiết Sơ đồ Yêu cầu 1 (Realtime Market Data)

Sơ đồ của Yêu cầu 1 mô tả hành trình của một "cây nến" (candlestick) từ sàn giao dịch đến lúc hiện lên màn hình người dùng. Dưới đây là diễn biến từng bước:

1.  **Sàn giao dịch (Exchange - Binance/OKX):**
    *   Sàn liên tục phun ra dữ liệu thô dạng JSON (giá đang nhảy liên tục từng giây). Định dạng của mỗi sàn lại khác nhau.
2.  **Bộ chuyển đổi (MarketAdapter):**
    *   Nhận mớ dữ liệu JSON hỗn độn đó.
    *   Lọc lấy 4 giá trị quan trọng: Mở (Open), Đóng (Close), Cao (High), Thấp (Low).
    *   Gói gém lại thành một đối tượng "chuẩn mực" của hệ thống gọi là `CandleUpdate`. Từ lúc này trở đi, hệ thống không cần biết gốc của nó là Binance hay OKX nữa.
3.  **Trạm trung chuyển (MarketDataStreamService):**
    *   Nhận đối tượng `CandleUpdate` và rẽ làm 2 nhánh:
    *   **Nhánh 1 (Lưu trữ Database):** Nó kiểm tra xem cây nến này đã "đóng" chưa (hết thời gian 5 phút chưa, cờ `closed = true`). Nếu đóng rồi, nó gửi vào **CandleStore** để lưu vĩnh viễn vào Database PostgreSQL. Nếu nến vẫn đang chạy (nhảy giá), nó KHÔNG lưu (để tránh rác DB).
    *   **Nhánh 2 (Phát sóng):** Dù nến đã đóng hay đang nhảy, nó đều quăng ra cho **STOMP Broker** để phát sóng.
4.  **Đài phát thanh (STOMP WebSocket Broker):**
    *   Phân loại nến mới theo từng kênh (Topic). Ví dụ kênh nến 5 phút, kênh nến 15 phút.
5.  **Trình duyệt người dùng (Browser):**
    *   Giao diện của user đang mở 4 biểu đồ. Mỗi biểu đồ giống như một cái radio nhỏ đang dò đúng đài STOMP tương ứng.
    *   Khi nhận được bản cập nhật nến mới, biểu đồ lấy mốc thời gian (`openTime`) ra so sánh:
        *   Nếu mốc thời gian trùng với cây nến đang vẽ → **Ghi đè (Upsert)** cây nến cũ. (Điều này tạo hiệu ứng nến đang nhúc nhích nhảy múa trên màn hình).
        *   Nếu mốc thời gian mới → Vẽ thêm một cây nến mới nằm bên phải.
6.  **Người gác cổng (Tracker):**
    *   Song song với quá trình trên, Tracker liên tục đếm số lượng "radio" (trình duyệt) đang nghe từng đài, để báo cho MarketAdapter biết nên duy trì hay ngắt kết nối với Binance (như đã giải thích ở mục 1).

---

## 3. Giải thích AD-05: Mỗi biểu đồ tự quản lý đăng ký và trạng thái riêng

Để hiểu rõ quyết định này, hãy đặt vào bối cảnh thực tế khi bạn đang xem màn hình Trading.

**Vấn đề (Nỗi đau của Frontend):**
Bạn đang mở 4 biểu đồ trên cùng 1 màn hình.
- Biểu đồ 1: nến 1 phút
- Biểu đồ 2: nến 5 phút
- Biểu đồ 3: nến 15 phút
- Biểu đồ 4: nến 1 giờ
Bây giờ, bạn muốn đổi Biểu đồ 1 sang nến 4 giờ. Phản xạ tự nhiên của một lập trình viên mới vào nghề là: *"Gửi request báo cho server biết tôi đổi thành 4 giờ, rồi reload (tải lại) trang web để server trả về giao diện mới"*. 
Nhưng nếu làm vậy, 3 biểu đồ còn lại cũng bị tải lại theo, gây giật lag, đứt quãng trải nghiệm và cực kỳ thiếu chuyên nghiệp (không đúng chuẩn Realtime).

**Giải pháp (Quyết định AD-05):**
Hệ thống sử dụng cơ chế **STOMP Logical Subscriptions (Kênh logic)** chạy trên **1 kết nối WebSocket vật lý duy nhất**. 
Thay vì reload trang, Trình duyệt web của bạn chia làm 4 bộ não nhỏ (4 Chart States) hoàn toàn độc lập:

1. **Kết nối vật lý duy nhất:** Trình duyệt chỉ nối **đúng 1 đường ống WebSocket** về Server Backend. Điều này giúp máy chủ của bạn dù có 1000 user cũng chỉ phải chịu 1000 kết nối WebSocket, thay vì 4000.
2. **Kênh logic (Logical Subscription):** Bên trong 1 đường ống đó, STOMP cho phép tạo ra các kênh nhỏ có `subscription ID` riêng biệt. 
    - Biểu đồ 1 nói: *"Ê đường ống, tạo cho tôi kênh `sub-01`, đăng ký nghe chủ đề `BTCUSDT-1m`"*
    - Biểu đồ 2 nói: *"Tạo cho tôi kênh `sub-02`, nghe `BTCUSDT-5m`"*
3. **Độc lập tuyệt đối:** Mỗi biểu đồ chỉ lắng nghe dữ liệu chạy về qua cái kênh của riêng nó, và tự vẽ lên cái Canvas (khung hình) của riêng nó.
4. **Khi bạn đổi Biểu đồ 1 sang 4 giờ:** 
    - Biểu đồ 1 tự động gửi lệnh: *"Đường ống ơi, hủy kênh `sub-01` cũ đi. Tạo kênh `sub-01-new` đăng ký nghe `BTCUSDT-4h` nhé!"*.
    - Trong khi Biểu đồ 1 đang thực hiện việc hủy kênh cũ và lấy dữ liệu 4h về để vẽ lại, thì **Biểu đồ 2, 3, 4 hoàn toàn không hề hay biết**. Đường ống chính không hề đứt. Dữ liệu của 3 biểu đồ kia vẫn chảy về qua các kênh `sub-02`, `sub-03` bình thường. Không có bất kỳ sự giật lag hay reload trang nào xảy ra!

**Hệ quả mang lại:**
- **UI mượt mà:** Đáp ứng chuẩn trải nghiệm của các sàn giao dịch lớn.
- **Tiết kiệm tài nguyên:** 1 đường ống WebSocket gánh được vô số kênh logic.
- **Nhất quán:** Nhờ việc định nghĩa sẵn một danh sách các Khung thời gian hợp lệ (Enum `Timeframe` chứa 1m, 5m, 1h, 1d...), từ thằng vẽ giao diện (Frontend), thằng lưu DB (PostgreSQL), thằng nhận lệnh (REST Controller), đến thằng móc nối sàn (Binance Adapter) đều nói chung một ngôn ngữ. Không sợ tình trạng Frontend đòi xem nến "2 phút" mà Database không biết nến "2 phút" là cái gì.

---

## 4. Giải thích AD-21: Danh sách các cặp giao dịch được kiểm soát bằng Allow-list

**Vấn đề (Bối cảnh):**
Trong file yêu cầu Đồ án cuối kỳ, mục 1 có ghi: *"Thị trường cryptocurrency như Bitcoin, Ethereum..."*. Điều này có nghĩa là hệ thống phải hỗ trợ phân tích nhiều loại coin khác nhau (Multi-coin), chứ không chỉ mỗi BTC.
Tuy nhiên, sàn Binance có tới hàng ngàn cặp giao dịch (coin rác, memecoin...). Nếu chúng ta cho phép người dùng nhập tự do bất kỳ cặp nào (ví dụ: `PEPEUSDT`) trên thanh tìm kiếm, hệ thống sẽ:
1. Mù quáng mở kết nối WebSocket xuống Binance để lấy dữ liệu PEPE.
2. Lưu rác vào Database.
3. Nếu người dùng cố tình spam các mã coin không tồn tại (`ABCDXYZ`), server sẽ liên tục gửi request hỏng đến Binance, dẫn đến nguy cơ bị sàn khóa IP (Rate limit / Banned).

**Giải pháp (Quyết định AD-21):**
Hệ thống sử dụng cơ chế **Allow-list (Danh sách cho phép)** được định nghĩa ngay ở tầng ứng dụng cốt lõi (Core Domain), thông qua biến môi trường `CRYPTO_MARKET_SUPPORTED_SYMBOLS=BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT`.

Khi có một request từ Frontend đòi xem nến của `PEPEUSDT`, tầng Application Service (cụ thể là hàm `validatedPair`) sẽ kiểm tra: *"PEPEUSDT có nằm trong danh sách cho phép không?"*. Vì không có, nó ngay lập tức quăng lỗi (Exception) từ chối phục vụ, **chặn đứng request trước khi nó kịp chạm đến Binance Adapter hay Database**.

**Tính hợp lý so với yêu cầu đề bài & Khả năng mở rộng:**
Thiết kế này **Cực kỳ hợp lý và ghi điểm tuyệt đối về khả năng mở rộng**:
- **Đáp ứng yêu cầu:** Hỗ trợ nhiều loại coin (Bitcoin, Ethereum...) đúng như đề bài yêu cầu.
- **Mở rộng 0-code (Zero-code extensibility):** Theo yêu cầu số 12 của đề bài (*"Thiết kế hệ thống sao cho có thể mở rộng trong tương lai mà không phải sửa đổi toàn bộ hệ thống"*). Nếu ngày mai công ty muốn hỗ trợ thêm đồng DOGE, DevOps chỉ cần vào file cấu hình (hoặc Docker Compose), sửa biến môi trường thành `...,BNBUSDT,DOGEUSDT` rồi khởi động lại ứng dụng.
  - KHÔNG cần sửa một dòng code Java nào.
  - Giao diện (Frontend) tự động gọi API lấy danh sách này để render ra thanh Dropdown cho người dùng chọn. Rất linh hoạt và an toàn!

---

## 5. Giải thích AD-31: Tách biệt kết nối Trình duyệt và Sàn (Fan-out Architecture)

Quyết định này là trái tim của hệ thống Realtime, giúp Server không bị sập và không bị Binance khóa IP (banned) khi có hàng ngàn người dùng truy cập.

**Bài toán (Nguy cơ sập Server):**
Giả sử có **1000 người dùng** truy cập vào trang web. Mỗi người mở giao diện gồm 4 biểu đồ (1m, 5m, 1h, 4h). 
Nếu làm theo cách "ngây thơ" thông thường: Cứ 1 biểu đồ mở ra, Server lại tạo 1 kết nối WebSocket thẳng tới Binance để xin dữ liệu. 
👉 1000 người x 4 biểu đồ = **4000 kết nối WebSocket trực tiếp tới Binance**.
Hậu quả: Binance giới hạn số lượng kết nối từ 1 IP. Server của bạn sẽ bị Binance block ngay lập tức vì tưởng là tấn công DDoS!

**Giải pháp (Cơ chế Fan-out / Nhân bản):**
Hệ thống áp dụng kiến trúc "Bộ nhân bản" (Fan-out) kết hợp với "Trình theo dõi" (Tracker - đã giải thích ở mục 1).
1. **Gộp luồng (Multiplexing):** Dù có 1000 người đòi xem nến 1m, Server chỉ phái ĐÚNG 1 ĐẠI DIỆN mở 1 kết nối duy nhất tới Binance để lấy nến 1m. Tương tự cho 5m, 1h, 4h. Vậy tổng cộng Server chỉ mở **4 đường truyền Internet** ra ngoài tới sàn Binance. Tuyệt đối an toàn!
2. **Nhân bản (Fan-out):** Khi Binance gửi 1 bản cập nhật nến 1m mới về Server. Server lập tức dùng "loa phát thanh" (STOMP Broker) nhân bản tin nhắn đó ra làm 1000 bản copy, và đẩy vào 1000 đường ống WebSocket cục bộ đang nối với 1000 trình duyệt của người dùng.

**Giải mã con số "59,583 tin nhắn" trong bài test Load Proof:**
Bạn thắc mắc con số này từ đâu ra? Nó chính là minh chứng thép cho sức mạnh của cơ chế Fan-out trong bài test chịu tải thực tế (`RealtimeFanoutCapacityTest`).
- Hãy làm một phép tính: Trong bài test, giả sử Binance gửi về 15 lần chớp giá (cập nhật nến) cho mỗi khung giờ. Với 4 khung giờ, Server nhận khoảng 60 tin nhắn từ Binance.
- Nhưng vì có 1000 người dùng đang háo hức chờ, Server phải tự động nhân bản 60 tin nhắn này lên: `60 tin x 1000 người = 60,000 tin nhắn gửi đi`.
- Con số **59,583** chính là số lượng tin nhắn thực tế mà 1000 cái trình duyệt "giả lập" đã nhận được thành công trước khi bài test kết thúc. (Nó hao hụt một chút so với 60,000 là do độ trễ mạng thực tế, một số client kết nối chậm vài phần ngàn giây nên lỡ mất tin nhắn đầu tiên).

**Kết luận:** Con số 59,583 chứng minh rằng: **Server có khả năng "bơm" gần 60.000 tin nhắn đến hàng ngàn người dùng mượt mà, trong khi bản thân nó chỉ phải "xin" sàn Binance vỏn vẹn vài chục tin nhắn qua 4 kết nối.** Đây là minh chứng kiến trúc đạt chuẩn doanh nghiệp (Enterprise-grade) mà các thầy cô rất thích nghe!

---

## 6. Giải thích AD-06: Sử dụng hợp đồng Registry và Factory cho Strategy Plugin

**Vấn đề (Cách làm cũ - Anti-pattern):**
Giả sử hệ thống của bạn có 3 chiến lược: Đường trung bình (MA), RSI và MACD. Khi người dùng muốn chạy một chiến lược, cách code thông thường của sinh viên là tạo một class `StrategyEngine` khổng lồ, bên trong dùng một đống lệnh `if-else` hoặc `switch-case`:
```java
if (type.equals("MA")) { 
    return new MovingAverageStrategy(fastPeriod, slowPeriod); 
} else if (type.equals("RSI")) { 
    return new RsiStrategy(period); 
} ...
```
**Hậu quả:** 
1. **Vi phạm nguyên lý OCP (Open-Closed Principle):** Mỗi khi muốn thêm 1 chiến lược mới (ví dụ SuperTrend), bạn **bắt buộc** phải mở file `StrategyEngine` ra, thêm 1 nhánh `else if` nữa. Lâu ngày file này sẽ phình to ra hàng ngàn dòng, cực kỳ dễ sinh bug làm sập luôn các chiến lược cũ đang chạy tốt.
2. Tham số của mỗi chiến lược lại khác nhau (MA cần 2 tham số, RSI cần 1 tham số). Giao diện frontend sẽ phải code cứng các ô nhập liệu cho từng thuật toán, rất mất công.

**Giải pháp (Kiến trúc Plugin với Registry & Factory):**
Hệ thống sử dụng kết hợp 2 Mẫu thiết kế (Design Pattern): **Factory Method** và **Registry Pattern** cùng với cơ chế Dependency Injection của Spring.

1. **Factory đóng gói Schema:** Thay vì viết code tạo chiến lược ở Core, mỗi chiến lược tự có một nhà máy (Factory) đi kèm. Ví dụ class `MacdStrategy` sẽ đi kèm `MacdStrategyFactory`. Factory này không chỉ biết cách "đẻ" ra chiến lược MACD, mà nó còn khai báo "Sơ yếu lý lịch" (Schema) của chiến lược đó ra bên ngoài: *"Tôi tên là MACD, version 1.0, tôi cần 3 tham số kiểu số nguyên là fastPeriod, slowPeriod và signalPeriod"*.
2. **Khám phá tự động (Auto-discovery):** Lúc server bật lên (Startup), đối tượng `SpringStrategyRegistry` sẽ chạy quanh hệ thống, thu gom tất cả các class nào có dán mác là `StrategyFactory` và lưu vào bộ nhớ (Registry) dưới dạng một cuốn danh bạ (Map). `Engine` lõi tuyệt đối không hề biết trước có những chiến lược nào.

**Cơ chế hoạt động cực kỳ linh hoạt:**
- Khi **Frontend** cần vẽ giao diện, nó gọi API hỏi Registry: *"Cho tôi danh sách các chiến lược hiện có"*. Registry trả về mảng JSON chứa MACD, MA, RSI kèm theo Schema tham số. Frontend tự động sinh (auto-generate) ra các ô input nhập liệu tương ứng trên web. Không hề code cứng giao diện!
- Khi **User bấm nút Chạy**, Frontend gửi xuống tên chiến lược và mảng tham số. Core Engine mở danh bạ (Registry) ra, tìm đúng Factory của MACD, quăng tham số cho Factory đó. Lúc này chính `MacdStrategy` sẽ tự kiểm tra tính hợp lệ của tham số (như `fastPeriod` phải lớn hơn 0).
- **Mở rộng Zero-Code (Plug & Play):** Điểm ăn tiền nhất của kiến trúc này là khả năng mở rộng. Nếu ngày mai sếp yêu cầu code thêm chiến lược Bollinger Bands. Bạn chỉ cần viết đúng 2 class `BollingerBandsStrategy` và `BollingerBandsFactory` rồi quăng vào thư mục source code. Lúc bật server lên, Registry tự động quét thấy, tự động bắn API ra Frontend cho vẽ giao diện, Engine tự động biết cách gọi. **Hoàn toàn KHÔNG phải sửa DB, KHÔNG sửa Frontend, KHÔNG đụng một dòng code nào vào Core Engine (không có bất kỳ câu if-else nào được thêm vào)**. Kiến trúc này được gọi là Hệ thống Plugin (Plugin Architecture).

**Phân tích sâu: Tách biệt Tham số dùng chung (Context) và Tham số thuật toán (Strategy Schema)**
Trong file đề bài có nhắc đến việc khi chạy chiến lược, người dùng phải chọn: *Pair/coin, Khoảng thời gian (như 1 năm), Vốn, vân vân...* 
Nhiều sinh viên thường gộp sai lầm tất cả những thứ này vào chung class Strategy (ví dụ: `MacdStrategy` lại chứa biến `pair="BTCUSDT"`, `capital=1000$`). Thiết kế của chúng ta tách biệt hoàn toàn ranh giới này:

1. **Tham số Ngữ cảnh (Context/Experiment Parameters):** Gồm Pair (BTCUSDT), Khung thời gian (5m), Phạm vi dữ liệu (1 năm), Số vốn ban đầu ($10.000). 
   - Những tham số này là **dùng chung cho mọi chiến lược**. 
   - Nó không thuộc về Plugin MACD hay RSI, mà nó thuộc về `Experiment` (Thí nghiệm) hoặc `BacktestEngine`. 
   - Khi chạy, Engine sẽ đóng gói những thông tin chung này (cùng với danh sách các cây nến) vào một đối tượng gọi là `StrategyContext`. 
   - Lúc gọi hàm `analyze(StrategyContext context)`, Plugin chỉ việc đọc dữ liệu nến từ Context ra tính toán. Nhờ vậy, Plugin hoàn toàn "mù" (không bị phụ thuộc cứng) vào loại coin hay số vốn, giúp 1 plugin có thể chạy trên mọi loại cặp coin khác nhau.

2. **Tham số Thuật toán (Strategy-specific Parameters):** Gồm `fastPeriod`, `slowPeriod` của MACD, hoặc `buyThreshold` của RSI.
   - Những tham số này là **riêng biệt của từng chiến lược**.
   - Nó được định nghĩa trong Factory của chính chiến lược đó.
   - **Ai nhập tham số này?** 
     - Trực tiếp: Người dùng nhập bằng tay trên Frontend (Frontend tự động vẽ ô nhập dựa vào Schema từ Registry).
     - Tự động: Trong Module 6 (Strategy Search Engine), thuật toán Genetic Algorithm hoặc Random Search sẽ tự động sinh (generate) ra hàng ngàn bộ tham số khác nhau dựa vào giới hạn (min/max) mà Factory khai báo, từ đó tìm ra cấu hình MACD hoặc RSI tốt nhất.

**Danh sách các Chiến lược đã được xây dựng và Bản chất Kỹ thuật (Technical Nature) của chúng:**
Dưới đây là 6 Plugin chiến lược đã được lập trình sẵn. Bản chất của phân tích kỹ thuật là dùng các công thức toán học/thống kê xử lý chuỗi giá trong quá khứ. Vì công thức toán học của mỗi chiến lược là hoàn toàn khác nhau, nên **chúng bắt buộc phải có các tham số đầu vào (Schema) hoàn toàn khác nhau**.

1. **Moving Average (Hai đường trung bình cắt nhau - `MOVING_AVERAGE`)**
   - **Bản chất:** Tính trung bình cộng của giá đóng cửa trong `N` cây nến gần nhất để làm mượt biểu đồ (lọc nhiễu). Chiến lược này dùng 2 đường MA: đường nhanh (ôm sát giá hiện tại) và đường chậm (thể hiện xu hướng dài hạn). Giao cắt của 2 đường báo hiệu đảo chiều xu hướng.
   - **Tham số kỹ thuật:**
     - `fastPeriod` (Ví dụ: 10): Chu kỳ tính MA nhanh. Nghĩa là cộng giá đóng cửa của 10 nến gần nhất rồi chia 10.
     - `slowPeriod` (Ví dụ: 20): Chu kỳ tính MA chậm (cộng 20 nến rồi chia 20).
   - **Logic Mua/Bán:** Nếu giá trị MA nhanh > MA chậm (Cắt lên - Crossover) 👉 BUY. Nếu cắt xuống 👉 SELL.

2. **MACD (Đường trung bình động hội tụ phân kỳ - `MACD`)**
   - **Bản chất:** Thay vì dùng trung bình cộng đơn giản, MACD dùng Exponential Moving Average (EMA - tính trung bình nhưng dồn trọng số vào các nến gần nhất). Nó đo lường động lượng (momentum) của xu hướng.
   - **Tham số kỹ thuật:**
     - `fastPeriod` (Mặc định: 12): Dùng để tính EMA(12).
     - `slowPeriod` (Mặc định: 26): Dùng để tính EMA(26). Đường MACD chính = EMA(12) - EMA(26).
     - `signalPeriod` (Mặc định: 9): Dùng để tính đường Signal = EMA(9) của chính đường MACD.
   - **Logic Mua/Bán:** Mua khi đường MACD chính cắt lên đường Signal (động lượng tăng). Bán khi cắt xuống.

3. **RSI (Chỉ số sức mạnh tương đối - `RSI`)**
   - **Bản chất:** Đo lường tốc độ và sự thay đổi của biến động giá để xác định thị trường đang bị mua quá mức (Overbought) hay bán quá mức (Oversold). Công thức tính toán dựa trên mức tăng/giảm trung bình (Average Gain/Loss) trong chu kỳ N nến, chuẩn hóa về thang điểm từ 0 đến 100.
   - **Công thức tính toán (Toán học):**
     - **Bước 1:** Tính `Average Gain` (Trung bình cộng mức giá tăng) và `Average Loss` (Trung bình cộng mức giá giảm) trong `period` nến quá khứ.
     - **Bước 2:** Tính Sức mạnh tương đối (Relative Strength): `RS = Average Gain / Average Loss`. (Nếu đà tăng mạnh hơn đà giảm thì RS > 1).
     - **Bước 3:** Chuẩn hóa về thang 0-100: `RSI = 100 - [ 100 / (1 + RS) ]`. Công thức này giúp điểm số luôn nằm gọn trong khung 0-100 bất kể thị trường biến động mạnh đến đâu.
   - **Tham số kỹ thuật:**
     - `period` (Mặc định: 14): Chu kỳ (số nến) để tính Average Gain/Loss.
     - `oversold` (Mặc định: 30): Ngưỡng 0-100. Nếu RSI < 30 tức là lực bán đã kiệt sức.
     - `overbought` (Mặc định: 70): Ngưỡng 0-100. Nếu RSI > 70 tức là lực mua đã bị đẩy lên quá đà.
   - **Logic Mua/Bán:** Mua khi RSI cắt lên từ dưới mức `oversold` (Bắt đáy). Bán khi RSI cắt xuống từ mức `overbought` (Chốt lời ở đỉnh).

4. **Bollinger Bands (Dải Bollinger - `BOLLINGER_BANDS`)**
   - **Bản chất:** Sử dụng khái niệm **Độ lệch chuẩn (Standard Deviation)** trong thống kê. Giá của một tài sản thường dao động xung quanh giá trị trung bình của nó (phân phối chuẩn). Dải Bollinger tạo ra 2 "bức tường" bao quanh giá.
   - **Tham số kỹ thuật:**
     - `window` (Mặc định: 20): Chu kỳ để tính đường trung bình ở giữa (SMA 20) và tính Độ lệch chuẩn (Sigma).
     - `deviationMultiplier` (Mặc định: 2): Nhân hệ số này với Độ lệch chuẩn. (Theo quy tắc 3-Sigma của xác suất thống kê, 95.4% thời gian giá sẽ nằm trong vùng SMA ± 2 Sigma).
   - **Logic Mua/Bán:** Nếu giá đâm thủng dải dưới (cách SMA 2 độ lệch chuẩn) 👉 Giả định giá đã bị nén quá xa giá trị thực và sẽ bật lại 👉 BUY (Mean Reversion). Bán khi giá đâm thủng dải trên.

5. **Support / Resistance (Hỗ trợ và Kháng cự cục bộ - `SUPPORT_RESISTANCE`)**
   - **Bản chất:** Thuật toán duyệt qua mảng giá trong quá khứ để tìm các "Đỉnh cục bộ" (Local Maxima - Kháng cự) và "Đáy cục bộ" (Local Minima - Hỗ trợ). Đỉnh/đáy cục bộ được xác định nếu nó cao/thấp hơn tất cả các nến xung quanh nó trong một cửa sổ thời gian.
   - **Tham số kỹ thuật:**
     - `window` (Mặc định: 20): Số lượng nến trước và sau nến hiện tại dùng để xét xem nó có phải là đỉnh/đáy hay không.
   - **Logic Mua/Bán:** Mua khi giá hiện tại đang tiệm cận sát (trong phạm vi sai số nhỏ) với một đường Hỗ trợ (vì kỳ vọng giá sẽ bật lên). Bán khi chạm Kháng cự.

6. **News Sentiment (Phân tích cảm xúc tin tức - `NEWS_SENTIMENT`)**
   - **Bản chất:** Dùng mô hình Machine Learning/NLP để chấm điểm (Score) tin tức từ -1 (Cực kỳ Tiêu cực) đến 1 (Cực kỳ Tích cực). Strategy này tính điểm trung bình của tất cả tin tức xuất hiện trước khi cây nến đóng cửa.
   - **Tham số kỹ thuật:**
     - `windowMinutes` (Mặc định: 60): Nhìn ngược về quá khứ 60 phút để gom các bài báo lại tính trung bình điểm cảm xúc.
     - `buyThreshold` (Mặc định: 0.7): Ngưỡng xác nhận tin tốt mãnh liệt.
     - `sellThreshold` (Mặc định: -0.7): Ngưỡng xác nhận tin xấu (FUD).
   - **Logic Mua/Bán:** Điểm trung bình >= 0.7 👉 BUY. Điểm trung bình <= -0.7 👉 SELL.

*(Nhờ có cơ chế Factory Schema, Frontend chỉ việc đọc danh sách tham số kỹ thuật trên qua API và tự động vẽ ra các Form nhập liệu cực kỳ gọn gàng mà không phải code cứng bất kỳ thẻ HTML `<input>` nào).*

---

## 7. Giải thích AD-07: Tách biệt Chính sách kết hợp (Combination Policy) và Logic phân tích (Strategy Analysis)

**Vấn đề (Sự xung đột của các chỉ báo):**
Trong Module 5 của đồ án có nêu rõ bài toán "Composite Strategy": làm sao để kết hợp nhiều chiến lược (MA, RSI, Bollinger Bands...) lại với nhau.
Tuy nhiên, thị trường rất phức tạp. Tại một thời điểm, RSI có thể báo MUA (ví dụ vì giá đã quá bán), nhưng MA lại báo BÁN (vì giá nằm dưới đường trung bình). Nếu chúng ta viết cứng logic giải quyết xung đột này vào bên trong từng chiến lược, mã nguồn sẽ trở thành một mớ hỗn độn và vi phạm Nguyên lý Đơn trách nhiệm (Single Responsibility Principle - SRP).

**Giải pháp (Quyết định AD-07):**
Hệ thống giải quyết bài toán này bằng cách tách bạch hoàn toàn 2 vai trò:

1. **Người phân tích (Strategy):**
   - Các class như `MovingAverageStrategy`, `RsiStrategy` chỉ đóng vai trò là những chuyên gia độc lập.
   - Nhiệm vụ duy nhất của chúng là nhìn vào dữ liệu nến và đưa ra 1 trong 3 kết luận: `BUY`, `SELL`, hoặc `HOLD`. Chúng tuyệt đối không cần quan tâm đến các chuyên gia khác đang nghĩ gì.

2. **Người phân xử (Combination Policy):**
   - Các class như `MajorityVotePolicy` (Bầu chọn đa số) hay `WeightedVotePolicy` (Bầu chọn có trọng số) đóng vai trò là một "Hội đồng xét duyệt".
   - `MajorityVotePolicy` sẽ đếm số phiếu: Ví dụ 2 phiếu BUY, 1 phiếu HOLD, 1 phiếu SELL => Quyết định cuối cùng là BUY.
   - `WeightedVotePolicy` sẽ tính điểm theo trọng số (như ví dụ trong mục 14 của đồ án: MA=0.2, RSI=0.3, SR=0.5).

**Tính hợp lý và Mức độ đáp ứng đề bài:**
- **Hoàn toàn đáp ứng Module 5 (Composite Strategy) & Đạt điểm tối đa về thiết kế:** Đề bài yêu cầu hệ thống phải cho phép "kết hợp nhiều strategy" (Mục 13) và gợi ý "có thể dùng Majority Vote hoặc Weighted Combination" (Mục 14). Việc tách biệt bằng Pattern Policy/Strategy giúp hệ thống không chỉ làm được điều đó, mà còn mở ra cơ hội cho **Frontend (người dùng) được tự do chọn lựa chính sách kết hợp**.
- Việc Frontend (Giao diện) cung cấp một Dropdown cho phép người dùng chọn "Cách thức kết hợp" (Majority Vote hay Weighted) là **hoàn toàn chính xác và rất xuất sắc**. Nó biến hệ thống thực sự thành một "Strategy Lab" (Phòng thí nghiệm) đúng nghĩa, nơi người dùng có thể tự do thử nghiệm việc lai tạo các chiến lược theo nhiều cách khác nhau.
- **Mở rộng không giới hạn (Zero-code extensibility):** Nếu sau này cần thêm một cách kết hợp mới (ví dụ: Bầu chọn theo độ tin cậy AI - `AiConfidencePolicy`), lập trình viên chỉ cần viết thêm 1 class Policy mới mà hoàn toàn không phải sửa lại code của MA hay RSI cũ. Kiến trúc này đáp ứng hoàn hảo yêu cầu số 12 của đề bài.

---

## 8. Giải thích AD-08: Search Engine biến đổi cả thành phần chiến lược lẫn tham số

**Vấn đề (Bối cảnh):**
Câu hỏi cốt lõi của thí nghiệm (như mô tả trong Yêu cầu 8 của file `new_add_requirement.txt`) là tự động khám phá và so sánh các tổ hợp chiến lược khác nhau (ví dụ: MA + RSI so sánh với Bollinger + Support/Resistance). 
Nếu công cụ tìm kiếm (Search Engine) chỉ biết thay đổi tham số (ví dụ: đổi MA chu kỳ 10 thành MA chu kỳ 20) mà giữ nguyên cấu trúc cố định (luôn chạy MA), hệ thống sẽ thất bại trong việc đánh giá hiệu quả của việc lai tạo các chiến lược khác nhau. Đặc biệt, yêu cầu nhấn mạnh **"không được vét cạn vì quá lâu"**, do không gian tổ hợp của tất cả các chiến lược và tham số có thể lên tới hàng triệu trường hợp.

**Giải pháp (Quyết định AD-08):**
Hệ thống giải quyết bài toán này bằng cách thiết kế các Trình sinh chiến lược (Strategy Generator) có khả năng can thiệp vào cả **cấu trúc (thành phần tham gia)** lẫn **tham số**:
1.  **Random Generator:** Nó mô hình hóa mỗi họ chiến lược dưới dạng một nút bật/tắt ("tham gia" hoặc "loại trừ" khỏi tổ hợp). Sau đó nó sinh ngẫu nhiên và chỉ từ chối trường hợp duy nhất là tất cả các chiến lược đều bị "loại trừ" (vì vô nghĩa).
2.  **Genetic Generator:** Sử dụng Thuật toán Di truyền (Genetic Algorithm) để tìm kiếm thông minh. Trong quá trình Lai ghép (Crossover) kiểu gene-by-type và Đột biến (Mutation), hệ thống có quyền loại bỏ một chiến lược đang có hoặc thêm vào một chiến lược mới, không bao giờ rơi vào ngõ cụt.

**Hệ quả và Mức độ đáp ứng đề bài:**
-   **Đáp ứng tuyệt đối Yêu cầu 8 (Chống Vét cạn):** Bằng cách áp dụng Sinh ngẫu nhiên (Random) và Di truyền (Genetic) thay vì các vòng lặp `for` lồng nhau để Vét cạn (Brute-force), hệ thống đã "tìm ra cách hay hơn" để lướt qua không gian tìm kiếm khổng lồ. Việc sinh Ứng viên (Candidate) tuân theo cơ chế Lazy (lười biếng - không tạo trước toàn bộ mà chỉ sinh khi có yêu cầu) và được giới hạn bởi các Điều kiện dừng (Stop Condition).
-   **Tách biệt logic linh hoạt (Maintainability):** Theo tinh thần của đề án, việc thay đổi thuật toán tìm kiếm tuyệt đối không được làm ảnh hưởng đến chức năng Backtest hay Evaluator. Điều này đã được chứng minh qua unit test kiến trúc `GeneratorReplacementArchitectureTest`.

**📍 Code tham chiếu:**
-   [`RandomStrategyGenerator`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/experiment/application/RandomStrategyGenerator.java)
-   [`GeneticStrategyGenerator`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/experiment/application/GeneticStrategyGenerator.java)
-   Cả hai đều cùng implement một giao diện chung là [`StrategyGenerator`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/experiment/port/StrategyGenerator.java), cho phép dễ dàng cắm rút và thay đổi thuật toán ở Runtime.

---

## Hoạt động chi tiết của Thuật toán Di truyền (Genetic Algorithm)

Để giải quyết bài toán chống Vét cạn (Brute-force) khi kết hợp hàng ngàn chiến lược, hệ thống sử dụng Thuật toán Di truyền được lập trình bám sát các khái niệm sinh học tiến hóa. Quá trình này gồm 4 bước xoay vòng:

### 1. Cơ chế hoạt động (Quá trình Tiến hóa)
*   **Bước 1: Khởi tạo Quần thể (Initial Population)**
    - Kích thước quần thể mặc định: `POPULATION_SIZE = 20`.
    - Lượt chạy đầu tiên, hệ thống dùng thuật toán ngẫu nhiên (Random) để "đẻ" ra 20 ứng viên (Candidate Strategy).
*   **Bước 2: Chấm điểm & Chọn lọc Tự nhiên (Fitness & Parent Selection)**
    - Sau khi 20 ứng viên này được Backtester chạy thử xong, hệ thống sẽ thu về điểm số (Fitness - đánh giá dựa trên Lợi nhuận/Win-rate).
    - Thuật toán xếp hạng 20 cá thể từ cao xuống thấp. Nó sẽ **đào thải 50% kẻ yếu nhất** và giữ lại Top 50% (10 cá thể giỏi nhất) để làm cha mẹ (Parents) sinh ra thế hệ tiếp theo.
*   **Bước 3: Lai ghép (Crossover - Gene-by-type)**
    - Để đẻ ra 1 đứa con, hệ thống chọn ngẫu nhiên 2 người trong 10 người cha mẹ.
    - Việc lai ghép diễn ra theo nguyên tắc "cùng loài": Ví dụ lấy gen MA của cha lai với gen MA của mẹ. Đối với từng tham số (như Chu kỳ), đứa con có 50% xác suất lấy số của cha, 50% lấy số của mẹ.
    - Nếu cha có gen Bollinger Bands mà mẹ không có, đứa con có 50% cơ hội được thừa hưởng gen đó.
*   **Bước 4: Đột biến (Mutation)**
    - Xác suất đột biến: `MUTATION_PERCENT = 20` (20% số lượng con sinh ra sẽ bị đột biến).
    - Có 2 kiểu đột biến xảy ra ngẫu nhiên:
        1. **Mất gen:** Nếu cá thể đang có cấu trúc gồm nhiều chiến lược kết hợp (ví dụ: MA + RSI + BB), nó có thể tự động bị rụng đi mất 1 chiến lược để xem có gọn nhẹ, hiệu quả hơn không.
        2. **Đột biến tham số:** Thuật toán bốc ngẫu nhiên 1 tham số (ví dụ RSI Threshold) và tự ý đổi sang một con số hoàn toàn mới mà cả cha và mẹ đều không có (nhưng vẫn nằm trong vùng Không gian tham số cho phép).

### 2. Quyền kiểm soát đầu vào của người dùng
Thuật toán di truyền không chạy mù quáng mà chịu sự kiểm soát chặt chẽ của người dùng thông qua đối tượng `SearchContext`:
1. **Danh sách chiến lược (`strategyTypes`):** Người dùng tick chọn MA, RSI... trên giao diện. Thuật toán chỉ được phép dùng những nguyên liệu này để lai tạo thành phần.
2. **Không gian tham số (`parameterSpace`):** Khi xảy ra Đột biến (Mutation), thuật toán không được phép lấy một con số vô lý. Nó bắt buộc phải bốc ngẫu nhiên một giá trị nằm trong danh sách giới hạn mà người dùng đã khoanh vùng (ví dụ: MA chỉ được chạy trong mảng `[10, 20, 30]`).
3. **Hạt giống ngẫu nhiên (`randomSeed`):** Để đảm bảo tính minh bạch khoa học, người dùng có thể nhập một "hạt giống" (Seed). Nếu nhập cùng 1 Seed, trải qua 100 thế hệ tiến hóa ngẫu nhiên, kết quả sinh ra vẫn giống y hệt nhau (Deterministic). Điều này giúp các thí nghiệm có khả năng **"Tái lập được" (Reproducibility)** - Tiêu chuẩn cao nhất của một hệ thống tài chính phân tích.

---

## 9. Giải thích AD-09: Tách biệt Backtest, Đánh giá, và Xếp hạng (Deterministic Pipeline)

**Vấn đề (Bối cảnh):**
Trong quá trình thử nghiệm chiến lược, chúng ta phải thực hiện rất nhiều khâu: (1) Sinh ra chiến lược, (2) Mô phỏng giao dịch mua/bán (Backtest), (3) Tính toán chỉ số lợi nhuận/rủi ro (Evaluate), và (4) Xếp hạng lên Bảng vàng (Rank).
Nếu code tất cả các khâu này lồng chặt vào nhau (ví dụ: vừa test, vừa chấm điểm rồi nhét ngay vào bảng xếp hạng), hệ thống sẽ cực kỳ khó kiểm thử (Unit Test) và không thể lưu vết phiên bản (versioning). Nguy hiểm hơn là rủi ro **Look-ahead bias** (Nhìn trộm tương lai) – lỗi kinh điển nhất của dân làm Bot Trading khi chiến lược vô tình "thấy" được giá của ngày mai để ra quyết định mua ngày hôm nay.

**Giải pháp (Quyết định AD-09):**
Hệ thống thiết kế một luồng ống dẫn (Pipeline) chia cắt rạch ròi 4 giai đoạn độc lập: `Candidate -> Backtest -> Evaluate -> Rank`.

1. **Deterministic Backtest Engine (Cỗ máy mô phỏng chống nhìn trộm):**
   - Engine này áp đặt một nguyên tắc sắt đá: Nó chỉ cung cấp cho Chiến lược **một mảng nến từ quá khứ tính đến đúng cây nến hiện tại (candle prefix)**. Chiến lược tuyệt đối không có cách nào truy cập được nến của tương lai vì dữ liệu đó không hề tồn tại trong mảng được truyền vào.
   - **Độ trễ thực thi (Execution Delay):** Tín hiệu (BUY/SELL) được sinh ra ở cây nến thứ N sẽ **chỉ được khớp lệnh ở giá mở cửa (Open Price) của cây nến N+1**. Điều này mô phỏng chính xác độ trễ của thế giới thực: Bạn phân tích xong nến hiện tại đóng cửa, sau đó mới gửi lệnh lên sàn thì lúc đó sàn đã sang cây nến tiếp theo rồi.
   - **Đóng vị thế cuối kỳ (Force Close):** Để tính toán lợi nhuận chính xác, khi tập dữ liệu test kết thúc, bất kỳ vị thế (lệnh) nào còn đang mở (chưa chốt lời/cắt lỗ) sẽ bị ép đóng lại ở giá Close của cây nến cuối cùng.

**Hệ quả và Mức độ đáp ứng đề bài:**
-   **Tính Truy vết (Provenance):** Do tách biệt, mỗi kết quả sinh ra đều được dán nhãn phiên bản của từng Engine tham gia (ví dụ: kết quả này được sinh ra bởi Backtest V1, Evaluate V2). Nhờ đó, nếu kết quả có điểm bất thường, kỹ sư có thể dễ dàng truy vết lại nguyên nhân do module nào gây ra.
-   **Độ tin cậy tuyệt đối:** Bằng cách thiết kế chống Look-ahead và mô phỏng độ trễ thực tế, kết quả (Winrate, Profit) hiển thị trên Bảng xếp hạng (Leaderboard) mang tính thực chiến cực cao, hoàn toàn đáng tin cậy.

**📍 Code tham chiếu:**
-   [`DeterministicBacktestEngine`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/experiment/application/DeterministicBacktestEngine.java): Chứa logic cốt lõi chạy vòng lặp nến và chống look-ahead.
-   [`ExperimentPipelineService`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/experiment/application/ExperimentPipelineService.java): Đóng vai trò "Nhạc trưởng" điều phối luồng `Candidate -> Backtest -> Evaluate -> Rank`.
-   Các bài Test kiến trúc: [`ExperimentPipelineServiceTest`](../crypto-strategy-lab/core/src/test/java/com/cryptolab/experiment/ExperimentPipelineServiceTest.java) và [`DeterministicBacktestEngineTest`](../crypto-strategy-lab/core/src/test/java/com/cryptolab/experiment/DeterministicBacktestEngineTest.java).

---

## 10. Giải thích AD-10: Win Rate là chỉ số được lưu trữ (Chi tiết tính toán 4 chỉ số)

**Vấn đề (Bối cảnh):**
Trong yêu cầu cơ bản (MVP), hệ thống cần hiển thị 4 chỉ số trên Bảng xếp hạng: Total Return (Tổng lợi nhuận), Win Rate (Tỷ lệ thắng), Max Drawdown (Sụt giảm tối đa), và Number of Trades (Số lượng giao dịch).
Vấn đề là các chỉ số này, đặc biệt là tỷ lệ thắng (Win Rate), được tính toán như thế nào cho chuẩn xác và có nên tính đi tính lại mỗi lần người dùng xem bảng xếp hạng hay không.

**Giải pháp (Quyết định AD-10):**
Hệ thống quyết định tính toán và lưu thẳng 4 chỉ số này vào Database (trong bảng `evaluation` và bảng chiếu `leaderboard`). Cụ thể, cách tính 4 chỉ số được quy định chặt chẽ như sau:

1. **Number of Trades (Số lượng giao dịch):**
   - Đơn giản là đếm tổng số lệnh đã đóng (bao gồm cả lệnh mua và bán khống, cả chốt lời, cắt lỗ, hoặc bị ép đóng ở cuối kỳ test).

2. **Win Rate (Tỷ lệ thắng):**
   - **Công thức:** `Win Rate = (Số lệnh có lãi / Tổng số lệnh đã đóng) × 100`.
   - **Thế nào là lệnh có lãi (Profitable trade)?** Lệnh có lãi là lệnh có lợi nhuận ròng (PnL - Profit and Loss) lớn hơn hẳn 0 (`pnl > 0`). 
   - **Lưu ý quan trọng:** Lệnh hòa vốn (breakeven - pnl bằng 0) hoặc lỗ phí giao dịch (pnl < 0) đều KHÔNG được tính là lệnh thắng. Nếu chiến lược chưa đóng bất kỳ lệnh nào, Win Rate mặc định trả về 0% để tránh lỗi chia cho 0.
   - Ví dụ: Có 3 lệnh (Lệnh 1 lãi $10, Lệnh 2 lỗ $4, Lệnh 3 lãi $6) -> Có 2 lệnh lãi trên tổng 3 lệnh -> Win Rate = 66.67%.

3. **Total Return (Tổng lợi nhuận %):**
   - **Công thức:** `(Vốn cuối kỳ - Vốn ban đầu) / Vốn ban đầu × 100`.
   - Ví dụ: Bắt đầu với $10,000, kết thúc vòng test tài khoản có $11,000 -> Tăng $1,000 -> Total Return = 10%.

4. **Max Drawdown (Sụt giảm vốn tối đa %):**
   - Đo lường mức độ rủi ro tồi tệ nhất của chiến lược.
   - **Công thức:** Tìm khoảng tụt giảm lớn nhất từ đỉnh vốn cao nhất (Peak) xuống đáy vốn thấp nhất (Trough) ngay sau cái đỉnh đó.
   - Ví dụ: Vốn đi từ 10,000 lên 12,000 (Đỉnh), rớt xuống 9,000 (Đáy), rồi lại lên 11,000. Khoảng tụt sâu nhất là từ 12,000 rớt xuống 9,000 (mất 3,000). Drawdown = (12,000 - 9,000) / 12,000 = -25%. (Chỉ số này càng âm, mức độ rủi ro càng cao).

**Hệ quả và Mức độ đáp ứng đề bài:**
-   **Hiệu năng cao:** Không cần phải dùng vòng lặp để duyệt lại toàn bộ danh sách hàng nghìn lệnh giao dịch nhằm tính lại các chỉ số mỗi lần người dùng mở Bảng xếp hạng. Điều này giúp Leaderboard load cực kỳ nhanh (O(1)).
-   **Khóa cứng phiên bản (Versioning):** Nếu trong tương lai, team quyết định đổi công thức tính (ví dụ: lệnh hòa vốn cũng tính là thắng), hệ thống bắt buộc phải nâng version của Evaluator lên để tránh làm sai lệch dữ liệu cũ.

**📍 Code tham chiếu:**
- Logic tính toán và kiểm chứng các công thức trên nằm tại [`EvaluationRankingStateTest`](../crypto-strategy-lab/core/src/test/java/com/cryptolab/experiment/EvaluationRankingStateTest.java)
- Kiểm chứng tích hợp ghi xuống PostgreSQL tại [`AsyncEvaluationRankingIT`](../crypto-strategy-lab/integration-tests/src/test/java/com/cryptolab/persistence/AsyncEvaluationRankingIT.java)

---

## 11. Giải thích AD-12: Leaderboard là bảng chiếu (Query Projection)

**Vấn đề (Bối cảnh):**
Đọc Bảng xếp hạng (Leaderboard) là thao tác xảy ra thường xuyên nhất của người dùng. Nếu mỗi lần người dùng mở web, hệ thống lại phải chui vào DB, lấy hàng triệu kết quả thí nghiệm ra sort, group, và tính điểm lại từ đầu thì Database sẽ quá tải và Server sẽ sập (Crash). Không được phép xây lại toàn bộ kết quả thí nghiệm cho mỗi lần xem.

**Giải pháp (Quyết định AD-12):**
Áp dụng mẫu thiết kế (Design Pattern) CQRS một phần. Bảng Leaderboard được thiết kế thành một "Bảng chiếu" (Query Projection) được tính toán sẵn.
- Khi một thí nghiệm chạy xong khâu Backtest, nó ném ra sự kiện `BacktestCompleted`.
- Khâu Evaluator bắt được sự kiện, tính điểm xong ném ra sự kiện `StrategyEvaluated`.
- Listener bắt được sự kiện này và tự động `UPSERT` (Cập nhật hoặc Thêm mới) một dòng vào thẳng bảng Leaderboard.
- Các API REST và WebSocket (STOMP) phục vụ người dùng chỉ việc `SELECT` trực tiếp từ bảng chiếu này ra mà không cần tính toán gì thêm.

**Hệ quả và Mức độ đáp ứng đề bài:**
-   **Tốc độ phản hồi (Latency) cực thấp:** Người dùng xem Leaderboard realtime không bị giật lag.
-   **Idempotent:** Hệ thống Message (như RabbitMQ) có thể gửi nhầm một sự kiện 2 lần, nhưng do thiết kế `UPSERT` (cập nhật theo ID cố định), dữ liệu Leaderboard sẽ không bao giờ bị nhân đôi (xử lý trùng lặp an toàn).
-   Hệ thống không cần dùng toàn bộ Event Sourcing phức tạp vì ta không có nhu cầu phát lại toàn bộ lịch sử hệ thống, giúp kiến trúc nhẹ nhàng hơn.

**📍 Code tham chiếu:**
- Lưu trữ kết quả thí nghiệm và cập nhật Bảng chiếu: [`JdbcExperimentRepository`](../crypto-strategy-lab/infrastructure/src/main/java/com/cryptolab/infrastructure/experiment/adapter/JdbcExperimentRepository.java)
- Kiểm chứng tích hợp tính năng Projection: [`AsyncEvaluationRankingIT`](../crypto-strategy-lab/integration-tests/src/test/java/com/cryptolab/persistence/AsyncEvaluationRankingIT.java)

---

## 12. Giải thích AD-13: Truy vết bất biến (Provenance) là một phần của kết quả

**Vấn đề (Bối cảnh):**
Trong môi trường tài chính chuyên nghiệp, một kết quả (Return +200% trên Leaderboard) sẽ hoàn toàn vô nghĩa và bị vứt vào sọt rác nếu không ai chứng minh được nó đến từ đâu. Nếu không biết chiến lược đó dùng tham số gì, chạy trên tập dữ liệu nào, và bằng phiên bản Engine nào, thì không ai dám bỏ tiền thật ra trade theo nó.

**Giải pháp (Quyết định AD-13):**
Xây dựng khái niệm **Truy vết bất biến (Immutable Provenance)**. Mỗi khi lưu một kết quả Thí nghiệm (Experiment), hệ thống đóng gói và lưu trữ vĩnh viễn các thông tin "dấu vân tay" sau:
1. Hash (chuỗi mã hóa) của ứng viên và cấu trúc thuật toán.
2. Phiên bản chính xác và tham số của từng chiến lược (Ví dụ MA v1.0, chu kỳ 20).
3. Loại chính sách kết hợp (Majority Vote hay Weighted).
4. Checksum (chữ ký số) của tập dữ liệu nến (Dataset) và mốc thời gian lấy mẫu.
5. Snapshot (Bản chụp) của thuật toán Search Generator.
6. Phiên bản của Backtest Engine, Evaluator.
7. Mã Commit Code của Git và Build Version.
8. Toàn bộ tín hiệu (Signals) sinh ra và lịch sử giao dịch (Trades).

**Hệ quả và Mức độ đáp ứng đề bài:**
-   **Tái lập hoàn toàn (Full Reproducibility):** Ngay cả khi 1 năm sau, code dự án đã nâng cấp lên bản mới hoàn toàn, bạn vẫn có thể nhìn vào kết quả cũ và biết chính xác nó được sinh ra như thế nào. Bạn có thể tái lập nghiệm thu lại kết quả đó y hệt 100%.
-   **Đánh đổi (Trade-off):** Hệ thống sẽ tốn nhiều dung lượng Database hơn để lưu đống "vân tay" này. Nhưng trong Khoa học dữ liệu tài chính, đây là sự đánh đổi bắt buộc và hoàn toàn xứng đáng.

**📍 Code tham chiếu:**
- Bài kiểm tra toàn diện nhất cho truy vết: [`ExperimentPipelineIT`](../crypto-strategy-lab/integration-tests/src/test/java/com/cryptolab/persistence/ExperimentPipelineIT.java). Bài test này mô phỏng luồng thực tế: Từ Top 1 Leaderboard → lấy ra `experimentId` → truy ngược lại toàn bộ cấu hình, dataset, commit code, signals... và xác minh không có cọng lông nào bị thay đổi.

---

## 13. Bức tranh tổng thể Yêu cầu 3: Search Engine, Backtest, Leaderboard & Truy vết

**Tổng quan kiến trúc:**
Yêu cầu 3 là trái tim của nền tảng Crypto Strategy Lab. Nó chuyển đổi hệ thống từ một bộ công cụ hiển thị biểu đồ thụ động thành một **"Cỗ máy nghiên cứu và lai tạo chiến lược lượng tử"** (Quantitative Strategy Research Engine). Toàn bộ khối kiến trúc này được xây dựng dựa trên nguyên tắc **Chia để trị (Separation of Concerns)** và **Lập trình hướng sự kiện (Event-driven)**.

**Hành trình của một Chiến lược (The Pipeline Journey):**

1. **Khởi tạo và Khoanh vùng (Search Context):**
   Người dùng không bị ép phải duyệt hàng tỷ trường hợp. Họ thiết lập một "Không gian tìm kiếm" (Search Context) – chỉ định rõ những nguyên liệu được phép dùng (MA, RSI) và giới hạn tham số (ví dụ RSI chỉ chạy từ 20-80). Điều này kết hợp với Isolation (Khóa ngoại `account_id`) đảm bảo thuật toán của ai người nấy giữ.

2. **Cỗ máy Lai tạo (Search Generator - AD-08):**
   Tại đây, hệ thống không duyệt vét cạn (Brute-force) mà sử dụng các thuật toán thông minh như Random Search hoặc Genetic Algorithm (Thuật toán Di truyền). Nhờ thiết kế Interface `StrategyGenerator`, cỗ máy này có khả năng thay thế linh hoạt (Plug & Play). Nó nhận lệnh từ Coordinator, đẻ ra một "lô" (batch) chiến lược. Điểm đột phá là nó biến đổi **cả cấu trúc chiến lược lẫn tham số**, giúp tạo ra những "đứa con lai" độc đáo (Ví dụ: Cha là MA+RSI, Mẹ là MA+BB -> Con là RSI+BB với tham số đột biến).

3. **Vị "Nhạc trưởng" điều phối vòng lặp (SearchCoordinator):**
   Generator chỉ đẻ chiến lược chứ không tự chạy. Vị "nhạc trưởng" `SearchCoordinator` sẽ đứng ra nhận các chiến lược vừa đẻ, đóng gói thành các Job và ném vào Hàng đợi (Message Queue). Đồng thời, nó ôm cái Đồng hồ bấm giờ (Stop Conditions: `maxCandidates`, `maxDuration`, `noImprovementIterations`) để quyết định xem có nên ra lệnh cho Generator đẻ tiếp hay dừng lại. Sự tách biệt này giúp vòng lặp chạy an toàn và tối ưu hóa cho xử lý phân tán (Distributed Processing).

4. **Mô phỏng Giao dịch nghiêm ngặt (Deterministic Backtest - AD-09):**
   Ứng viên chiến lược (sau khi được Worker bốc khỏi Hàng đợi) sẽ được đẩy vào cỗ máy mô phỏng. Cỗ máy này hoạt động như một cỗ máy thời gian, quay về quá khứ và bơm từng cây nến một cho Chiến lược phân tích. Nó tuyệt đối cấm chiến lược nhìn trộm tương lai (No Look-ahead bias) và mô phỏng độ trễ 1 nến khi khớp lệnh. Kết quả là danh sách các lệnh mua/bán (Trades) cực kỳ đáng tin cậy.

5. **Chấm điểm và Lên bảng vàng (Evaluate & Projection - AD-10 & AD-12):**
   Sau khi Backtest nhả ra lịch sử giao dịch, Evaluator sẽ tính toán Win Rate, Max Drawdown, Total Return. Hệ thống không ngốc nghếch tính lại bảng xếp hạng mỗi khi có người xem. Thay vào đó, nó ghi thẳng điểm số này vào một Bảng chiếu (Query Projection) để Frontend đọc với tốc độ bàn thờ (O(1)). Bảng xếp hạng luôn hiển thị Top-K realtime.

6. **Lưu vết và Tái lập (Immutable Provenance - AD-13):**
   Khi một chiến lược leo lên đỉnh vinh quang, nó bị đóng băng vĩnh viễn (Immutable). Hệ thống chụp lại mọi thứ: mã hash, dataset, version, commit code... Giúp cho việc "Start lại" (Resume) hệ thống bản chất là tạo ra một phiên bản thí nghiệm mới mẻ và trong sạch, thay vì ghi đè lên kết quả cũ làm bẩn dữ liệu khoa học.

**Kết luận:** 
Bức tranh Yêu cầu 3 thể hiện sự trưởng thành về mặt kiến trúc. Nó giải quyết hoàn hảo bài toán hiệu năng (Query Projection, Không vét cạn), bài toán chính xác (Chống Look-ahead, Tính toán riêng rẽ) và bài toán khoa học (Truy vết bất biến, Tái lập). Đây chính là tiêu chuẩn kiến trúc cấp doanh nghiệp (Enterprise Architecture) của một hệ thống Algorithmic Trading thực thụ.

---

## 14. Giải thích AD 11 Vòng lặp Liên tục ngầm, Hàng đợi & Worker (Module 9)

**1. Hàng đợi bền vững với Transactional Outbox và Inbox lũy đẳng (AD-11: durable queue with transactional outbox and idempotent inbox)**

**Vấn đề (Bối cảnh):** 
Chạy Backtest không phải là một thao tác diễn ra trong chớp mắt. Nó có thể kéo dài hàng phút nếu tập dữ liệu (dataset) quá lớn và chiến lược quá phức tạp. Do đó, không thể dùng HTTP Request thông thường (đợi tới khi xong mới trả về).
Hơn nữa, hệ thống Message Broker (như RabbitMQ) thông thường chỉ đảm bảo gửi tin nhắn ít nhất một lần (at-least-once). Giả sử hệ thống đang chạy thì một Worker bị sập (crash) do hết RAM, hoặc hệ thống Cloud tự động tăng/giảm số lượng Worker (auto-scale ngang), làm sao để đảm bảo không có Job (nhiệm vụ backtest) nào bị bốc hơi, và không có Job nào bị 2 Worker khác nhau tính toán trùng lặp?

**Giải pháp (Quyết định):**
Để dễ hiểu, hãy hình dung Coordinator và Worker là 2 nhà máy, còn RabbitMQ là người giao bưu kiện. Khung kiến trúc được chia làm 2 cụm hàng đợi (`crypto.backtest.jobs` để giao việc, và `crypto.events` để báo cáo kết quả), vận hành qua quy trình 5 bước chống rớt mạng:

- **Bước 1: Ghi ý định vào Outbox (Transaction 1):** Khi `SearchCoordinator` quyết định cần chạy 1 chiến lược, thay vì gọi thẳng RabbitMQ (lỡ rớt mạng thì mất dữ liệu), nó mở 1 Giao dịch DB (Transaction) để: Tạo 1 bản ghi chiến lược vào bảng `experiments`, bỏ 1 lá thư vào bảng `Outbox`, và Commit (lưu cứng an toàn 100% trên DB).
- **Bước 2: Rơ-le (Relay) quét và ném vào Queue:** Một tiến trình ngầm (đóng vai người đưa thư) quét bảng Outbox mỗi giây. Thấy thư mới -> ném vào Queue của RabbitMQ. Khi RabbitMQ xác nhận đã nhận (`Publisher Confirm / ACK`), Rơ-le mới xóa lá thư trong bảng Outbox.
- **Bước 3: Worker bốc Job và "Thuê" (Lease):** RabbitMQ đẩy thư đến cho 1 Worker. Worker không chạy ngay mà gọi vào DB xin "Thuê" (Lease) Job này trong 5 phút. Nếu Worker sập nguồn (Crash), quá 5 phút mà Job vẫn chưa xong, hệ thống sẽ tự tước quyền và cho Worker khác bốc lại lá thư đó. Chống kẹt Job vĩnh viễn!
- **Bước 4: Tính toán và Ghi Inbox (Transaction 2):** Worker chạy mô phỏng xong, mở 1 Transaction DB để: Lưu lịch sử giao dịch/lợi nhuận, và **quan trọng nhất** là ghi mã `Event_ID` của lá thư đó vào bảng `Inbox`. Sau đó Commit.
- **Bước 5: Lọc trùng (Idempotent):** Giả sử RabbitMQ lỗi mạng, gửi lại lá thư cũ lần 2 cho một Worker khác. Worker 2 chuẩn bị chạy thì lôi `Event_ID` ra soi với bảng `Inbox`. Thấy ID này đã có trong Inbox (tức là đã có người làm rồi), Worker 2 lập tức phớt lờ và báo RabbitMQ xóa thư. Tuyệt đối không chạy trùng lặp 2 lần.

**Hệ quả và Mức độ đáp ứng đề bài:**
- **Bất tử (Resilience):** Hệ thống có thêm các trạng thái retry, lease, outbox, inbox. Dù hệ thống có sập nguồn đột ngột, khi có điện lại nó vẫn chạy tiếp tục chính xác tại điểm đang làm dở, không rớt 1 Job, không trùng 1 kết quả.
- **Mở rộng vô hạn (Horizontal Scaling):** Mã nguồn lõi hoàn toàn không cần quan tâm đến số lượng Worker. Bạn có thể bật 1 Worker hay 100 Worker chạy song song, chúng sẽ tự động bốc Job trong hàng đợi mà không giẫm đạp lên nhau. Điều này đáp ứng mức độ cao nhất mong đợi *"chạy nhiều worker, retry khi lỗi, scale trong tương lai"* của Đề tài.

**📍 Code tham chiếu:**
- Logic của Outbox và Inbox được cài đặt chìm tại tầng hạ tầng (Infrastructure). Toàn bộ luồng điều phối của [`ExperimentPipelineService`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/experiment/application/ExperimentPipelineService.java) và các sự kiện như `BacktestCommand`, `StrategyEvaluated` đều tự động hưởng lợi từ rào chắn này.

**2. Phân tách trạng thái "đang sinh" và "đang chờ hoàn thành" của Search Run (AD-16: generation and run completion are separate states)**

**Vấn đề (Bối cảnh):** 
Trong mô hình bất đồng bộ Hàng đợi - Worker, thuật toán điều phối (`SearchCoordinator`) chỉ làm nhiệm vụ "đẻ" (generate) ra các ứng viên và ném vào Hàng đợi. Tốc độ đẻ thường rất nhanh, trong khi tốc độ chạy Backtest của các Worker lại mất nhiều thời gian hơn.
Giả sử Coordinator đẻ xong mục tiêu 100 chiến lược và lập tức cập nhật trạng thái Search Run là `COMPLETED` (Hoàn thành). Lúc đó, trên giao diện người dùng sẽ báo "Đã tìm kiếm xong", nhưng thực tế 100 chiến lược đó vẫn đang xếp hàng chờ Worker xử lý. Điều này không chỉ gây nhầm lẫn mà còn làm vô hiệu hóa nút "Hủy" (Cancel) trên UI, khiến người dùng không thể can thiệp bắt các Worker ngừng chạy.

**Giải pháp (Quyết định):**
Hệ thống thiết kế tách bạch vòng đời của Search Run ra làm 3 giai đoạn:
- **Giai đoạn đẻ (RUNNING):** Trạng thái ban đầu, Coordinator đang hì hục sinh cấu trúc chiến lược.
- **Giai đoạn chờ (EVALUATING):** Khi Coordinator sinh đủ số lượng hoặc hết giờ, nó chuyển trạng thái sang `EVALUATING` (Đang chấm điểm). Đây là trạng thái trung gian.
- **Giai đoạn chốt sổ (COMPLETED):** Mỗi khi một Worker chạy xong 1 Job (dù thành công, thất bại, hay bị hủy), thao tác cuối cùng của nó trước khi đóng Transaction luôn là kiểm tra: *"Tôi có phải là thằng cuối cùng không? Còn Job nào của lô này chưa chạy không?"*. Worker nào chạy cái Job cuối cùng sẽ lãnh trách nhiệm chuyển trạng thái Search Run thành `COMPLETED`.

**Hệ quả và Mức độ đáp ứng đề bài:**
- **Minh bạch tiến trình:** Trạng thái `EVALUATING` phản ánh chính xác thực tế rằng "Việc tạo đã xong, nhưng hệ thống vẫn đang cày cuốc đánh giá". 
- **Cancellable (Có thể hủy):** Vì `EVALUATING` được định nghĩa là trạng thái non-terminal (chưa kết thúc), hệ thống vẫn cho phép người dùng bấm nút Hủy. Bấm một phát, tất cả Job trong hàng đợi lập tức bị đánh dấu "Đã hủy", các Worker đang rảnh sẽ không bốc thêm Job mới. Nó đáp ứng hoàn hảo tính năng kiểm soát Loop (pause, cancel, tiến trình) mà Đề bài đòi hỏi ở dòng 716.
- **Lưu trữ đơn giản:** Vì chỉ là thay đổi trạng thái Text, Database không cần cập nhật cấu trúc (migration), nhưng kiến trúc luồng (Flow Architecture) thì chặt chẽ hơn bội phần.

**📍 Code tham chiếu:**
- Toàn bộ logic kiểm tra xem tất cả Job đã chạy xong chưa (chốt sổ) được nhúng chặt vào hàm hoàn thành của các Worker, đảm bảo không có bất kỳ trạng thái lơ lửng nào (race conditions).

**3. Quan sát được (Observability) bằng Telemetry và System Status**

**Vấn đề (Bối cảnh):** 
Khi vòng lặp ngầm đã phân tán ra hàng chục Worker và hàng nghìn Job, làm sao người quản trị (và người dùng) biết được tiến độ đến đâu? Bao nhiêu Job bị lỗi? Có bao nhiêu Worker đang rảnh hay bận? Làm sao để đảm bảo vòng lặp không biến thành `while(true)` treo cứng Server?

**Giải pháp (Quyết định):**
Hệ thống được "phủ sóng" công cụ thu thập số liệu (Micrometer) ở mọi ngóc ngách:
- **Theo dõi vòng lặp & Điều kiện dừng rõ ràng:** `SearchCoordinator` được bọc bởi `StopConditionEvaluator`. Vòng lặp bị ép buộc phải dừng nếu vi phạm 1 trong 3 giới hạn: (1) Đạt đủ số lượng `maxCandidates`, (2) Hết hạn thời gian `maxDuration`, hoặc (3) Trải qua N vòng mà thứ hạng không nhích lên (`noImprovementIterations`). Trạng thái của vòng lặp liên tục được báo cáo qua `SearchTelemetry`.
- **Giám sát Sức khỏe Worker:** Mỗi Worker được gắn một bộ đếm `WorkerTelemetry`. Chạy xong một Job, nó báo cáo ngay kết quả: Thành công, Thất bại do hệ thống (`InfrastructureFailure`), hay rớt đài do dính "Thuốc độc" (`PoisonMessage`). Thời gian chạy từng Job cũng được đo đếm chi li.
- **Giám sát Hàng đợi:** Hàm `OperationalStatusProvider` liên tục đếm số lượng `runningJobs` (Worker đang bận) và `pendingOutbox` (Thư đang kẹt chưa gửi) để hiển thị lên API tình trạng hệ thống.

**Hệ quả và Mức độ đáp ứng đề bài:**
- Biến một hệ thống chạy ngầm "hộp đen" trở nên "trong suốt" (Transparent). Hệ thống hoàn toàn có thể kết nối với Grafana / Prometheus để vẽ biểu đồ theo dõi Real-time. Nó giải quyết triệt để yêu cầu về khả năng quan sát và kiểm soát vòng lặp của Đề bài.

**📍 Code tham chiếu:**
- Các file `MicrometerWorkerTelemetry`, `MicrometerSearchTelemetry`, `StopConditionEvaluator` và Resource `/api/v1/system/status`.

---

## 15. Giải thích AD-22: Dữ liệu Sentiment được sao chép vào Dataset bất biến (Module 6)

**Đề bài có yêu cầu tính năng này không?**
Có, Đề tài yêu cầu cực kỳ rõ ràng ở nhiều file tài liệu:
- **File Đồ án cuối kỳ (dòng 59):** *"11. Phân tích sentiment của tin tức bằng mô hình Machine Learning."*
- **File Đồ án cuối kỳ (dòng 828, 844):** Yêu cầu chiến lược giao dịch phải tính toán được *"Average sentiment trong 1 giờ > 0.7"* để ra quyết định Mua/Bán.
- **File Slide kiến trúc (dòng 996):** Đòi hỏi *"MLOps sentiment model lifecycle/versioning"* (Quản lý vòng đời mô hình).

**Vấn đề (Bối cảnh):** 
Giả sử có một chiến lược giao dịch tên là `NewsSentimentStrategy` (Mua khi tin tốt, Bán khi tin xấu). 
Nếu trong lúc chạy Backtest với nến (Candles) của 1 năm trước, mà thuật toán lại gọi trực tiếp (Query) vào Database tin tức để lấy điểm Sentiment ở thời điểm "hiện tại", thì đó gọi là "Rò rỉ tương lai" (Future Leakage) hay "Nhìn trước tương lai" (Look-ahead bias). Nó giống như việc bạn cầm kết quả xổ số ngày mai quay về quá khứ để đánh đề vậy, AI sẽ luôn luôn "thắng ảo" 100%.
Thứ hai, nếu bảng tin tức trong Database liên tục thay đổi do có tin mới đổ về, cùng một thuật toán Backtest chạy ở 2 thời điểm khác nhau sẽ cho ra 2 kết quả khác nhau. Điều này vi phạm nghiêm trọng nguyên tắc cốt lõi "Bất biến và có thể Tái lập" của hệ thống phân tích khoa học.

**Giải pháp (Quyết định):**
Hệ thống sử dụng cơ chế **"Chụp ảnh nhanh & Đóng băng" (Snapshot & Immutable Copy)**:
- Ngay tại khoảnh khắc hệ thống tạo ra một Thí nghiệm (Experiment), nó sẽ gom toàn bộ điểm số Sentiment đã phân tích và **sao chép (copy)** chúng gắn chết vào bên trong đối tượng `Dataset` của thí nghiệm đó.
- Khối `Dataset` này sau đó được tính mã băm (Checksum SHA-256) bao phủ cả Dữ liệu giá (Candles) lẫn Dữ liệu cảm xúc (Sentiment). Nếu dataset không có tin tức, checksum giữ nguyên; nếu có tin tức, nó sinh ra một checksum khác biệt đại diện cho "Dataset chứa tin tức".
- Khi Cỗ máy Backtester chạy vòng lặp, thay vì kết nối mạng ra ngoài DB, nó chỉ được phép đọc điểm Sentiment từ trong khối `Dataset` bất biến đã bị đóng băng. Thêm vào đó, nó phải tuân thủ kỷ luật: Chỉ cung cấp cho Chiến lược những bản tin (observation) có thời gian `published_at` nằm TRƯỚC thời điểm đóng của cây Nến hiện tại. 

**Hệ quả và Mức độ đáp ứng đề bài:**
- **Chống gian lận thời gian tuyệt đối:** Đảm bảo chính xác 100% dòng thời gian lịch sử. Không có chuyện cây nến lúc 8:00 sáng lại được nhồi một bản tin phát hành lúc 9:00 sáng.
- **Bảo chứng khoa học (Reproducibility):** Vì Sentiment đã bị đóng băng vào Checksum, bạn chạy lại (Rerun) Thí nghiệm này 10 năm sau kết quả vẫn đảm bảo y hệt như ngày hôm nay.
- **Hỗ trợ Plugin MLOps hoàn hảo:** Điểm số Sentiment được lưu trữ kèm theo ID của mô hình (`model identity`) và phiên bản (`version`). Hệ thống có thể so sánh chéo để xem Phiên bản Model AI nào phân tích tâm lý chuẩn hơn (Đáp ứng xuất sắc yêu cầu MLOps dòng 996).

**📍 Code tham chiếu:**
- Các đối tượng Domain như `SentimentObservation`, `Dataset` và cơ chế lọc tin tức của `StrategyContext` trong module lõi (`core`).

---

## 16. Giải thích AD-25: Tài khoản dùng Server-side Session và BCrypt (Module Auth)

**Vấn đề (Bối cảnh):** 
Với yêu cầu tính năng Bổ sung (Yêu cầu 6), hệ thống phải phân biệt được chiến lược do User A tạo ra không được phép cho User B xem, và chức năng Continuous Discovery phải gán với Chủ sở hữu (Owner). Do Frontend (Browser) và Backend (API) nằm trên cùng một tên miền (same origin), nếu dùng công nghệ JWT Bearer Token, Frontend sẽ phải tự viết code quản lý lưu trữ Token cực kỳ phức tạp (dễ dính lỗi bảo mật XSS nếu lưu ở LocalStorage). 

**Giải pháp (Quyết định):**
Hệ thống sử dụng giải pháp **Server-side Session + HTTP-only Cookie** cổ điển nhưng bảo mật nhất:
- **Lưu mật khẩu (BCrypt):** Database tuyệt đối không lưu mật khẩu thật. Mật khẩu được băm bằng thuật toán `BCrypt` với độ khó (Cost) = 12 (đủ sức chống lại các dàn máy đào coin brute-force hiện tại). Username được chuẩn hóa (không phân biệt hoa thường) để chống lỗi đăng nhập.
- **Phiên đăng nhập (Session):** Khi người dùng đăng nhập thành công, Server tạo ra một Session trên RAM và trả về cho Browser một đoạn mã Cookie có cờ `HTTP-only` (Javascript của Browser không thể đọc được) và `SameSite Strict` (Chống tấn công giả mạo CSRF). 
- **Bảo mật Tài nguyên (Guard):** Các API Public như xem Bảng xếp hạng (Leaderboard) hay xem Giá (Market) vẫn cho phép khách vô danh gọi thoải mái. Riêng các API tạo Chiến lược hay chạy Tìm kiếm sẽ bị chặn bởi `Session Guard` — nếu Cookie không có mã Account ID hợp lệ, lập tức trả về lỗi `401 Unauthorized`.

**Hệ quả và Mức độ đáp ứng đề bài:**
- **Bảo mật tối đa:** Mã độc Javascript trên trình duyệt không bao giờ đánh cắp được Session của người dùng (nhờ `HTTP-only`). Lỗi đăng nhập trả về thông báo chung chung ("Sai tài khoản hoặc mật khẩu") để hacker không thể dò xem tài khoản có tồn tại hay không.
- **Đánh đổi (Trade-off):** Vì Session lưu trên RAM của API Server, nếu sau này cần chạy 10 con API Server cùng lúc (Scale ngang), kiến trúc sẽ phải bổ sung thêm cơ chế "Sticky Session" ở Load Balancer hoặc dùng chung bộ nhớ Redis (Shared Session Store).

**📍 Code tham chiếu:**
- Băm mật khẩu: [`BCryptPasswordHasher`](../crypto-strategy-lab/infrastructure/src/main/java/com/cryptolab/infrastructure/account/adapter/BCryptPasswordHasher.java) (Cost = 12).
- Cấu hình Cookie HTTP-only, SameSite Strict: File `application.yml` (`server.servlet.session.cookie...`).
- Logic đăng ký/đăng nhập: [`AccountService`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/account/application/AccountService.java).

---

## 17. Giải thích AD-26: Cơ chế tạo Chiến lược bằng AI (LLM) thay vì sinh Code

**Vấn đề (Bối cảnh):**
Đề bài yêu cầu hệ thống phải tích hợp Trí tuệ nhân tạo (AI/LLM) để người dùng có thể gõ ngôn ngữ tự nhiên (hoặc thả 1 link bài báo), sau đó AI sẽ tự động biến nó thành một Chiến lược giao dịch (Strategy Authoring). 
Cách làm dễ nhất là gọi API của AI và yêu cầu: *"Hãy viết một file code Java hoàn chỉnh thực thi chiến lược này"*. Sau đó hệ thống sẽ chạy đoạn code đó (Dynamic Execution). 
Tuy nhiên, trong một hệ thống tài chính, đây là một lỗ hổng bảo mật cực kỳ nghiêm trọng (RCE - Remote Code Execution). Nếu AI bị hacker đánh lừa (Prompt Injection) sinh ra một đoạn code chứa lệnh xóa Database hoặc ăn cắp dữ liệu, toàn bộ Server sẽ bị vô hiệu hóa.

**Giải pháp (Quyết định):**
Hệ thống áp dụng chính sách **"Không Code Thực Thi" (No Executable Code)**. AI bị khóa chặt quyền hạn:
- **Giới hạn Output:** AI chỉ được phép trả về một chuỗi văn bản định dạng **JSON** theo đúng bộ khung (Schema) mà Server định sẵn.
- **Chỉ sử dụng công cụ có sẵn (Registry):** File JSON này không hề chứa logic lập trình. Nó chỉ chứa các thông số cấu hình của những Chỉ báo (Indicators) đã được hệ thống phê duyệt từ trước. Ví dụ, AI chỉ có quyền nói: *"Dùng SMA chu kỳ 50 kết hợp với RSI chu kỳ 14"*. Nó tuyệt đối không được phép tự định nghĩa công thức mới.
- **Smoke Test (Chạy thử nghiệm):** Sau khi nhận JSON, hệ thống sẽ dịch nó vào các Class Java an toàn có sẵn. Tiếp theo, nó cho chiến lược này "chạy thử" (Smoke Test) với 250 cây nến mặc định. Nếu chiến lược bị lỗi, hệ thống sẽ ném lỗi lại cho AI và ép nó sửa. Nếu quá 3 lần vẫn lỗi, hệ thống hủy bỏ luôn tác vụ để chống lặp vô hạn.

**Hệ quả và Mức độ đáp ứng đề bài:**
- **An toàn tuyệt đối:** Dù AI có "ảo giác" (hallucinate) hay bị hack, nó cũng không thể chạy bất kỳ mã độc nào trên Server.
- Chấp nhận đánh đổi sự sáng tạo vô biên (người dùng không thể tự chế ra thuật toán lạ) để đổi lấy sự an toàn tuyệt đối cho kiến trúc Enterprise. Tính năng tạo chiến lược bằng AI vẫn hoạt động trơn tru theo yêu cầu của Đề bài.

**📍 Code tham chiếu:**
- Toàn bộ luồng giao tiếp với AI, giới hạn JSON, và Smoke Test nằm gọn trong class [`StrategyAuthoringService`](../crypto-strategy-lab/core/src/main/java/com/cryptolab/strategy/application/StrategyAuthoringService.java).
