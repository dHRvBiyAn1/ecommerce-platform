package com.project.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    private String id;
    private String userId;
    private String type;
    private String subject;
    private String body;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
