# Báo cáo toàn bộ project Crypto Strategy Lab

## 1. Đọc phần nào trước?

Nếu bạn mới học Java hoặc Software Architecture, hãy đọc theo thứ tự:

1. Phần 2 để hiểu project làm gì.   
2. Phần 3 để biết các thuật ngữ quan trọng.
3. Phần 4 và 5 để hiểu sơ đồ và các module.
4. Phần 7 để theo dõi một luồng chạy hoàn chỉnh.
5. Phần 16 để tự chạy và kiểm thử project.
6. Sau đó mới đọc các phần chi tiết về outbox, RabbitMQ, worker, idempotency và provenance.

Tài liệu gốc có giá trị cao nhất vẫn là [FEATURE_SPEC.md](FEATURE_SPEC.md). Nếu báo cáo này và specification mâu thuẫn nhau thì phải theo `FEATURE_SPEC.md`.

Thứ tự triển khai được phê duyệt nằm trong [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md). Bảng liên kết yêu cầu với bằng chứng nằm trong [docs/REQUIREMENTS_TRACEABILITY.md](docs/REQUIREMENTS_TRACEABILITY.md).

---

## 2. Project này làm gì?

Crypto Strategy Lab là một phòng thí nghiệm chiến lược giao dịch tiền mã hóa.

Người dùng có thể:

- Lấy dữ liệu nến lịch sử và thời gian thực từ Binance.
- Chọn hoặc kết hợp các chiến lược như MA, RSI, Bollinger Bands, Support/Resistance và MACD.
- Sinh nhiều cấu hình chiến lược bằng thuật toán Random hoặc Genetic.
- Chạy backtest trên dữ liệu quá khứ.
- Tính hiệu quả và rủi ro của từng kết quả.
- Xếp hạng các chiến lược trên leaderboard.
- Mở chiến lược hạng nhất và xem toàn bộ nguồn gốc dữ liệu, cấu hình và phiên bản dùng để tạo ra kết quả đó.
- Xem tin tức crypto và sentiment được phân tích từ tin tức.
- Theo dõi trạng thái các module, tiến độ search và kết quả gần như thời gian thực.

Project **không**:

- Đặt lệnh mua bán thật.
- Quản lý ví hoặc tiền của người dùng.
- Cam kết chiến lược có lợi nhuận trong tương lai.
- Là hệ thống High-Frequency Trading.
- Dùng Kubernetes, Service Mesh, Kafka, Event Sourcing hoặc một hệ microservice đầy đủ.

Một cách hình dung đơn giản:

```text
Dữ liệu thị trường
        ↓
Sinh ứng viên chiến lược
        ↓
Backtest từng ứng viên
        ↓
Đánh giá hiệu quả và rủi ro
        ↓
Xếp hạng
        ↓
Hiển thị leaderboard + bằng chứng tái lập
```

---

## 3. Các khái niệm nền tảng

### 3.1 Candle là gì?

Một candle, hay cây nến, tóm tắt biến động giá trong một khoảng thời gian. Ví dụ nến `5m` đại diện cho 5 phút và có:

- `open`: giá mở đầu.
- `high`: giá cao nhất.
- `low`: giá thấp nhất.
- `close`: giá cuối khoảng thời gian.
- `volume`: khối lượng giao dịch.
- `openTime` và `closeTime`: thời gian bắt đầu và kết thúc.

Project chuẩn hóa dữ liệu Binance thành đối tượng domain `Candle`. Vì vậy phần lõi không cần biết JSON riêng của Binance trông như thế nào.

### 3.2 Strategy là gì?

Strategy là một quy tắc nhận dữ liệu nến và đưa ra tín hiệu:

- `BUY`: nên mua.
- `SELL`: nên bán.
- `HOLD`: chưa làm gì.

Ví dụ, Moving Average có thể phát tín hiệu BUY khi đường trung bình ngắn hạn vượt đường trung bình dài hạn.

### 3.3 Backtest là gì?

Backtest là giả lập một chiến lược trên dữ liệu quá khứ. Nó trả lời câu hỏi: “Nếu ta dùng chiến lược này trong giai đoạn đã biết thì chuyện gì xảy ra?”

Backtest chỉ là bằng chứng trên quá khứ, không phải bảo đảm lợi nhuận tương lai.

### 3.4 Module là gì?

Module là một phần lớn của project có trách nhiệm rõ ràng. Project có các Maven module: `core`, `infrastructure`, `api-app`, `worker-app` và `integration-tests`.

### 3.5 Domain là gì?

Domain là các khái niệm của bài toán thực tế, ví dụ `Candle`, `Strategy`, `Experiment`, `Trade`, `NewsItem`. Domain không nên bị phụ thuộc vào chi tiết web, database hay RabbitMQ.

### 3.6 Port và Adapter là gì?

Hãy hình dung port là một ổ cắm tiêu chuẩn, adapter là đầu chuyển phù hợp với thiết bị cụ thể.

- `MarketDataProvider` là port: core chỉ yêu cầu “hãy cung cấp nến”.
- Binance adapter là adapter: nó biết cách gọi Binance và đổi dữ liệu Binance thành `Candle`.
- `NewsProvider` là port.
- CryptoCompare adapter là adapter tương ứng.

Nhờ vậy có thể thay nhà cung cấp bên ngoài mà không sửa business logic.

### 3.7 DTO là gì?

DTO là cấu trúc dùng để vận chuyển dữ liệu giữa các biên hệ thống. Binance có định dạng riêng, nhưng DTO Binance bị giữ bên trong adapter. Đây là lý do yêu cầu “Binance DTO không được rò rỉ ra ngoài adapter” rất quan trọng.

### 3.8 Transaction là gì?

Transaction là nhóm thao tác database hoặc thành công cùng nhau, hoặc thất bại cùng nhau.

Ví dụ, khi worker hoàn tất backtest, hệ thống phải lưu signals, trades, metrics, trạng thái `COMPLETED` và sự kiện hoàn tất trong cùng một transaction. Không được để trạng thái hoàn tất nhưng lại thiếu metrics.

### 3.9 Idempotency là gì?

Một thao tác idempotent có thể chạy lại nhiều lần nhưng kết quả cuối không bị nhân đôi.

RabbitMQ có thể giao cùng một message hơn một lần. Project dùng `experimentId`, unique constraint và bảng `processed_events` để đảm bảo một experiment không sinh hai bộ kết quả hoặc hai dòng leaderboard.

### 3.10 Outbox là gì?

Outbox là một bảng database chứa “ý định gửi message”. Business data và outbox được lưu trong cùng transaction. Một relay riêng đọc outbox rồi gửi message tới RabbitMQ.

Điểm quan trọng: ghi database thành công không có nghĩa RabbitMQ đã nhận message. Vì thế job vẫn là `PENDING_DISPATCH`; chỉ chuyển thành `QUEUED` sau khi broker xác nhận.

### 3.11 WebSocket là gì?

