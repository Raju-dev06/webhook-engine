# Webhook Delivery Engine

A production-ready Webhook Delivery Engine built with Java 21, Spring Boot 3.3, MySQL 8, and RabbitMQ.

## Features
- **Reliable Delivery:** Uses RabbitMQ for durable and reliable asynchronous processing.
- **Exponential Backoff Retry:** Failed deliveries are retried at 5s, 10s, 20s, and 40s using separate TTL queues.
- **Dead Letter Queue (DLQ):** Exhausted retries are moved to a DLQ for operator inspection.
- **HMAC SHA-256 Signing:** All outgoing webhook requests are signed to guarantee authenticity and integrity.
- **Idempotency:** Prevents duplicate event processing using an `idempotency_key`.
- **Manual Replay:** APIs provided to manually replay failed deliveries from the DLQ.
- **Comprehensive Tracking:** Records overall delivery status and every individual HTTP attempt.

## Technology Stack
- Java 21
- Spring Boot 3.3.x (Web, Data JPA, AMQP, Validation)
- MySQL 8
- RabbitMQ
- MapStruct & Lombok
- Docker & Docker Compose
- Swagger/OpenAPI

## How to Run

1. **Start Infrastructure (MySQL & RabbitMQ):**
   ```bash
   docker-compose up -d
   ```

2. **Run Application:**
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Access APIs:**
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - RabbitMQ Management: `http://localhost:15672` (guest/guest)

## API Flow
1. Register an Endpoint (`POST /api/v1/endpoints`).
2. Subscribe Endpoint to an Event Type (`POST /api/v1/endpoints/subscribe`).
3. Publish an Event (`POST /api/v1/events`).
4. View Delivery History (`GET /api/v1/deliveries/history/{endpointId}`).
