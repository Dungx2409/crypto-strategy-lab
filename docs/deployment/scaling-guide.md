# Hướng dẫn Mở rộng Hệ thống (Scaling Guide)

Thiết kế tách rời **API** và **Worker** thông qua RabbitMQ mang lại cho Crypto Strategy Lab một khả năng mở rộng (Scalability) tuyệt vời theo chiều ngang (Horizontal Scaling).

## 1. Bài toán (The Problem)

Giả sử hệ thống đang chạy thuật toán **Genetic Search (Tìm kiếm di truyền)** để tìm ra tham số MACD tối ưu nhất. 
Quần thể có 1000 cá thể, sinh ra 1000 cấu hình chiến lược cần được chấm điểm (Evaluate).
Nếu chỉ có 1 `worker` duy nhất, nó sẽ phải kéo lần lượt 1000 Job từ RabbitMQ xuống để tính toán, có thể mất 30 phút.

## 2. Giải pháp Scale-out (Mở rộng chiều ngang)

Vì `worker` được thiết kế theo nguyên lý **Stateless** (Không lưu trạng thái trong bộ nhớ RAM, mà lấy từ Database và MQ), chúng ta có thể tạo ra bao nhiêu bản sao (Replica) của Worker tùy thích!

### Khởi chạy nhiều Worker trên Docker Compose
Thay vì dùng Docker Swarm hay Kubernetes phức tạp, ngay trên Docker Compose local, bạn có thể tăng số lượng Worker bằng lệnh `--scale`:

```bash
# Lệnh này sẽ bật 1 API, 1 Postgres, 1 RabbitMQ và 5 WORKER song song!
docker-compose up -d --scale worker=5
```

### Luồng hoạt động sau khi Scale
1. API (Điều phối viên) vẫn tiếp nhận yêu cầu và bắn 1000 Job vào hàng đợi (Queue) của RabbitMQ.
2. Lúc này, **5 con Worker** (Worker-1 đến Worker-5) sẽ cùng lúc tranh nhau lấy Job từ Queue.
3. Theo cơ chế **Round-Robin** hoặc **Fair Dispatch** của RabbitMQ, cứ mỗi Worker rảnh sẽ được giao 1 Job. 5 Worker cùng chạy thì thời gian chấm điểm 1000 chiến lược giảm từ 30 phút xuống chỉ còn khoảng **6 phút**.

## 3. Cơ chế Khóa (Locking & Claiming) an toàn
Khi chạy 5 Worker, có sợ trường hợp 2 Worker cùng xử lý 1 Job dẫn đến trùng lặp kết quả ghi vào Database không?
**Câu trả lời là KHÔNG, vì:**
- RabbitMQ đảm bảo mỗi thông điệp (Job) chỉ được gửi cho **DUY NHẤT một Worker** (Competing Consumers Pattern).
- Thêm vào đó, Worker có cơ chế `Database Claim/Lease`. Khi nhận Job, Worker sẽ cập nhật trạng thái trong Database là `IN_PROGRESS`. Các Worker khác dù có bằng cách nào đó nhận được trùng Job, khi nhìn vào Database thấy đã có người đang xử lý sẽ tự động bỏ qua (Idempotent by `experimentId`).

## 4. Tóm lược
Chỉ với 1 cờ `--scale worker=N`, hệ thống đã biến thành một cỗ máy tính toán phân tán (Distributed Computing) thực thụ!
