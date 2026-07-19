# Architecture Document: Webhook Delivery Engine

## 1. High-Level Architecture
The Webhook Delivery Engine is designed as a **Modular Monolith**. It exposes REST APIs for client configuration and event publishing. When an event is published, the system asynchronously fans out the delivery to all subscribed endpoints using RabbitMQ.

## 2. Request Flow
1. **Publish API:** Client calls `POST /api/v1/events` with payload and `idempotency_key`.
2. **Persistence:** The `EventService` attempts to save the event in the `event_message` table. We rely on a database unique constraint to gracefully discard duplicates (Idempotency check).
3. **Fan-out:** It finds all `WebhookSubscription`s for the event type.
4. **Queueing (Transactional):** For each subscriber, it creates a `DeliveryRecord` in `PENDING` state. Using `TransactionSynchronizationManager`, we ensure the `DeliveryRecordId` and MDC `trace_id` are only published to RabbitMQ's `webhook.exchange` *after* the database commit succeeds.
5. **Consumption:** The `DeliveryWorker` consumes the message, extracting the `trace_id` into the MDC for correlated logging.
6. **Optimistic Locking:** The worker attempts to set the status to `PROCESSING`. If another worker raced to pick up a redelivery, `@Version` prevents overwriting, avoiding duplicate dispatches.
7. **Execution:** It signs the payload and executes the POST request using a tightly-tuned `RestClient` backed by `VirtualThreads`.
8. **Success:** Updates status to `DELIVERED`.
9. **Failure (Retry Logic):** Updates status to `FAILED`. Computes the next retry delay (5s, 10s, etc.) and routes the message to the respective `webhook.retry.*s` queue.

## 3. RabbitMQ Flow (Non-blocking Retry)
Instead of holding the thread (blocking), we use separate TTL Queues for exponential backoff:
- `webhook.main.queue` (Main consumption)
- `webhook.retry.5s` (TTL = 5000ms, DLX = webhook.exchange)
- `webhook.retry.10s` (TTL = 10000ms, DLX = webhook.exchange)
- `webhook.retry.20s` (TTL = 20000ms, DLX = webhook.exchange)
- `webhook.retry.40s` (TTL = 40000ms, DLX = webhook.exchange)
- `webhook.dlq` (Dead Letter Queue for exhausted retries)

## 4. Database Schema
- **webhook_endpoint:** Stores URL and secret key.
- **webhook_subscription:** Maps endpoints to event types.

## 5. Engineering Decision Log
- **Why RabbitMQ instead of Kafka?** Kafka is great for event streaming and log aggregation. However, for a Webhook engine where we need granular, per-message routing, TTL-based delays, and Dead Lettering based on individual message failure, RabbitMQ's advanced routing and DLX features are fundamentally superior and easier to implement.
- **Why Modular Monolith instead of Microservices?** A monolith is easier to deploy, test, and maintain. For a system serving up to thousands of requests a second, a well-structured modular monolith provides high cohesion without the distributed systems tax.
- **Idempotency:** Checked at the database level using a UNIQUE constraint on `idempotency_key`. This guarantees atomicity, compared to a check-then-act application level cache which has race conditions.
- **Transactional Messaging:** Messages are pushed to RabbitMQ in the `afterCommit` phase of the database transaction to prevent "phantom reads" where the consumer tries to fetch a `DeliveryRecord` that hasn't committed yet.
- **Observability:** Used an MDC Filter to generate and propagate `trace_id` through the REST API, down to RabbitMQ message headers, and back into the async worker threads, ensuring logs are debuggable.
- **Virtual Threads for RestClient:** Used Java 21's Virtual Threads inside the `JdkClientHttpRequestFactory` for massive non-blocking outbound HTTP request concurrency without thread pool exhaustion.

## 6. Production Improvements & Scaling to 10k RPS
- **Pagination Everywhere:** All collection APIs return `Page<T>` to prevent OutOfMemory exceptions.
- **Database Scaling:** Introduce read-replicas for the API layer and partition `event_message` and `delivery_record` tables by date.
- **Caching:** Cache `WebhookEndpoint` and `WebhookSubscription` data using Redis to avoid DB hits during the fan-out phase.
- **Connection Pooling:** Use Apache HttpClient or OkHttp connection pools behind `RestClient` to handle thousands of concurrent outbound connections efficiently.
- **Message Broker Scaling:** Cluster RabbitMQ or migrate to a highly available partitioned queue system if single-node throughput is breached.

## 7. Migrating to Microservices
If needed, the domain can be split into:
1. **Config Service:** Manages Endpoints and Subscriptions.
2. **Ingestion Service:** Validates and accepts events, putting them into an initial topic.
3. **Dispatcher Service:** Consumes the topic, does the fan-out, and pushes to individual delivery queues.
4. **Worker Service:** Only handles the HTTP calls and retries.
