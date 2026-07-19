package com.webhook.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscribeEventRequest(
        @NotNull(message = "Endpoint ID is required")
        Long endpointId,
        
        @NotBlank(message = "Event type is required")
        String eventType
) {}
