package com.webhook.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PublishEventRequest(
        @NotBlank(message = "Event type is required")
        String eventType,
        
        @NotNull(message = "Payload is required")
        Object payload,
        
        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {}
