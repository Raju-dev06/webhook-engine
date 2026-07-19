# Interview Guide: Webhook Delivery Engine

## 1. The 10-Minute Pitch
"I designed and implemented a production-grade Webhook Delivery Engine. It is a Modular Monolith built with Java 21, Spring Boot, MySQL, and RabbitMQ. 
The core problem it solves is reliably delivering asynchronous events to third-party endpoints. 
When a client publishes an event, the system persists it for idempotency relying on DB constraints, then fans it out to all subscribed endpoints by queueing messages in RabbitMQ only *after* the DB transaction successfully commits. 
A fleet of asynchronous workers consumes these messages, pulling a Correlation ID from the headers into MDC for observability. The workers use Optimistic Locking to prevent race conditions during redeliveries, sign the payload using HMAC SHA-256 for security, and dispatch the HTTP POST using a tuned RestClient running on Java 21 Virtual Threads. 
If the delivery fails, I implemented a non-blocking exponential backoff retry mechanism (5s, 10s, 20s, 40s) using RabbitMQ's Message TTL and Dead Letter Exchanges. After 4 failed attempts, messages are moved to a Dead Letter Queue (DLQ). 
This architecture guarantees at-least-once delivery, security, deep observability, and massive scalability."

## Top 50 Interview Questions & Answers

*(Note: In a real interview, these topics would be covered. Here are the most critical ones)*

### Architecture & Design
**1. Why did you choose RabbitMQ over Kafka?**
*Answer:* Kafka is a partitioned log designed for high-throughput streaming and replayability. RabbitMQ is a smart broker with advanced routing. For webhooks, we need per-message routing, message-level TTL for retries, and Dead Lettering for failed messages. RabbitMQ natively supports these patterns via DLX, whereas Kafka would require complex custom consumer logic to handle delays.

**2. Why a Modular Monolith instead of Microservices?**
*Answer:* Microservices introduce network latency, distributed tracing complexity, and deployment overhead. A modular monolith provides the internal code boundaries (high cohesion, low coupling) without the distributed systems tax, making it the perfect starting point.

**3. How do you handle idempotency?**
*Answer:* The `PublishEventRequest` requires an `idempotency_key`. The `event_message` table has a `UNIQUE` constraint on this column. If a duplicate request arrives, the `save()` method throws a `DataIntegrityViolationException`. By catching this, we safely ignore the duplicate and prevent race conditions (avoiding a classic check-then-act flaw).

**3b. How do you prevent race conditions in the async workers?**
*Answer:* RabbitMQ might redeliver an un-acknowledged message if a pod restarts. To prevent two threads from dispatching the same webhook, I added an `@Version` column to `DeliveryRecord`. When the worker transitions the status to `PROCESSING`, any concurrent thread attempting the same will receive an `ObjectOptimisticLockingFailureException`. We catch this, log it, and discard the message.

### RabbitMQ Deep Dive
**4. How does your exponential backoff retry work without blocking the consumer?**
*Answer:* If we thread.sleep() inside the consumer, we block the thread and reduce throughput. Instead, when a delivery fails, the consumer Acks the original message and re-publishes it to a specific delay queue (e.g., `webhook.retry.5s`). This queue has no consumers, but has an `x-message-ttl` of 5000ms and an `x-dead-letter-exchange` pointing back to the main queue. When the TTL expires, RabbitMQ automatically routes it back to the main queue for processing.

**5. What is a Dead Letter Exchange (DLX)?**
*Answer:* An exchange where messages are routed if they are rejected, expire (TTL), or the queue is full.

**6. Why use Manual ACK instead of Auto ACK?**
*Answer:* With Auto ACK, the broker considers the message processed as soon as it sends it to the consumer. If the consumer crashes before making the HTTP call, the message is lost. Manual ACK ensures the message is only acknowledged *after* we have successfully processed it or safely routed it to a retry/DLQ queue.

