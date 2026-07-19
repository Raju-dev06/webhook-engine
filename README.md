# 🚀 Webhook Engine

A highly resilient, production-ready Webhook Delivery Engine built with **Java, Spring Boot, RabbitMQ, and MySQL**. This project demonstrates how to handle asynchronous event-driven architectures, exponential backoff, cryptographic security, and idempotency in distributed systems.

## 🌟 Key Features

* **Asynchronous Delivery via RabbitMQ:** Decouples event generation from delivery using message queues, ensuring the core API remains blazing fast.
* **Resilience & Exponential Backoff:** Automatically handles client server outages (HTTP 500s, connection timeouts) by routing failed messages to a retry queue (Dead Letter Queue routing) with intelligent exponential delays.
* **Cryptographic Security (HMAC SHA-256):** Every outgoing webhook is mathematically signed using a unique secret key. The receiver can verify the `x-webhook-signature` header to guarantee the payload was not tampered with and originated from our servers.
* **Idempotency & Concurrency Control:** 
  * Prevents duplicate event processing using strict database constraints.
  * Implements **Optimistic Locking** (`@Version`) in Hibernate to safely handle thousands of concurrent delivery attempts without race conditions.
* **Live Interactive Dashboard:** Includes a built-in frontend UI (`http://localhost:8080/index.html`) to visually demonstrate real-time webhook delivery, simulate server outages, and watch the RabbitMQ retry mechanics in action.

## 🛠️ Tech Stack

* **Backend:** Java 21, Spring Boot 3.3, Spring Data JPA
* **Database:** MySQL 8 (Persisted via Docker Volumes)
* **Message Broker:** RabbitMQ 3.13 (Exchanges, Queues, Routing Keys)
* **Frontend UI:** HTML, CSS (Glassmorphism design), Vanilla JavaScript
* **Infrastructure:** Docker & Docker Compose

## 🚀 Getting Started

### 1. Start the Infrastructure
Make sure Docker Desktop is running, then spin up the MySQL and RabbitMQ containers:
```bash
docker-compose up -d
```

### 2. Run the Spring Boot Server
You can run this via your IDE or terminal:
```bash
mvn spring-boot:run
```

### 3. Open the Dashboard
Navigate to `http://localhost:8080/index.html` in your browser. From here, you can:
1. Register a webhook endpoint (either the built-in mock receiver or an external tool like `webhook.site`).
2. Simulate a client outage by toggling the server status.
3. Publish events and watch the engine securely sign and deliver the payloads in real-time!

## 🏗️ Architecture

1. **Event Trigger:** A business action occurs (e.g., `user.created`) and is sent to the `EventService`.
2. **Idempotency Check:** The event is saved to the database. If it's a duplicate, it's rejected.
3. **Queueing:** The event is published to a RabbitMQ Exchange.
4. **Processing:** The `DeliveryWorker` consumes the message from the queue.
5. **Security:** The payload is hashed using HMAC SHA-256.
6. **Execution:** An HTTP POST request is sent to the client.
7. **Retry Logic:** If the client's server is down, the message is routed to a retry queue with a delay, and the delivery is attempted again later.

---
*Built to demonstrate enterprise-grade distributed system patterns.*