REST thường là trình duyệt hỏi rồi server trả lời một lần. WebSocket giữ kết nối mở để server chủ động đẩy tiến độ search, leaderboard và candle mới cho trình duyệt.

Project dùng STOMP trên endpoint `/ws`.

### 3.12 Provenance là gì?

Provenance là hồ sơ nguồn gốc của một kết quả. Nó trả lời:

- Dữ liệu nào đã được dùng?
- Checksum dữ liệu là gì?
- Strategy và tham số nào?
- Seed của generator là gì?
- Quy tắc khớp lệnh nào?
- Phiên bản engine, evaluator, source code và build nào?

Nếu thiếu provenance, một con số đẹp trên leaderboard không đủ đáng tin vì không thể tái lập.

---

## 4. Kiến trúc tổng thể

Project chọn **modular monolith kết hợp worker process độc lập**.

“Modular monolith” nghĩa là business code được chia module rõ ràng nhưng chưa tách thành hàng loạt microservice. Đây là lựa chọn phù hợp cho đồ án đại học: đủ để chứng minh ranh giới kiến trúc, nhưng vẫn dễ chạy, debug và giải thích.

```text
                      Người dùng / Browser
                               │
                    REST + WebSocket/STOMP
                               │
                         ┌─────▼─────┐
                         │  api-app  │
                         └─────┬─────┘
                               │
                         infrastructure
                     ┌─────────┼──────────┐
                     │         │          │
                 PostgreSQL RabbitMQ  Binance/CryptoCompare
                     │         │
                     │   ┌─────▼──────┐
                     └───┤ worker-app │
                         └─────┬──────┘
                               │
                              core
```

Hướng phụ thuộc bắt buộc:

```text
api-app / worker-app -> infrastructure -> core
integration-tests    -> api-app / worker-app (chỉ trong test scope)
```

Ý nghĩa quan trọng nhất là mũi tên chỉ đi về phía `core`. `core` không được import hoặc biết đến:

- Spring MVC.
- JPA.
- RabbitMQ.
- Class riêng của Binance.

Các luật này không chỉ nằm trong tài liệu mà còn được kiểm tra bằng Maven Enforcer và ArchUnit.

---

## 5. Năm Maven module

### 5.1 `core`

`core` chứa phần ổn định nhất:

- Domain model.
- Business rules.
- Application services.
- Các port để bên ngoài triển khai.

Ví dụ:

- `marketdata/domain/Candle.java`.
- `strategy/domain/Strategy.java`.
- `experiment/application/DeterministicBacktestEngine.java`.
- `news/port/NewsProvider.java`.

Nếu đổi PostgreSQL thành một database khác hoặc đổi Binance thành một nguồn khác, business rule trong `core` không nên phải đổi.

### 5.2 `infrastructure`

`infrastructure` chứa chi tiết kỹ thuật:

- JDBC/PostgreSQL repository.
- Flyway migration.
- RabbitMQ publisher, listener support và topology.
- Binance REST/WebSocket adapter.
- CryptoCompare adapter.
- Micrometer telemetry.
- Các Spring factory nối strategy plugin vào registry.

### 5.3 `api-app`

`api-app` là ứng dụng dành cho browser và client:

- REST controller.
- WebSocket/STOMP endpoint.
- Dashboard tĩnh.
- Spring Boot configuration.
- Health endpoint và system status.

### 5.4 `worker-app`

`worker-app` là process chạy backtest:

- Nhận job từ RabbitMQ.
- Claim job bằng lease.
- Chạy backtest.
- Commit kết quả.
- ACK message sau khi commit.

Worker không mở cổng HTTP riêng và có thể scale nhiều replica từ cùng một image.

### 5.5 `integration-tests`

Module này kiểm tra hành vi xuyên nhiều module với PostgreSQL và RabbitMQ thật trong Testcontainers. Đây là bằng chứng mạnh hơn unit test vì nó kiểm tra migration, constraint, transaction, broker ACK, duplicate delivery và DLQ.

---

## 6. Chia package theo feature

Trong từng module, code được nhóm theo bài toán thay vì nhóm thuần kỹ thuật:

```text
marketdata   dữ liệu giá và candle
strategy     chiến lược và cách kết hợp tín hiệu
experiment   candidate, backtest, evaluate, rank, search, worker
news         news và sentiment
shared       hợp đồng dùng chung thật sự
```

Cách chia này giúp một sinh viên khi sửa News có thể tìm phần lớn code trong package `news`, thay vì phải đi qua các thư mục chung như `controllers`, `services`, `repositories` của toàn hệ thống.

---

## 7. Luồng hoàn chỉnh từ browser đến leaderboard

### Bước 1: lấy dữ liệu thị trường

Dashboard gọi:

```text
GET /api/v1/market/candles?symbol=BTCUSDT&timeframe=5m&limit=100
```

Binance adapter gọi REST của Binance, parse dữ liệu riêng của Binance rồi trả về các `Candle` chuẩn hóa.

### Bước 2: đóng băng dataset

Trước khi search, dashboard gửi snapshot candle hiện tại đến:

```text
POST /api/v1/datasets
```

Hệ thống tạo checksum cho dataset. Dataset đã dùng cho experiment là bất biến. Điều này ngăn việc cùng một experiment hôm nay và ngày mai vô tình dùng hai bộ dữ liệu khác nhau.

### Bước 3: tạo SearchRun

Dashboard gửi:

```text
POST /api/v1/search-runs?generator=random
```

hoặc:

```text
POST /api/v1/search-runs?generator=genetic
```

Request chứa dataset, strategy version, parameter space, random seed, combination policy, batch size và điều kiện dừng.

API trả `202 Accepted` vì search là công việc bất đồng bộ, không nên giữ HTTP request mở tới khi mọi backtest xong.

### Bước 4: sinh candidate theo batch

Generator sinh candidate theo stream và từng batch giới hạn. Nó không tạo toàn bộ không gian tìm kiếm trong RAM.

Mỗi candidate có canonical hash. Hai cấu hình giống nhau sẽ có cùng định danh logic, giúp chống trùng.

### Bước 5: lưu ý định dispatch

Trong một transaction, hệ thống lưu:

- Candidate.
- Experiment ở trạng thái `CREATED`.
- Backtest job ở `PENDING_DISPATCH`.
- Outbox row yêu cầu gửi job.

Lúc này hệ thống **không được báo job là `QUEUED`**.

### Bước 6: publisher gửi job tới RabbitMQ

Outbox relay gửi message bền vững tới queue `crypto.backtest.jobs`.

Chỉ khi RabbitMQ publisher confirm thành công, database mới đổi job và experiment sang `QUEUED`. Nếu broker lỗi, trạng thái vẫn phản ánh trung thực là đang chờ dispatch.

### Bước 7: worker xử lý job

Worker dùng manual acknowledgment:

1. Nhận message nhưng chưa ACK.
2. Atomic claim job và gắn lease.
3. Chạy backtest.
4. Ghi kết quả và completion outbox trong transaction.
5. Commit database.
6. Sau đó mới ACK RabbitMQ.

