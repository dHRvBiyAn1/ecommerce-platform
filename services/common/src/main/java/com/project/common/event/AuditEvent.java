package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entry. Every privileged operation must emit one of these.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder(builderMethodName = "auditEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditEvent extends BaseEvent {

    private String action;          // e.g., "REFUND_ISSUED", "ROLE_GRANTED"
    private String actorService;    // service that performed the action
    private UUID actorUserId;
    private String actorEmail;
    private String resourceType;
    private String resourceId;
    private String description;
    private String ipAddress;
    private String userAgent;
    private Instant occurredAt;
}
