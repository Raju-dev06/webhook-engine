package com.webhook.engine.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhook.engine.common.DeliveryStatus;
import com.webhook.engine.config.RabbitMQConfig;
import com.webhook.engine.delivery.DeliveryRecord;
import com.webhook.engine.delivery.DeliveryRecordRepository;
import com.webhook.engine.dto.PublishEventRequest;
import com.webhook.engine.endpoint.WebhookSubscription;
import com.webhook.engine.endpoint.WebhookSubscriptionRepository;
import com.webhook.engine.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventMessageRepository eventMessageRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public void publishEvent(PublishEventRequest request) {
        // 1. Idempotency Check
        if (eventMessageRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
            log.info("Duplicate event ignored due to idempotency key: {}", request.idempotencyKey());
            return;
        }

        // 2. Convert Payload to JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException e) {
            throw new BusinessException("Invalid payload format");
        }

        // 3. Save Event (Rely on DB Unique Constraint for idempotency)
        EventMessage event = EventMessage.builder()
                .eventType(request.eventType())
                .payload(payloadJson)
                .idempotencyKey(request.idempotencyKey())
                .build();
                
        try {
            event = eventMessageRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event ignored due to idempotency key: {}", request.idempotencyKey());
            return;
        }

        // 4. Find Subscribers
        List<WebhookSubscription> subscriptions = subscriptionRepository.findByEventType(request.eventType());
        
        if (subscriptions.isEmpty()) {
            log.info("No subscribers found for event type: {}", request.eventType());
            return;
        }

        // 5. Create Delivery Records
        for (WebhookSubscription sub : subscriptions) {
            DeliveryRecord record = DeliveryRecord.builder()
                    .eventMessage(event)
                    .endpoint(sub.getEndpoint())
                    .status(DeliveryStatus.PENDING)
                    .retryCount(0)
                    .build();
            deliveryRecordRepository.save(record);

            log.info("Queued delivery record {} for endpoint {}", record.getId(), sub.getEndpoint().getId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.MAIN_EXCHANGE, RabbitMQConfig.ROUTING_KEY, record.getId());
        }
    }
}