Nếu worker chết trước commit, message có thể giao lại. Nếu chết sau commit nhưng trước ACK, message cũng có thể giao lại. Idempotency theo `experimentId` làm cho lần giao lại không tạo kết quả trùng.

Job lỗi tạm thời được retry tối đa 3 lần. Message độc hại hoặc hết retry được đưa vào Dead Letter Queue (DLQ).

### Bước 8: evaluate và rank bất đồng bộ

Worker hoàn tất backtest sẽ tạo event `BacktestCompleted`. Consumer khác nhận event này để:

- Tính evaluation.
- Tạo `StrategyEvaluated`.
- Cập nhật leaderboard.
- Tạo `LeaderboardUpdated`.

Bảng `processed_events` chống xử lý trùng. Vì vậy cùng một event giao hai lần vẫn không tạo hai dòng leaderboard.

### Bước 9: dashboard nhận tiến độ

Browser đăng ký:

```text
/topic/search/{searchRunId}
/topic/leaderboard/{searchRunId}
```

Tiến độ và leaderboard mới được đẩy qua WebSocket mà không phải tải lại toàn trang.

### Bước 10: mở Top #1

Từ `experimentId` của hàng hạng nhất, dashboard gọi:

```text
GET /api/v1/experiments/{experimentId}
GET /api/v1/experiments/{experimentId}/provenance
```

Người dùng xem được signals, trades, metrics và hồ sơ tái lập đầy đủ.

---

## 8. Market Data Walking Skeleton

### 8.1 Historical và realtime

Historical candle được lấy qua REST. Realtime candle được nhận qua Binance WebSocket, nhưng chỉ candle đã đóng mới trở thành dữ liệu xác nhận dùng ổn định.

### 8.2 Reconnect

Kết nối mạng có thể mất. Service sử dụng bounded exponential backoff: thử lại với khoảng chờ tăng dần nhưng có giới hạn, thay vì lặp cực nhanh làm quá tải server.

### 8.3 Gap recovery

Trong lúc mất kết nối có thể thiếu candle. Sau khi reconnect, hệ thống gọi historical API để lấp đoạn trống, kể cả candle ở biên. Candle trùng không gây lỗi vì database và service đều có duplicate protection.

### 8.4 Khóa chống trùng

Danh tính một candle trong database là:

```text
(symbol, timeframe, open_time)
```

Nếu cùng candle được nhận lại, `ON CONFLICT DO NOTHING` khiến thao tác an toàn.

### 8.5 Đổi timeframe không reload module khác

Khi người dùng đổi từ `5m` sang `15m`, dashboard chỉ:

- Hủy subscription market cũ.
- Fetch candle của timeframe mới.
- Đăng ký topic market mới.

Search, News, leaderboard và các panel không liên quan không bị reload.

---

## 9. Strategy Plugin

### 9.1 Hợp đồng plugin

Các điểm mở rộng chính là:

- `Strategy`: thực thi quy tắc tạo signal.
- `StrategyFactory`: kiểm tra config và tạo Strategy.
- `StrategyRegistry`: tìm factory theo `type` và `version`.

Controller chỉ đọc registry. Backtester chỉ gọi interface Strategy. Database lưu snapshot JSONB linh hoạt.

Do đó thêm một strategy mới không cần sửa:

- Backtester.
- Evaluator.
- Ranking.
- Controller.
- Database schema.

### 9.2 Các strategy hiện có

- `MA@1.0`: Moving Average.
- `RSI@1.0`: Relative Strength Index.
- `BB@1.0`: Bollinger Bands.
- `SR@1.0`: Support/Resistance.
- `MACD@1.0`: Moving Average Convergence Divergence, được thêm như bằng chứng mở rộng ở M7.

### 9.3 Combination policies

`MajorityVotePolicy` quy đổi:

```text
BUY  = +1
HOLD =  0
SELL = -1
```

Tổng dương là BUY, tổng âm là SELL, bằng 0 là HOLD.

`WeightedVotePolicy` nhân mỗi tín hiệu với trọng số. Mặc định dùng ngưỡng `+0.10` và `-0.10` để tránh phản ứng với tổng điểm quá nhỏ.

---

## 10. Experiment Pipeline và chống look-ahead bias

Pipeline bắt buộc:

```text
Candidate -> Backtest -> Evaluate -> Rank
```

### 10.1 Quy tắc không nhìn tương lai

Khi strategy tính signal tại candle N, nó chỉ được thấy prefix từ candle đầu tiên đến N. Signal sau khi candle N đóng được khớp tại giá mở cửa của candle N+1.

```text
Candle N đóng -> tạo signal -> Candle N+1 mở -> khớp lệnh
```

Nếu dùng giá đóng cửa N để vừa quan sát vừa khớp lệnh tại đúng thời điểm đó, thuật toán đã dùng thông tin không thực sự có sẵn và kết quả sẽ đẹp giả tạo.

### 10.2 Quy tắc backtest mặc định

- Vốn ban đầu: `10000`.
- Phí giao dịch: `0.001`.
- Không short sell.
- Execution model: `NEXT_CANDLE_OPEN`.
- Vị thế còn mở cuối dataset được định giá bằng cách thanh lý tại close của candle cuối.

Các số tiền và giá dùng `BigDecimal` để tránh sai số kiểu `double` trong tính toán tài chính.

### 10.3 Metrics và điểm số

Project lưu ít nhất:

- Total return.
- Maximum drawdown.
- Trade count.
- Score.

Công thức score hiện tại:

```text
score = return - 0.5 × abs(maxDrawdown)
```

Nếu hai kết quả bằng điểm, ranking dùng các tie-break ổn định để cùng input luôn cho cùng thứ tự.

---

## 11. Random, Genetic và điều kiện dừng

### 11.1 RandomStrategyGenerator

Random ở đây là pseudo-random có seed. Cùng dataset, parameter space, version và seed sẽ tạo cùng thứ tự candidate. Đây là “ngẫu nhiên tái lập được”.

### 11.2 GeneticStrategyGenerator

Genetic generator mô phỏng ý tưởng tiến hóa:

1. Tạo quần thể ban đầu.
2. Đánh giá candidate.
3. Chọn candidate phù hợp.
4. Crossover tham số.
5. Mutation một số tham số.
6. Tạo thế hệ tiếp theo.

Nó vẫn trả về cùng hợp đồng `CandidateStrategy`, nên downstream không biết candidate đến từ Random hay Genetic.

### 11.3 Điều kiện dừng

Search không được chạy vô hạn. Các điều kiện gồm:

- Đạt `maxCandidates`.
- Vượt `maxDuration`.
- Không cải thiện sau số iteration quy định.
- Hết parameter space.
- Người dùng cancel.

Trạng thái SearchRun:

```text
CREATED -> RUNNING -> COMPLETED
                   -> CANCELLED
                   -> FAILED
```

