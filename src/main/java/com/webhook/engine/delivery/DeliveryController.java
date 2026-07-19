package com.webhook.engine.delivery;

import com.webhook.engine.dto.DeliveryRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@Tag(name = "Deliveries", description = "APIs for tracking and managing webhook deliveries")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping("/history/{endpointId}")
    @Operation(summary = "Get delivery history for an endpoint")
    public ResponseEntity<Page<DeliveryRecordResponse>> getDeliveryHistory(@PathVariable Long endpointId, Pageable pageable) {
        return ResponseEntity.ok(deliveryService.getDeliveryHistory(endpointId, pageable));
    }

    @PostMapping("/{deliveryRecordId}/replay")
    @Operation(summary = "Manually replay a failed delivery")
    public ResponseEntity<Void> replayDelivery(@PathVariable Long deliveryRecordId) {
        deliveryService.replayFailedDelivery(deliveryRecordId);
        return ResponseEntity.accepted().build();
    }
}
