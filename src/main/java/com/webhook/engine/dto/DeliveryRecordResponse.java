package com.webhook.engine.dto;

import com.webhook.engine.common.DeliveryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeliveryRecordResponse {
    private Long id;
    private Long eventMessageId;
    private Long endpointId;
    private DeliveryStatus status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