Cancel sẽ đánh dấu run terminal, hủy job chưa bắt đầu, hủy experiment tương ứng và tombstone outbox chưa publish. Job đã chạy cần kết thúc theo quy tắc an toàn, không để sinh artifact trái với semantics cancellation.

---

## 12. RabbitMQ, worker và độ tin cậy

### 12.1 Tại sao cần RabbitMQ?

Backtest có thể tốn thời gian. Nếu API tự làm mọi backtest thì HTTP server dễ nghẽn. RabbitMQ tách việc nhận yêu cầu khỏi việc xử lý nặng.

### 12.2 Queue chính và DLQ

- Queue chính: `crypto.backtest.jobs`.
- DLQ: `crypto.backtest.jobs.dlq`.

Message không thể xử lý an toàn sau retry hoặc message sai định dạng sẽ vào DLQ để quan sát và xử lý thủ công.

### 12.3 Atomic claim và lease

Khi có nhiều worker, chỉ một worker được sở hữu job tại một thời điểm. Database update có điều kiện thực hiện atomic claim.

Lease là thời hạn sở hữu. Nếu worker chết, lease hết hạn và worker khác có thể lấy lại job. Không có lease thì job có thể mắc kẹt vĩnh viễn ở `RUNNING`.

### 12.4 Commit before ACK

Thứ tự đúng là:

```text
DB commit thành công -> RabbitMQ ACK
```

Không được ACK trước commit. Nếu ACK trước rồi database lỗi, RabbitMQ tưởng công việc xong và message có thể mất.

### 12.5 At-least-once và chống trùng

RabbitMQ được dùng theo tinh thần at-least-once: ưu tiên không mất việc, chấp nhận message có thể đến lại. Hệ thống chống trùng ở consumer và database.

Đây là tư duy quan trọng: trong hệ phân tán, “giao đúng một lần tuyệt đối” rất khó; “có thể giao lại nhưng xử lý idempotent” thường đơn giản và đáng tin hơn.

### 12.6 Scale worker từ 1 lên 3

Worker không có fixed `container_name` và không publish host port. Scale chỉ là thay đổi deployment:

```bash
docker compose up -d --scale worker=3
```

Không sửa core, không build một loại source khác, và nhiều worker cùng cạnh tranh an toàn trên một queue.

---

## 13. News và Sentiment

### 13.1 Luồng xử lý

```text
CryptoCompare
    ↓
CryptoCompareNewsProvider
    ↓ chuẩn hóa
NewsItem
    ↓ lưu PostgreSQL
SentimentAnalyzer
    ↓
SentimentResult có version
    ↓
Dashboard News + Sentiment
```

`NewsCollector` trong core chỉ phụ thuộc các port:

- `NewsProvider`.
- `NewsStore`.
- `SentimentAnalyzer`.
- `NewsTelemetry`.

### 13.2 Sentiment hiện tại là gì?

Project dùng `DeterministicKeywordSentimentAnalyzer`, model family/version được ghi trung thực là `keyword`/`keyword-v1`. Đây là bộ phân tích từ khóa tất định, **không phải FinBERT hay AI model cao cấp**.

Việc ghi đúng model version quan trọng hơn việc đặt tên nghe ấn tượng. Provenance phải nói đúng hệ thống thực sự đã chạy gì.

### 13.3 Failure isolation

Nếu CryptoCompare timeout, trả lỗi, hoặc sentiment analyzer hỏng:

- Market vẫn hoạt động.
- Search vẫn hoạt động.
- Worker/backtest vẫn hoạt động.
- Leaderboard vẫn hoạt động.
- News/Sentiment có health và metric riêng để báo degraded/down.
- Tin đã lưu trước đó vẫn có thể đọc.

News có executor, timeout, health và telemetry riêng. Vì thế lỗi News không lan vào critical path của experiment.

### 13.4 Hai lỗi News được phát hiện trong session này

#### Lỗi thứ nhất: hiểu sai mã thành công của CryptoCompare

CryptoCompare trả:

```json
{
  "Type": 100,
  "Message": "News list successfully returned",
  "Data": ["..."]
}
```

Adapter cũ lại xem `Type=100` là lỗi. API key hợp lệ và provider trả dữ liệu, nhưng application tự từ chối response.

Cách sửa: chỉ chấp nhận response thành công khi `Type` bằng `100` và `Data` là một array. Các response khác tiếp tục được xử lý như lỗi provider.

#### Lỗi thứ hai: cột database quá ngắn

Sau khi sửa parser, hệ thống fetch được tin nhưng lưu thất bại vì:

```text
value too long for varchar(64)
```

`inputVersion` có dạng:

```text
sha256:<64 ký tự hex>
```

Tổng chiều dài là 71 ký tự, trong khi schema cũ chỉ cho `varchar(64)`.

Cách sửa đúng là thêm Flyway migration `V8__expand_news_input_version.sql`, tăng hai cột `news_items.input_version` và `sentiment_predictions.input_version` lên `varchar(128)`. Không sửa database thủ công vì môi trường khác sẽ không nhận được thay đổi.

### 13.5 Cách cấu hình API key an toàn

Không ghi key trực tiếp vào `docker-compose.yml` và không commit key lên Git.

```bash
cp .env.example .env
```

Sau đó sửa file `.env`:

```dotenv
NEWS_API_KEY=YOUR_NEW_CRYPTOCOMPARE_KEY
```

Compose truyền key qua environment và adapter gửi key trong HTTP header:

```text
Authorization: Apikey <key>
```

API key từng xuất hiện trực tiếp trong cấu hình/session trước đó nên phải được thu hồi và tạo key mới. Báo cáo này không chứa key thật.

---

## 14. Database và Flyway

Mọi thay đổi schema đều đi qua Flyway để database mới và database cũ có thể tiến tới cùng một trạng thái.

| Migration | Nội dung chính |
|---|---|
| V1 | Schema nền tảng: market, strategy, experiment, news |
| V2 | SearchRun progress và stop information |
| V3 | Backtest dispatch outbox |
| V4 | Worker execution, retry, lease |
| V5 | Constraint cho async event projection |
| V6 | Cancellation và tombstone outbox |
| V7 | Danh tính/constraint sentiment prediction |
| V8 | Mở rộng News/Sentiment `input_version` lên 128 ký tự |

Một số loại dữ liệu quan trọng được lưu:

- Candle và immutable market dataset.
- Candidate và canonical hash.
- SearchRun, progress và stop reason.
- Experiment và trạng thái.
- Recorded signals, trades, metrics.
- Leaderboard projection.
- Job, retry, worker, lease.
- Outbox events và processed events.
- News items và sentiment predictions.
- Provenance snapshot.

Strategy config dùng JSONB versioned snapshot. Nhờ vậy thêm MACD không phải thêm các cột như `macd_fast`, `macd_slow` vào schema.

---

## 15. Dashboard và observability

Dashboard tại `http://localhost:8080` có bảy khu vực:

