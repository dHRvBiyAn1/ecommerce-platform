package com.project.notification.config;

import com.project.common.kafka.KafkaConsumerProps;
import com.project.common.kafka.KafkaErrorHandlerFactory;
import com.project.common.kafka.KafkaProducerProps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    // Producer for sending DLT messages
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(KafkaProducerProps.defaults(bootstrapServers));
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Generic consumer factory: deserialize whatever target type the listener method declares.
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = KafkaConsumerProps.defaults(bootstrapServers, "notification-service");
        // Trust common event package
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.project.common.event");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(KafkaErrorHandlerFactory.dltAwareErrorHandler(kafkaTemplate()));
        return factory;
    }
}
