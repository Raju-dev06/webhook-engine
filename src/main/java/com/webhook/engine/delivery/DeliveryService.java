package com.webhook.engine.delivery;

import com.webhook.engine.dto.DeliveryRecordResponse;
import com.webhook.engine.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRecordRepository deliveryRecordRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional(readOnly = true)
    public Page<DeliveryRecordResponse> getDeliveryHistory(Long endpointId, Pageable pageable) {
        return deliveryRecordRepository.findByEndpointId(endpointId, pageable)
                .map(record -> DeliveryRecordResponse.builder()
                        .id(record.getId())
                        .eventMessageId(record.getEventMessage().getId())
                        .endpointId(record.getEndpoint().getId())
                        .status(record.getStatus())
                        .retryCount(record.getRetryCount())
                        .nextRetryAt(record.getNextRetryAt())
                        .createdAt(record.getCreatedAt())
                        .updatedAt(record.getUpdatedAt())
                        .build());
    }

    @Transactional
    public void replayFailedDelivery(Long deliveryRecordId) {
        DeliveryRecord record = deliveryRecordRepository.findById(deliveryRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery record not found"));

        // Reset retry count and status, then queue again
        record.setRetryCount(0);
        record.setStatus(com.webhook.engine.common.DeliveryStatus.PENDING);
        deliveryRecordRepository.save(record);

        rabbitTemplate.convertAndSend("webhook.exchange", "webhook.routing.key", record.getId());
    }
}
