package com.webhook.engine.endpoint;

import com.webhook.engine.dto.RegisterEndpointRequest;
import com.webhook.engine.dto.SubscribeEventRequest;
import com.webhook.engine.dto.WebhookEndpointResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
@Tag(name = "Webhook Endpoints", description = "APIs for managing webhook endpoints")
public class EndpointController {

    private final WebhookEndpointService endpointService;

    @PostMapping
    @Operation(summary = "Register a new webhook endpoint")
    public ResponseEntity<WebhookEndpointResponse> registerEndpoint(@Valid @RequestBody RegisterEndpointRequest request) {
        return new ResponseEntity<>(endpointService.registerEndpoint(request), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List all webhook endpoints")
    public ResponseEntity<Page<WebhookEndpointResponse>> getAllEndpoints(Pageable pageable) {
        return ResponseEntity.ok(endpointService.getAllEndpoints(pageable));
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Subscribe an endpoint to an event type")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeEventRequest request) {
        endpointService.subscribe(request.endpointId(), request.eventType());
        return ResponseEntity.ok().build();
    }
}
