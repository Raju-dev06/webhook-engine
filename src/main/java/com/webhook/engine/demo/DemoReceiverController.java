package com.webhook.engine.demo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/demo")
public class DemoReceiverController {

    private final List<ReceivedWebhook> receivedWebhooks = new CopyOnWriteArrayList<>();
    private boolean simulateFailure = false;

    @PostMapping("/receive")
    public ResponseEntity<String> receiveWebhook(@RequestHeader HttpHeaders headers, @RequestBody String payload) {
        if (simulateFailure) {
            log.warn("Demo endpoint simulating HTTP 500 error for incoming webhook");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Simulated Internal Server Error");
        }

        log.info("Received webhook payload: {}", payload);
        
        ReceivedWebhook hook = new ReceivedWebhook();
        hook.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        hook.setHeaders(headers.toSingleValueMap());
        hook.setPayload(payload);
        
        // Keep only last 50 to prevent memory leaks in demo
        if (receivedWebhooks.size() >= 50) {
            receivedWebhooks.remove(0);
        }
        receivedWebhooks.add(hook);

        return ResponseEntity.ok("Webhook received successfully");
    }

    @GetMapping("/logs")
    public ResponseEntity<List<ReceivedWebhook>> getLogs() {
        List<ReceivedWebhook> reversed = new ArrayList<>(receivedWebhooks);
        Collections.reverse(reversed);
        return ResponseEntity.ok(reversed);
    }

    @PostMapping("/settings/failure")
    public ResponseEntity<Void> setSimulateFailure(@RequestParam boolean fail) {
        this.simulateFailure = fail;
        log.info("Simulate Failure set to: {}", fail);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/logs")
    public ResponseEntity<Void> clearLogs() {
        receivedWebhooks.clear();
        return ResponseEntity.ok().build();
    }

    @Data
    public static class ReceivedWebhook {
        private String timestamp;
        private Map<String, String> headers;
        private String payload;
    }
}
