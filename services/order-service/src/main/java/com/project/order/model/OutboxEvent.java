package com.project.order.model;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    private String id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private String status; // PENDING, PROCESSED, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