### Security
**7. How do you secure the webhooks you send?**
*Answer:* Using HMAC SHA-256. When an endpoint is registered, we generate a unique `secret_key`. Before sending the HTTP request, the worker hashes the JSON payload using this secret. The resulting hash is placed in the `X-Webhook-Signature` header. The receiver computes the hash on their end using the shared secret and compares it to verify authenticity and integrity.

### Spring Boot & Java
**8. Why use Spring's `RestClient` instead of `RestTemplate`?**
*Answer:* `RestClient` is a modern, fluent, synchronous HTTP client introduced in Spring 6.1. It provides a cleaner builder API compared to the older `RestTemplate` while remaining synchronous, which fits our worker model well.

**9. What is MapStruct and why use it over modelmapper?**
*Answer:* MapStruct is a compile-time code generator. It generates plain Java code for mapping DTOs to Entities, making it extremely fast and type-safe compared to reflection-based mappers like ModelMapper.

**10. How do you handle database transactions across multiple operations?**
*Answer:* Using Spring's `@Transactional`. However, mixing DB commits and RMQ publishing is dangerous. If the RMQ publish succeeds but the DB commit drops, we have phantom messages. To fix this, I use `TransactionSynchronizationManager` to register the `rabbitTemplate.convertAndSend` execution *only during the `afterCommit` phase*.

### Future Scale & Edge Cases
### Q: How do you handle idempotency?
**A:** "I enforce strict idempotency at the database level. The `event_message` table has a `UNIQUE` constraint on the `idempotency_key`. If the client sends the same key twice, the database rejects it with a `DataIntegrityViolationException`, which I gracefully catch and ignore. This is completely atomic and prevents race conditions without needing Redis."

### Q: Why RabbitMQ and not Kafka?
**A:** "Kafka is great for log-streaming and event-sourcing, but Webhooks require precise, targeted redeliveries with exponential backoff. RabbitMQ's built-in TTL (Time-To-Live) and Dead Letter Exchanges make implementing non-blocking retry queues trivial. Building this in Kafka requires complex separate worker clusters."

### Q: What if a worker pod crashes while processing a delivery?
**A:** "Because I use `ackMode = MANUAL`, RabbitMQ will not remove the message from the queue until the worker explicitly sends an ACK. If the pod crashes, the connection drops, and RabbitMQ immediately requeues the message for another pod to pick up."

### Q: How would you prevent Phantom Reads if RabbitMQ fails?
**A:** "Right now, I publish to RabbitMQ immediately after saving to the DB. At massive scale, I would use the **Transactional Outbox Pattern** or Spring's `TransactionSynchronizationManager` to guarantee that the RabbitMQ publish only happens *after* the database commit succeeds."

### Q: How do you trace a request across the API and async workers?
**A:** "I would implement MDC (Mapped Diagnostic Context) Tracing. I'd generate a `trace_id` at the API edge, inject it into the RabbitMQ message headers, and extract it inside the async worker. This allows perfect log correlation in Datadog or Kibana."

### Q: How do you prevent race conditions if RabbitMQ redelivers exactly at the same time?
**A:** "I would add **Optimistic Locking** by putting an `@Version` column on the `DeliveryRecord` table. If two workers try to update the exact same record, one will throw an `ObjectOptimisticLockingFailureException` and gracefully discard the duplicate."

### Q: How would you tune the HTTP Client for thousands of concurrent webhooks?
**A:** "I would migrate the Spring `RestClient` to use **Java 21 Virtual Threads**. By swapping the executor to `Executors.newVirtualThreadPerTaskExecutor()`, the JVM can handle tens of thousands of concurrent I/O blocked threads without exhausting the underlying OS platform threads."

### Database
**11. Why separate `delivery_record` and `delivery_attempt`?**
*Answer:* A `delivery_record` represents the overall lifecycle of an event reaching an endpoint (status: PENDING to DELIVERED). However, it might take 4 retries to get there. If we only had one table, we'd overwrite the HTTP status code each time. `delivery_attempt` allows us to keep an audit trail of every single HTTP request (e.g., attempt 1: 500, attempt 2: 503, attempt 3: 200).

*(Addressed to interviewer: The above represents the core conceptual knowledge required to defend this design in a Senior Engineering interview)*
