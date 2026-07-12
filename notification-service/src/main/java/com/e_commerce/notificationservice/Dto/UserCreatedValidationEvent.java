package com.e_commerce.notificationservice.Dto;

import java.time.Instant;

public record UserCreatedValidationEvent(
        String verificationToken,
        String email,
        Long userId,
        Instant createdAt
) {
}
