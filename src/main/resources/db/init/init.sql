-- Webhook Engine Database Schema

CREATE DATABASE IF NOT EXISTS webhook_engine;
USE webhook_engine;

-- Webhook Endpoint
CREATE TABLE webhook_endpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    secret_key VARCHAR(255) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Webhook Subscription (Mapping Endpoints to Event Types)
CREATE TABLE webhook_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    endpoint_id BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(id) ON DELETE CASCADE,
    UNIQUE KEY uk_endpoint_event (endpoint_id, event_type)
);

-- Event Message (Stored before publishing to RMQ for audit/reliability)
CREATE TABLE event_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    INDEX idx_event_type (event_type)
);

-- Delivery Record (Tracks the overall status of delivering an event to an endpoint)
CREATE TABLE delivery_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_message_id BIGINT NOT NULL,
    endpoint_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, DELIVERED, FAILED, DEAD_LETTERED
    retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (event_message_id) REFERENCES event_message(id) ON DELETE CASCADE,
    FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(id) ON DELETE CASCADE,
    INDEX idx_delivery_status (status),
    INDEX idx_next_retry (next_retry_at)
);
