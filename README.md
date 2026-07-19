# 🚀 Webhook Engine

A highly resilient, production-ready Webhook Delivery Engine built with **Java, Spring Boot, RabbitMQ, and MySQL**. 

## 📖 What is a Webhook Delivery Engine?
When an event happens in a system (like a customer paying for an order), a Webhook Delivery Engine is responsible for notifying external client servers about that event in real-time. It takes an event payload, securely packages it, and fires an HTTP POST request to a URL registered by the client. 

## 💡 Why is it Useful?
Sending an HTTP request is easy, but sending *millions* of them reliably over the public internet is incredibly difficult. 
If a client's server is down, they lose their data. If our system crashes, we drop events. If an attacker intercepts the request, data is compromised. 
This engine solves these distributed system problems by guaranteeing **reliable, secure, and asynchronous delivery** even in the face of network failures and high concurrency.

---

## 🌟 Core Features

* **Asynchronous Message Queueing:** Uses **RabbitMQ** to decouple the API from the delivery mechanism, ensuring the main application remains blazing fast and never blocks on slow client responses.
* **Resilience & Exponential Backoff:** Automatically handles client server outages (HTTP 5xx errors, timeouts) by routing failed messages to a retry queue with intelligent, exponential delays.
* **Cryptographic Security (HMAC SHA-256):** Every outgoing webhook is mathematically signed using a unique secret key. The receiver verifies the `x-webhook-signature` header to guarantee the payload was not tampered with.
* **Idempotency:** Prevents duplicate event processing using strict database constraints.
* **Optimistic Locking:** Uses Hibernate `@Version` control to safely handle thousands of concurrent delivery attempts without database race conditions.
* **Live Interactive Dashboard:** Includes a built-in frontend UI to visually demonstrate webhook delivery and RabbitMQ retry mechanics in real-time.

---

## 🏗️ Architecture

The system is built on a modern, event-driven microservice architecture:
* **Backend:** Java 21, Spring Boot 3.3, Spring Data JPA
* **Database:** MySQL 8 (Persisted via Docker Volumes)
* **Message Broker:** RabbitMQ 3.13 (Exchanges, Queues, Routing Keys, DLQs)
* **Frontend UI:** HTML, CSS (Glassmorphism), Vanilla JavaScript
* **Infrastructure:** Docker & Docker Compose

### Request Flow
1. **Trigger:** A business action occurs (e.g., `user.created`) via the `POST /api/v1/events` API.
2. **Idempotency Check:** The event is saved to the database. If it's a duplicate, it's rejected.
3. **Queueing:** The event is published to a RabbitMQ Topic Exchange.
4. **Processing:** The `DeliveryWorker` consumes the message from the queue asynchronously.
5. **Security:** The payload is hashed using HMAC SHA-256 using the client's secret key.
6. **Execution:** An HTTP POST request is dispatched to the client's registered URL.
7. **Retry Logic:** If the client's server is offline, the message is routed to a retry queue, delayed, and attempted again later.

---

## 🛡️ Key Reliability Ideas

This project implements several enterprise-grade reliability patterns:
1. **Dead Letter Queues (DLQ):** Messages that fail repeatedly are not lost; they are routed to a DLQ for manual inspection.
2. **Event Sourcing (Partial):** Every delivery attempt (success or failure) is logged immutably in the `delivery_record` table for auditability.
3. **Connection Pooling:** Uses HikariCP for optimal database connection management during high load.
4. **Non-Blocking I/O:** Uses Spring's `RestClient` for efficient, non-blocking HTTP calls.

---

## 🧪 Testing & Live Demos

You can test all features visually using the built-in Frontend Dashboard.

### 1. Setup
Make sure Docker Desktop is running, then start the infrastructure and application:
```bash
docker-compose up -d
mvn spring-boot:run
```
Navigate to `http://localhost:8080/index.html` in your browser.

### 2. The "Happy Path" (Basic Delivery)
1. In the dashboard, leave the **Setup Endpoint** URL blank and click **Register Endpoint**.
2. Click **Publish Event**.
3. **Result:** Watch the mock terminal on the right. You will instantly see the JSON data arrive, complete with the `x-webhook-signature` header proving it was secured!

### 3. The "Chaos Test" (RabbitMQ Exponential Backoff)
1. Click the toggle switch under **Client Server Status** so it turns grey ("Server is Offline"). This simulates an internet outage.
2. Click **Publish Event**.
3. **Result:** Nothing appears in the terminal! Instead, check your Java console logs. You will see the worker fail, wait 5 seconds, try again, fail, and wait 10 seconds.
4. **Resolution:** Flip the toggle switch back to green ("Online"). On the very next retry, the worker will succeed and the webhook will appear in the dashboard!

### 4. The "External Client" Test
1. Go to `https://webhook.site/` and copy your unique URL.
2. Paste it into the dashboard's **Setup Endpoint** text box and register it.
3. Click **Publish Event**.
4. **Result:** Go back to `webhook.site` and watch the real HTTP POST request arrive over the public internet!

---

## 🔌 API Reference

### 1. Endpoints
* **`POST /api/v1/endpoints`**: Register a new URL to receive webhooks.
* **`POST /api/v1/endpoints/subscribe`**: Subscribe an endpoint to a specific event type (e.g., `user.created`).
* **`GET /api/v1/endpoints`**: List all registered endpoints.

### 2. Events
* **`POST /api/v1/events`**: Trigger a new business event to be distributed to subscribers.

---

## 🎯 What Reviewers Should Notice

If you are reviewing this code, please note:
* The **clean separation of concerns** between Controllers, Services, and Workers.
* The use of **`@Version` in `DeliveryRecord.java`** to prevent race conditions when updating delivery statuses concurrently.
* The **RabbitMQ configuration** (`RabbitMQConfig.java`) which elegantly sets up exchanges, queues, and routing keys programmatically.
* The **HMAC hashing implementation** (`SignatureService.java`) which avoids storing raw signatures.

---

## ⚠️ Known Limitations
* **Scaling:** Currently relies on a single relational database instance. For massive scale, horizontal sharding or a NoSQL datastore (like Cassandra) for `delivery_record` would be preferred.
* **Strict Ordering:** Because of asynchronous retries, if `Event B` occurs after `Event A`, but `A` fails and enters the retry queue, `B` will be delivered before `A`. (In webhooks, strict ordering is notoriously difficult without severely bottlenecking throughput).