1. Market.
2. Strategy.
3. Search.
4. Leaderboard.
5. Experiment Details/Provenance.
6. News/Sentiment.
7. System Status.

Dashboard không dùng dữ liệu giả để tạo cảm giác hệ thống đang chạy. Nó đọc REST và WebSocket contracts của backend.

Các endpoint quan sát chính:

```text
GET /api/v1/system/status
GET /actuator/health
GET /actuator/health/marketData
GET /actuator/health/newsProvider
GET /actuator/health/sentimentAnalyzer
GET /actuator/metrics
```

Metric bao gồm:

- Search đang active.
- Số candidate đã sinh.
- Queue depth.
- Job start/completion/failure/duration/duplicate.
- Outbox backlog.
- Market reconnect, gap recovery và UI latency.
- News collection failure.
- Sentiment inference failure và duration.

Log mang các định danh như `correlationId`, `searchRunId`, `jobId`, `experimentId`, `eventId` để lần theo một luồng xuyên nhiều process.

Lưu ý: một aggregate `/actuator/health` có thể trả HTTP 503 nếu một component tùy chọn như News đang DOWN. Điều đó không tự động có nghĩa Market hay Search cũng hỏng; hãy đọc `/api/v1/system/status` và từng health component riêng.

---

## 16. Những việc đã được thực hiện và kiểm chứng trong session này

Phần này phân biệt rõ giữa “code được thiết kế để làm” và “đã chạy thật trên localhost”.

### 16.1 Kiểm tra dashboard và Market

- Dashboard `/` trả HTTP 200.
- Đủ bảy panel bắt buộc.
- Lấy thật 100 candle `BTCUSDT`, timeframe `5m` từ Binance.
- Tạo immutable dataset version `localhost-e2e-v1`.
- Dataset checksum: `1dc9d752e011d2979fb9ed38a24e8702de4464cae73256761a15143427a37fb0`.
- Khoảng dữ liệu: `2026-08-18T01:40:00Z` đến `2026-08-18T10:00:00Z`.

### 16.2 Search Random end-to-end

- `searchRunId`: `7efba6ec-06d2-4905-a131-dc93eec96ad7`.
- Không gian tham số MA gồm 2 × 2 lựa chọn.
- Sinh và hoàn tất 4 experiment duy nhất.
- `bestScore`: `-0.87405514`.
- Stop reason: `SOURCE_EXHAUSTED`.

Score âm không có nghĩa hệ thống lỗi. Nó chỉ cho biết những cấu hình được thử đã không tốt trên dataset cụ thể đó.

### 16.3 Search Genetic end-to-end

- `searchRunId`: `a20ecd88-38c7-46ee-9178-1fed18af49f2`.
- Generator tạo 6 lượt candidate và persistence hoàn tất 4 candidate duy nhất.
- Stop reason: `MAX_CANDIDATES`.

Số lượt generate có thể lớn hơn số candidate duy nhất vì genetic search có thể tạo lại cấu hình đã thấy; canonical hash và constraint chống lưu trùng.

### 16.4 Kiểm chứng Top #1 provenance

- Top experiment: `14494b80-baad-3b6d-a291-9448e5776b14`.
- Return: `-0.50730094`.
- Maximum drawdown: `-0.73350839`.
- Số trade: `2`.
- Score: `-0.87405514`.
- Strategy: `MA@1.0`, `fast=10`, `slow=30`.
- Execution: `NEXT_CANDLE_OPEN`.
- Engine: `deterministic-next-open-v1`.
- Generator: `random@1.0`, seed `42`.
- Evaluator: `return-minus-half-drawdown-v1`.
- Provenance nối được tới candidate hash, dataset version và checksum.

Giới hạn được phát hiện: `codeCommit` và `buildVersion` của lượt chạy này là `dev`. Để provenance production tốt hơn, cần truyền `APP_GIT_COMMIT` và `APP_BUILD_VERSION` thật khi build/deploy.

### 16.5 Kiểm chứng cancellation

- `searchRunId`: `16612183-c261-4200-a865-62f09b16a417`.
- Run chuyển sang `CANCELLED` ngay.
- Generated jobs: `0`.
- Stop reason: `USER_CANCELLED`.

### 16.6 Kiểm chứng scale worker 1 lên 3

Worker được scale bằng Docker Compose, không sửa source code.

- Workload run: `55b221f4-b92f-47bd-81a7-744af0f4ee4e`.
- 20 candidate được sinh, lưu và hoàn tất.
- 0 job failed.
- Phân phối thực tế: worker-3 xử lý 14, worker-4 xử lý 3, worker-5 xử lý 3.
- Database có 20 experiment hoàn tất duy nhất.
- 20 metrics duy nhất.
- 20 leaderboard entries duy nhất.
- Không có duplicate completion.

Sau test, môi trường đã được trả về một worker.

### 16.7 Kiểm chứng WebSocket và metrics

- WebSocket handshake trả HTTP 101 Switching Protocols.
- Các metric về search, candidate, worker, market reconnect/gap, News, Sentiment và outbox có mặt.

### 16.8 Kiểm chứng failure isolation của News

Trước khi sửa, CryptoCompare từng trả 401. Khi News DOWN:

- Market vẫn UP.
- Search vẫn UP.
- Queue vẫn UP.
- Sentiment component vẫn độc lập.

Điều này chứng minh lỗi provider không kéo sập các luồng chính.

### 16.9 Sửa News và kiểm chứng lại

Sau khi sửa parser, header API key và Flyway V8:

- Focused infrastructure tests: 73 tests, tất cả pass.
- `mvn clean verify`: cả 6 Maven reactor modules pass.
- Integration test module: 17 tests pass.
- Docker rebuild API thành công.
- Flyway áp dụng V8 thành công.
- `POST /api/v1/news/collect` trả HTTP 200.
- Fetch 50 bài, lưu 50 bài, phân tích 50 bài.
- `inferenceFailures = 0`.
- News health UP.
- Sentiment health UP.
- Database có 50 news items và 50 sentiment predictions.
- `GET /api/v1/news?limit=10` trả 10 item thật.

Một item đã quan sát:

- ID: `cryptocompare:69097246`.
- Provider: `Investing.Com Crypto Opinion and Analysis`.
- Tiêu đề: `Crypto Bears Remain in Control`.
- Thời điểm: `2026-08-18T10:49:48Z`.
- Sentiment: `NEUTRAL`.
- Score: `0`.
- Analyzer: `deterministic-keyword@keyword-v1`.

---

## 17. Source code đã thay đổi để sửa News

Các file được sửa:

