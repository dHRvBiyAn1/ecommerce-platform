package com.project.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

public class UserEvent {

    public enum Type {
        CREATED, LOGGED_IN, LOGGED_OUT, PASSWORD_CHANGED, ROLE_CHANGED, EMAIL_VERIFIED
    }

    public UserEvent() {}

    public UserEvent(Type type, UUID userId, String email) {
        this.type = type;
        this.userId = userId;
        this.email = email;
    }

    public UserEvent(Type type, UUID userId, String email, LocalDateTime timestamp) {
        this.type = type;
        this.userId = userId;
        this.email = email;
        this.timestamp = timestamp;
    }

    private Type type;
    private UUID userId;
    private String email;
    private LocalDateTime timestamp = LocalDateTime.now();

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
