package com.webhook.engine.common;

public class Constants {
    
    public static final String HEADER_EVENT_ID = "X-Webhook-Event-Id";
    public static final String HEADER_DELIVERY_ID = "X-Webhook-Delivery-Id";
    public static final String HEADER_TIMESTAMP = "X-Webhook-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Webhook-Signature";

    private Constants() {
        // utility class
    }
}