- `.env.example`: thêm biến cấu hình an toàn cho News API key.
- `README.md`: cập nhật cách cấu hình News và giữ secret ngoài Git.
- `docker-compose.yml`: truyền `NEWS_API_KEY` từ environment thay vì hard-code.
- `api-app/src/main/resources/application.yml`: khai báo News API key riêng.
- `api-app/src/main/java/com/cryptolab/api/news/NewsRuntimeConfiguration.java`: nối config vào adapter.
- `infrastructure/src/main/java/com/cryptolab/infrastructure/news/adapter/cryptocompare/CryptoCompareNewsProvider.java`: sửa điều kiện response thành công và giữ DTO/provider logic trong adapter.
- `infrastructure/src/main/java/com/cryptolab/infrastructure/news/adapter/cryptocompare/JdkCryptoCompareTransport.java`: gửi key bằng `Authorization` header.
- `infrastructure/src/test/java/com/cryptolab/infrastructure/news/adapter/cryptocompare/CryptoCompareNewsProviderTest.java`: test `Type=100`, error response và mapping.
- `integration-tests/src/test/java/com/cryptolab/persistence/NewsSentimentIT.java`: kiểm tra persistence/idempotency.
- `integration-tests/src/test/java/com/cryptolab/persistence/PostgresqlMigrationIT.java`: kiểm tra schema sau migration.

Các file được tạo mới:

- `infrastructure/src/main/resources/db/migration/V8__expand_news_input_version.sql`.
- `infrastructure/src/test/java/com/cryptolab/infrastructure/news/adapter/cryptocompare/JdkCryptoCompareTransportTest.java`.
- `report.md` là tài liệu bạn đang đọc.

Không có business module khác bị sửa để “né” lỗi News. Điều này phù hợp yêu cầu failure isolation và dependency direction.

---

## 18. Cách chạy toàn bộ project

### 18.1 Yêu cầu

- Java 21.
- Docker Engine.
- Docker Compose.
- Git.

Project có Maven Wrapper nên không bắt buộc cài Maven toàn hệ thống.

### 18.2 Tạo cấu hình local

```bash
cp .env.example .env
```

Sửa `.env`, ít nhất thay password mẫu và News key:

```dotenv
POSTGRES_PASSWORD=YOUR_LOCAL_DATABASE_PASSWORD
RABBITMQ_PASSWORD=YOUR_LOCAL_RABBIT_PASSWORD
NEWS_API_KEY=YOUR_NEW_CRYPTOCOMPARE_KEY
APP_GIT_COMMIT=YOUR_GIT_COMMIT
APP_BUILD_VERSION=local
```

`.env` đã được gitignore. Không dùng API key cũ từng lộ trong cấu hình; hãy revoke/rotate key đó.

### 18.3 Chạy quality gate

```bash
./mvnw clean verify
```

Hoặc:

```bash
mvn clean verify
```

Testcontainers cần Docker đang chạy.

### 18.4 Chạy topology hoàn chỉnh

```bash
docker compose up --build
```

Mở:

```text
http://localhost:8080
```

### 18.5 Chạy ở background

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

Trong terminal khác có thể xem worker:

```bash
docker compose logs -f worker
```

### 18.6 Scale worker

```bash
docker compose up -d --scale worker=3
docker compose ps
```

Trả lại một worker:

```bash
docker compose up -d --scale worker=1
```

---

## 19. Cách test toàn bộ luồng bằng tay

### Bước 1: kiểm tra container

```bash
docker compose ps
```

Mong đợi PostgreSQL và RabbitMQ healthy, API chạy, ít nhất một worker chạy.

### Bước 2: kiểm tra dashboard và status

```bash
curl -i http://localhost:8080/
curl http://localhost:8080/api/v1/system/status
curl http://localhost:8080/actuator/health
```

### Bước 3: kiểm tra market thật

```bash
curl "http://localhost:8080/api/v1/market/candles?symbol=BTCUSDT&timeframe=5m&limit=100"
```

Phải thấy candle có timestamp và OHLCV thật, không phải dữ liệu demo hard-code.

### Bước 4: kiểm tra plugin discovery

```bash
curl http://localhost:8080/api/v1/strategies
```

Phải thấy MA, RSI, BB, SR và MACD cùng metadata/config schema.

### Bước 5: kiểm tra generator capabilities

```bash
curl http://localhost:8080/api/v1/search-runs/capabilities
```

Phải thấy Random và Genetic có thể chọn.

### Bước 6: chạy search từ dashboard

Đây là cách dễ và ít sai payload nhất:

1. Mở dashboard.
2. Chờ Market tải candle.
3. Chọn strategy và parameter space.
4. Chọn Random hoặc Genetic.
5. Đặt max candidates, batch size và stop conditions.
6. Bấm bắt đầu.
7. Theo dõi generated, pending dispatch, queued, running, completed và failed.

Dashboard sẽ materialize dataset trước rồi mới tạo SearchRun.

### Bước 7: kiểm tra leaderboard và provenance

Khi có kết quả, chọn hạng nhất. Kiểm tra:

- Experiment ID.
- Candidate hash.
- Dataset checksum.
- Strategy version và parameters.
- Signals và trades.
- Metrics.
- Engine/generator/evaluator version.
- Code commit/build version.

### Bước 8: kiểm tra News

```bash
curl -X POST http://localhost:8080/api/v1/news/collect
curl "http://localhost:8080/api/v1/news?limit=10"
curl http://localhost:8080/actuator/health/newsProvider
curl http://localhost:8080/actuator/health/sentimentAnalyzer
```

### Bước 9: kiểm tra scale

Scale lên ba worker, tạo một search đủ lớn rồi xem log và database/leaderboard. Kết quả đúng là workload được chia cho nhiều worker nhưng mỗi experiment chỉ có một completion/artifact set.

### Bước 10: chạy sáu architecture proofs

```bash
./scripts/verify-architecture-proofs.sh
```

Script kiểm tra:

1. Thêm MACD không sửa downstream.
2. Đổi Random sang Genetic.
3. Scale worker 1 lên 3 không trùng kết quả.
4. News failure không ảnh hưởng module khác.
5. Binance reconnect/gap recovery/duplicate protection.
6. Top #1 nối tới provenance đầy đủ.

---

## 20. Các lớp kiểm thử

### 20.1 Unit test

Unit test kiểm tra một class hoặc business rule nhỏ:

- Strategy phát signal đúng.
- Combination policy xử lý boundary đúng.
- Generator tất định theo seed.
- State machine từ chối transition sai.
- Backtester không nhìn tương lai.
- News parser hiểu `Type=100`.

### 20.2 Architecture test bằng ArchUnit

Architecture test kiểm tra dependency bằng code:

- Core không phụ thuộc Spring MVC/JPA/RabbitMQ/Binance.
- Controller không phụ thuộc concrete strategy.
- Generator không phụ thuộc Backtester/Evaluator/Ranking.
- API app không consume worker job.

Nếu một lập trình viên vô tình import class sai tầng, build sẽ fail.

### 20.3 Integration test

Integration test dùng Testcontainers để chạy PostgreSQL và RabbitMQ thật:

- Flyway từ database rỗng.
- Unique constraint và duplicate candle.
- Outbox confirm semantics.
- Atomic worker claim/lease.
- Manual ACK và duplicate delivery.
- Retry và DLQ.
- Processed-event deduplication.
- Cancellation race.
- News persistence/idempotency.
- Top #1 provenance.

