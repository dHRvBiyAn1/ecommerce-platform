package com.project.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.constant.Topics;
import com.project.payment.api.dto.request.PaymentWebhookRequest;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.client.OrderClient;
import com.project.payment.kafka.PaymentEventPublisher;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import com.project.payment.repository.PaymentOutboxRepository;
import com.project.payment.repository.PaymentRepository;
import com.project.payment.repository.WebhookReceiptRepository;
import com.project.payment.service.impl.PaymentGateway;
import com.project.payment.service.impl.PaymentOutboxRelay;
import com.project.payment.service.impl.PaymentServiceImpl;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers
@DataMongoTest
class PaymentMongoKafkaIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private WebhookReceiptRepository receipts;

    @Autowired
    private PaymentOutboxRepository outbox;

    @Autowired
    private com.project.payment.repository.PaymentOperationRepository operations;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @BeforeEach
    void reset() {
        outbox.deleteAll();
        receipts.deleteAll();
        operations.deleteAll();
        payments.deleteAll();
    }

    @Test
    void duplicateVerifiedWebhookPersistsOneTransitionAndPublishesOneKafkaEvent() {
        Payment saved = payments.save(Payment.builder().paymentReference("PAY-1").orderId("order-1")
                .userId(UUID.randomUUID()).status(PaymentStatus.PENDING).paymentMethod("CARD")
                .amount(BigDecimal.TEN).currency("USD").transactionId("pi-1").build());
        PaymentServiceImpl service = new PaymentServiceImpl(payments, operations, receipts, outbox, mock(PaymentGateway.class),
                new PaymentMapper(), mock(OrderClient.class), new PaymentOrderValidator(),
                new PaymentTransitionValidator(), new ObjectMapper().findAndRegisterModules());
        PaymentWebhookRequest webhook = new PaymentWebhookRequest("PAY-1", "pi-1", "COMPLETED", null);

        service.handleStripeWebhook("evt-1", "payment_intent.succeeded", "PAY-1", webhook);
        service.handleStripeWebhook("evt-1", "payment_intent.succeeded", "PAY-1", webhook);

        assertThat(receipts.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(payments.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(Topics.PAYMENT_EVENTS));
            PaymentOutboxRelay relay = new PaymentOutboxRelay(outbox,
                    new PaymentEventPublisher(kafkaTemplate(), new ObjectMapper().findAndRegisterModules()));
            relay.relayEvents();
            relay.relayEvents();
            Iterable<ConsumerRecord<String, String>> events = consumer.poll(Duration.ofSeconds(10)).records(Topics.PAYMENT_EVENTS);
            assertThat(events).hasSize(1);
        }
    }

    private KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                com.project.common.kafka.KafkaProducerProps.defaults(KAFKA.getBootstrapServers())));
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "payment-proof-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
