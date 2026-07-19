package com.webhook.engine.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventMessageRepository extends JpaRepository<EventMessage, Long> {
    Optional<EventMessage> findByIdempotencyKey(String idempotencyKey);
}
