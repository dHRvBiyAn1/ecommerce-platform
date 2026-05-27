package com.project.notification.kafka;

import com.project.common.constant.Topics;
import com.project.common.event.UserEvent;
import com.project.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventHandler {

    private final NotificationService notificationService;

    @KafkaListener(topics = Topics.USER_EVENTS, containerFactory = "kafkaListenerContainerFactory")
    public void handle(UserEvent event) {
        if (event == null || event.getType() == null) return;
        log.info("UserEvent received: {} userId={} email={}", event.getType(), event.getUserId(), event.getEmail());

        switch (event.getType()) {
            case CREATED -> notificationService.record(event.getUserId(), event.getEmail(),
                    "EMAIL", "ACCOUNT",
                    "Welcome!",
                    "<p>Hi %s,</p><p>Welcome aboard.</p>".formatted(
                            event.getDisplayName() != null ? event.getDisplayName() : "there"),
                    event.getEventId());
            case PASSWORD_CHANGED -> notificationService.record(event.getUserId(), event.getEmail(),
                    "EMAIL", "ACCOUNT", "Password changed",
                    "Your password was just changed. If this wasn't you, contact support immediately.",
                    event.getEventId());
            case EMAIL_VERIFIED -> notificationService.record(event.getUserId(), event.getEmail(),
                    "EMAIL", "ACCOUNT", "Email verified",
                    "Thanks for verifying your email address.", event.getEventId());
            case MFA_ENABLED -> notificationService.record(event.getUserId(), event.getEmail(),
                    "EMAIL", "ACCOUNT", "Two-factor authentication enabled",
                    "MFA is now enabled on your account.", event.getEventId());
            default -> { /* ignore */ }
        }
    }
}
