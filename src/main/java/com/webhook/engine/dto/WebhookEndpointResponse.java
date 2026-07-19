package com.webhook.engine.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WebhookEndpointResponse {
    private Long id;
    private String url;
    private String secretKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
