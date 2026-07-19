package com.webhook.engine.worker;

import com.rabbitmq.client.Channel;
import com.webhook.engine.common.Constants;
import com.webhook.engine.common.DeliveryStatus;
import com.webhook.engine.config.RabbitMQConfig;
import com.webhook.engine.delivery.DeliveryRecord;
import com.webhook.engine.delivery.DeliveryRecordRepository;
import com.webhook.engine.delivery.SignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryWorker {

    private final DeliveryRecordRepository deliveryRecordRepository;
    private final SignatureService signatureService;
    private final RestClient restClient;
    private final RabbitTemplate rabbitTemplate;

    private static final int MAX_ATTEMPTS = 4;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.MAIN_QUEUE, ackMode = "MANUAL")
    public void processDelivery(Long deliveryRecordId, Channel channel, 
                                @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        
        log.info("Processing delivery record ID: {}", deliveryRecordId);

        DeliveryRecord record = deliveryRecordRepository.findById(deliveryRecordId).orElse(null);
        if (record == null) {
            log.warn("Delivery record not found: {}", deliveryRecordId);
            channel.basicAck(tag, false);
            return;
        }

        if (record.getStatus() == DeliveryStatus.DELIVERED) {
            log.info("Record {} is already delivered.", deliveryRecordId);
            channel.basicAck(tag, false);
            return;
        }

        record.setStatus(DeliveryStatus.PROCESSING);
        deliveryRecordRepository.save(record);

        int currentAttemptNumber = record.getRetryCount() + 1;
        String payload = record.getEventMessage().getPayload();
        String secret = record.getEndpoint().getSecretKey();
        String signature = signatureService.generateSignature(payload, secret);
        String eventId = record.getEventMessage().getId().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());

        boolean success = false;

        try {
            var responseEntity = restClient.post()
                    .uri(record.getEndpoint().getUrl())
                    .header(Constants.HEADER_EVENT_ID, eventId)
                    .header(Constants.HEADER_DELIVERY_ID, String.valueOf(record.getId()))
                    .header(Constants.HEADER_TIMESTAMP, timestamp)
                    .header(Constants.HEADER_SIGNATURE, signature)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            HttpStatusCode statusCode = responseEntity.getStatusCode();
            
            if (statusCode.is2xxSuccessful()) {
                success = true;
            } else {
                log.warn("Received non-2xx response: {} for record {}", statusCode, deliveryRecordId);
            }
        } catch (RestClientException e) {
            log.error("Failed to deliver webhook for record {}", deliveryRecordId, e);
        }

        if (success) {
            record.setStatus(DeliveryStatus.DELIVERED);
            record.setRetryCount(currentAttemptNumber);
            deliveryRecordRepository.save(record);
            log.info("Successfully delivered webhook record {}", deliveryRecordId);
        } else {
            handleFailure(record, currentAttemptNumber);
        }

        // Always ACK the original message since we either processed it or pushed to a retry/DLQ
        channel.basicAck(tag, false);
    }

    private void handleFailure(DeliveryRecord record, int currentAttemptNumber) {
        record.setRetryCount(currentAttemptNumber);

        if (currentAttemptNumber >= MAX_ATTEMPTS) {
            log.warn("Exhausted retries for delivery record {}. Moving to DLQ.", record.getId());
            record.setStatus(DeliveryStatus.DEAD_LETTERED);
            deliveryRecordRepository.save(record);
            
            // Route to DLQ manually or let it be if business logic says DLQ is just the state
            // But we can actually push it to the DLQ exchange so operators can see it in RMQ
            rabbitTemplate.convertAndSend(RabbitMQConfig.DLX_EXCHANGE, RabbitMQConfig.DLQ_QUEUE, record.getId());
        } else {
            record.setStatus(DeliveryStatus.FAILED);
            // Calculate next retry and route to appropriate delay queue
            String routingKey = getRetryRoutingKey(currentAttemptNumber);
            
            long delay = getDelayMs(currentAttemptNumber);
            record.setNextRetryAt(LocalDateTime.now().plusSeconds(delay / 1000));
            deliveryRecordRepository.save(record);
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.RETRY_EXCHANGE, routingKey, record.getId());
        }
    }

    private String getRetryRoutingKey(int failedAttemptCount) {
        return switch (failedAttemptCount) {
            case 1 -> "retry.5s";
            case 2 -> "retry.10s";
            case 3 -> "retry.20s";
            default -> "retry.40s";
        };
    }
    
    private long getDelayMs(int failedAttemptCount) {
        return switch (failedAttemptCount) {
            case 1 -> 5000;
            case 2 -> 10000;
            case 3 -> 20000;
            default -> 40000;
        };
    }
}