### 20.4 Runtime test trên localhost

Runtime test xác nhận wiring, container, Internet/provider và browser API thực sự hoạt động. Nó bổ sung cho automated tests, không thay thế automated tests.

Một build xanh không tự chứng minh CryptoCompare key đang hợp lệ; ngược lại, một lần gọi API thành công cũng không chứng minh retry/idempotency đúng. Cần cả hai loại bằng chứng.

---

## 21. Cách debug lỗi thường gặp

### 21.1 Web không có News

Kiểm tra theo thứ tự:

```bash
docker compose exec api sh -c 'test -n "$NEWS_API_KEY" && echo configured || echo missing'
curl -X POST http://localhost:8080/api/v1/news/collect
curl http://localhost:8080/actuator/health/newsProvider
docker compose logs api
```

Không in giá trị key khi chụp màn hình hoặc gửi log công khai.

Các nguyên nhân phổ biến:

- `.env` chưa tồn tại.
- Sửa `.env` nhưng chưa recreate container API.
- Key hết hạn/sai quyền.
- Provider trả 401/429.
- Parser hiểu sai response contract.
- Database schema chưa migrate.
- Browser giữ bundle/cache cũ.

Sau khi đổi env:

```bash
docker compose up -d --build --force-recreate api
```

### 21.2 API báo 503 nhưng Market vẫn chạy

Đọc từng component:

```bash
curl http://localhost:8080/api/v1/system/status
curl http://localhost:8080/actuator/health/marketData
curl http://localhost:8080/actuator/health/newsProvider
```

Aggregate health có thể DOWN vì News, trong khi critical modules vẫn UP.

### 21.3 Job đứng ở PENDING_DISPATCH

Kiểm tra:

- RabbitMQ healthy chưa.
- Exchange/queue/binding tồn tại chưa.
- Publisher confirm có thành công không.
- Outbox retry count và next-attempt time.
- Log correlation/search/job ID.

Không nên sửa trạng thái thành QUEUED bằng tay vì như vậy sẽ nói sai sự thật về broker confirmation.

### 21.4 Job QUEUED nhưng không hoàn tất

Kiểm tra:

- Có worker đang chạy không.
- Worker có kết nối đúng RabbitMQ và PostgreSQL không.
- Job đang có lease bởi worker nào.
- Retry count và last error.
- Message có vào DLQ không.

### 21.5 Kết quả leaderboard bị trùng

Kiểm tra unique constraint theo experiment, `processed_events`, event ID và idempotent projection. Không chỉ sửa UI ẩn dòng trùng; phải sửa tại transaction/consumer/database boundary.

### 21.6 Flyway validation fail

Không sửa file migration cũ đã chạy ở database. Hãy tạo migration version mới. Flyway dùng checksum để phát hiện lịch sử schema bị thay đổi.

---

## 22. Mười Architecture Decision Records

Các quyết định lớn được ghi trong `docs/adr`:

1. Market Data adapter.
2. WebSocket realtime.
3. Strategy plugin registry.
4. Tách Backtester và Evaluator.
5. Queue và Worker.
6. Modular Monolith.
7. Provenance.
8. Tách News và Sentiment.
9. Không dùng full CQRS/Event Sourcing.
10. Stop conditions và observability.

ADR không chỉ nói “đã chọn gì”, mà nên nói cả bối cảnh, lựa chọn thay thế và hậu quả. Đây là bằng chứng rằng kiến trúc có lý do chứ không phải ghép công nghệ tùy hứng.

---

## 23. Event Catalog

Project mô tả chín domain event:

- `MarketPriceUpdated`.
- `CandleClosed`.
- `StrategyGenerated`.
- `BacktestStarted`.
- `BacktestCompleted`.
- `StrategyEvaluated`.
- `LeaderboardUpdated`.
- `NewsCollected`.
- `SentimentAnalyzed`.

Mỗi event có envelope gồm `eventId`, type, schema version, time, aggregate, correlation, causation, ordering key và payload.

Outbox không biến project thành Event Sourcing. PostgreSQL state vẫn là source of truth; event dùng để truyền thay đổi đáng tin cậy và leaderboard là projection có thể tái tạo.

Chi tiết nằm trong [docs/architecture/EVENT_CATALOG.md](docs/architecture/EVENT_CATALOG.md).

---

## 24. Lịch sử triển khai P0 đến M7

### P0 — Repository/Foundation

- Java 21 và Maven multi-module.
- Maven Wrapper, Enforcer, CI.
- `.gitignore` và `.env.example`.
- Dependency direction ban đầu.

### M1 — Architecture Skeleton

- Package-by-feature và bounded contexts.
- Core ports/domain không phụ thuộc framework.
- API và worker Spring Boot app độc lập.
- PostgreSQL, Flyway và schema ban đầu.
- ArchUnit, C4, ADR và event catalog.

### M2 — Market Data Walking Skeleton

- Binance historical + realtime.
- DTO isolation.
- Reconnect, gap recovery, duplicate protection.
- REST/STOMP market contract.
- Timeframe isolation.

### M3 — Strategy Plugin

- Strategy/Factory/Registry.
- MA, RSI, BB, SR.
- Majority và Weighted policies.
- Extensibility tests.

### M4 — Experiment Pipeline

- Candidate → Backtest → Evaluate → Rank.
- No look-ahead.
- Signals, trades, metrics.
- Immutable provenance và rerun.

### M5.1 — Search nền tảng

- Deterministic Random generator.
- SearchRun state machine.
- Stop/cancel.
- Streaming và bounded batches.

### M5.2 — Dispatch

- RabbitMQ topology.
- Transactional dispatch outbox.
- Confirm rồi mới QUEUED.

### M5.3 — Worker

- Manual ACK.
- Atomic claim/lease.
- Commit before ACK.
- Idempotency, retry tối đa 3, DLQ.

### M5.4 — Async completion

- Transactional completion outbox.
- Processed-events inbox/dedup.
- Async evaluation/ranking.
- Idempotent leaderboard.

### M5.5 — Genetic, realtime và scaling

- Genetic generator.
- Hoàn chỉnh cancellation semantics.
- Search/leaderboard WebSocket.
- Worker scaling proof.

### M6 — News + Sentiment

- Replaceable News provider và Sentiment analyzer.
- Persistence/idempotency/version.
- Failure isolation.

### M7 — Dashboard và Architecture Proof

- Dashboard backend-driven.
- MACD extension.
- Observability.
- Sáu proof bắt buộc.
- README, C4, ADR và traceability.

---

## 25. Những giới hạn và việc cần chú ý hiện tại

### 25.1 API key

Workspace hiện không có file `.env` được commit, đây là đúng về bảo mật. Tuy nhiên container từng được khởi động với key qua environment tạm thời. Khi recreate môi trường, News sẽ không hoạt động nếu chưa tạo `.env` chứa key mới.

