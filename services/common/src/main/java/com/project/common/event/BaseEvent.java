package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope every event extends. Carries idempotency key, schema version,
 * and trace id. Producers MUST set eventId; consumers SHOULD deduplicate on it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseEvent {
    /** Unique id for the event itself; used for idempotent processing. */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /** Schema version. Bump when event shape changes. */
    @Builder.Default
    private int schemaVersion = 1;

    /** Distributed trace id propagated from the producer. */
    private String traceId;

    /** Wall-clock time the event was produced. */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
