package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope every event extends. Carries idempotency key, schema version,
 * and trace id. Producers MUST set eventId; consumers SHOULD deduplicate on it.
 *
 * <p>Note: no {@code @Builder} on this abstract class — Lombok would try to
 * instantiate the abstract base directly. Subclass builders cover their own
 * fields; the inherited fields here are populated by the no-args constructor's
 * field initializers (eventId, schemaVersion, timestamp). Set {@code traceId}
 * explicitly with the standard setter when you have one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseEvent {
    /** Unique id for the event itself; used for idempotent processing. */
    private String eventId = UUID.randomUUID().toString();

    /** Schema version. Bump when event shape changes. */
    private int schemaVersion = 1;

    /** Distributed trace id propagated from the producer. */
    private String traceId;

    /** Wall-clock time the event was produced. */
    private Instant timestamp = Instant.now();
}
