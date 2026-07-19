package com.webhook.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

public record RegisterEndpointRequest(
        @NotBlank(message = "URL is required")
        @URL(message = "Must be a valid URL")
        String url
) {}
