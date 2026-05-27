package com.project.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.io.IOException;

/**
 * Factory for Kafka error handlers that route un-processable messages to a
 * Dead Letter Topic ({@code <topic>.DLT}).
 *
 * <p>Retry: 3 attempts, 1 s apart, then publish to DLT.
 *
 * <p>Permanent (non-retryable) failures: {@link IllegalArgumentException},
 * {@link IOException}.
 */
@Slf4j
public final class KafkaErrorHandlerFactory {

    private KafkaErrorHandlerFactory() {}

    public static DefaultErrorHandler dltAwareErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLT", record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, IOException.class);
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.WARN);
        return handler;
    }
}