Key cũ từng xuất hiện trong session/cấu hình phải được revoke và rotate.

### 25.2 Dữ liệu test còn trong PostgreSQL local

Các lượt runtime test đã tạo dataset, search run, experiment, metrics, leaderboard, news và sentiment thật trong database local. Chúng chưa được xóa vì xóa dữ liệu là thao tác phá hủy và không cần thiết để chứng minh tính năng.

Nếu cần demo sạch, nên tạo database/volume riêng thay vì xóa bừa volume đang có dữ liệu.

### 25.3 Provenance `dev`

Một số experiment runtime có `codeCommit=dev` và `buildVersion=dev`. Chức năng provenance hoạt động, nhưng metadata deployment chưa đủ mạnh. Nên truyền commit SHA và build version thật trong CI/Compose.

### 25.4 Mức độ kiểm chứng UI

Session đã kiểm tra HTTP 200, các panel, JavaScript/backend data flow và WebSocket handshake. Không có bằng chứng screenshot-driven cho mọi kích thước màn hình. Vì vậy không nên tuyên bố đã kiểm thử hoàn hảo về responsive/visual UX.

### 25.5 Binance disconnect thực tế

Reconnect, gap recovery và duplicate protection có automated test. Session không chủ động phá mạng Binance production để quay lại bằng chứng vật lý, vì thao tác đó khó kiểm soát. Đây là khác biệt giữa test mô phỏng có kiểm soát và sự cố mạng thật.

### 25.6 Sentiment đơn giản

Keyword analyzer phù hợp phạm vi đồ án và có version rõ ràng, nhưng chất lượng ngôn ngữ không thể so với một mô hình NLP chuyên sâu. Nếu nâng cấp, hãy thêm adapter/model version mới mà không thay hợp đồng core.

---

## 26. Những bài học kiến trúc quan trọng nhất

1. **Business rule phải độc lập công nghệ.** Core càng ít biết framework thì càng dễ test và thay adapter.
2. **Trạng thái phải nói đúng sự thật.** Job chưa được broker confirm thì không được báo QUEUED.
3. **Hệ phân tán phải giả định message có thể đến lại.** Idempotency quan trọng hơn hy vọng message chỉ đến đúng một lần.
4. **Commit trước ACK.** Đây là ranh giới chống mất công việc.
5. **Không nhìn tương lai trong backtest.** Nếu không, toàn bộ kết quả nghiên cứu mất giá trị.
6. **Kết quả phải tái lập.** Seed, dataset checksum, version và config đều phải được lưu.
7. **Lỗi module phụ không được kéo sập critical path.** News hỏng không có lý do làm Market hoặc Backtest hỏng.
8. **Schema phải tiến hóa bằng migration.** Không sửa database bằng tay rồi quên ghi lại.
9. **Extension point phải được chứng minh bằng thay đổi thật.** MACD và Genetic là bằng chứng plugin/generator design hoạt động.
10. **Test có nhiều tầng.** Unit test, architecture test, integration test và runtime test trả lời các câu hỏi khác nhau.
11. **Đơn giản và giải thích được tốt hơn phức tạp theo trào lưu.** Modular monolith + worker đủ cho bài toán này.
12. **Quan sát được là một phần của kiến trúc.** Health, metrics và correlation IDs giúp chứng minh cũng như vận hành hệ thống.

---

## 27. Checklist trước khi demo hoặc nộp bài

- [ ] Java 21 hoạt động.
- [ ] Docker đang chạy.
- [ ] API key cũ đã revoke.
- [ ] `.env` chứa key mới và không bị Git track.
- [ ] `APP_GIT_COMMIT` và `APP_BUILD_VERSION` có giá trị thật.
- [ ] `./mvnw clean verify` pass.
- [ ] `./scripts/verify-architecture-proofs.sh` pass.
- [ ] `docker compose up --build` khởi động được.
- [ ] PostgreSQL và RabbitMQ healthy.
- [ ] Dashboard có đủ bảy panel.
- [ ] Binance trả candle thật.
- [ ] Random search hoàn tất.
- [ ] Genetic search hoàn tất.
- [ ] Top #1 mở được provenance.
- [ ] News collect và sentiment hoạt động.
- [ ] Tắt/sai News provider không làm Market/Search hỏng.
- [ ] Scale worker 1 → 3 không tạo duplicate experiment result.
- [ ] Không có secret trong `git diff` hoặc file tracked.
- [ ] Không tuyên bố lợi nhuận hoặc giao dịch thật.

---

## 28. Tài liệu nên đọc tiếp

- [FEATURE_SPEC.md](FEATURE_SPEC.md): hợp đồng sản phẩm và kiến trúc chính.
- [README.md](README.md): cách build, chạy và gọi endpoint.
- [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md): thứ tự triển khai.
- [docs/REQUIREMENTS_TRACEABILITY.md](docs/REQUIREMENTS_TRACEABILITY.md): yêu cầu ↔ bằng chứng.
- [docs/architecture/C4_CONTEXT.md](docs/architecture/C4_CONTEXT.md): hệ thống trong bối cảnh người dùng và external systems.
- [docs/architecture/C4_CONTAINER.md](docs/architecture/C4_CONTAINER.md): API, worker, database, broker.
- [docs/architecture/DYNAMIC_VIEW.md](docs/architecture/DYNAMIC_VIEW.md): luồng động theo thời gian.
- [docs/architecture/EVENT_CATALOG.md](docs/architecture/EVENT_CATALOG.md): event contract.
- [docs/architecture/PROOF_MATRIX.md](docs/architecture/PROOF_MATRIX.md): sáu bằng chứng cuối.
- [docs/adr](docs/adr): lý do cho các quyết định kiến trúc.

---

## 29. Kết luận ngắn

Crypto Strategy Lab không chỉ là một trang hiển thị giá crypto. Giá trị kiến trúc của project nằm ở việc nó tạo một pipeline nghiên cứu có thể giải thích và tái lập:

```text
Dữ liệu bất biến
  + Strategy plugin thay thế được
  + Backtest không nhìn tương lai
  + Async job đáng tin cậy
  + Consumer chống trùng
  + Provenance đầy đủ
  + Failure isolation
  + Bằng chứng test tự động và runtime
```

Trong session này, toàn bộ luồng đã được kiểm tra trên localhost; worker đã được scale từ một lên ba; Random và Genetic search đã tạo kết quả; Top #1 đã được truy ngược về provenance; lỗi News đã được tìm ra ở cả adapter contract lẫn database schema, sửa bằng code/test/Flyway, rồi xác nhận lại với 50 bài tin và 50 sentiment predictions thật.

Điểm quan trọng nhất khi trình bày đồ án là không chỉ nói “hệ thống chạy được”, mà phải giải thích **vì sao nó vẫn đúng khi mạng lỗi, message giao trùng, worker chết, provider News hỏng, strategy mới được thêm, hoặc người dùng muốn tái lập một kết quả cũ**.
