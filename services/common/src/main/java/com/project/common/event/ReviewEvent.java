package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder(builderMethodName = "reviewEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewEvent extends BaseEvent {

    public enum Type {
        SUBMITTED, APPROVED, REJECTED, EDITED, DELETED, FLAGGED
    }

    private Type type;
    private String reviewId;
    private String productId;
    private UUID userId;
    private int rating;
    private String moderationDecision;
}
