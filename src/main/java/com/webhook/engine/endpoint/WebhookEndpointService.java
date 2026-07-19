package com.webhook.engine.endpoint;

import com.webhook.engine.dto.RegisterEndpointRequest;
import com.webhook.engine.dto.WebhookEndpointResponse;
import com.webhook.engine.exception.BusinessException;
import com.webhook.engine.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookEndpointService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;

    @Transactional
    public WebhookEndpointResponse registerEndpoint(RegisterEndpointRequest request) {
        return endpointRepository.findByUrl(request.url())
            .map(saved -> WebhookEndpointResponse.builder()
                    .id(saved.getId())
                    .url(saved.getUrl())
                    .secretKey(saved.getSecretKey())
                    .createdAt(saved.getCreatedAt())
                    .updatedAt(saved.getUpdatedAt())
                    .build())
            .orElseGet(() -> {
                WebhookEndpoint endpoint = WebhookEndpoint.builder()
                        .url(request.url())
                        .secretKey(UUID.randomUUID().toString())
                        .build();

                WebhookEndpoint saved = endpointRepository.save(endpoint);
                
                return WebhookEndpointResponse.builder()
                        .id(saved.getId())
                        .url(saved.getUrl())
                        .secretKey(saved.getSecretKey())
                        .createdAt(saved.getCreatedAt())
                        .updatedAt(saved.getUpdatedAt())
                        .build();
            });
    }

    @Transactional(readOnly = true)
    public Page<WebhookEndpointResponse> getAllEndpoints(Pageable pageable) {
        return endpointRepository.findAll(pageable)
                .map(e -> WebhookEndpointResponse.builder()
                        .id(e.getId())
                        .url(e.getUrl())
                        .secretKey(e.getSecretKey())
                        .createdAt(e.getCreatedAt())
                        .updatedAt(e.getUpdatedAt())
                        .build());
    }

    @Transactional
    public void subscribe(Long endpointId, String eventType) {
        WebhookEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + endpointId));

        if (subscriptionRepository.findByEndpointIdAndEventType(endpointId, eventType).isEmpty()) {
            WebhookSubscription subscription = WebhookSubscription.builder()
                    .endpoint(endpoint)
                    .eventType(eventType)
                    .build();

            subscriptionRepository.save(subscription);
        }
    }
}
