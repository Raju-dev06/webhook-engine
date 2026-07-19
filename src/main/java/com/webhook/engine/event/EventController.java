package com.webhook.engine.event;

import com.webhook.engine.dto.PublishEventRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "APIs for publishing events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @Operation(summary = "Publish a new event")
    public ResponseEntity<Void> publishEvent(@Valid @RequestBody PublishEventRequest request) {
        eventService.publishEvent(request);
        return ResponseEntity.accepted().build();
    }
}
