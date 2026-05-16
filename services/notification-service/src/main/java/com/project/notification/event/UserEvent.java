package com.project.notification.event;

import lombok.Data;

import java.time.Instant;

@Data
public class UserEvent {
    private String type;
    private String userId;
    private String email;
    private String username;
    private Instant timestamp;
}
