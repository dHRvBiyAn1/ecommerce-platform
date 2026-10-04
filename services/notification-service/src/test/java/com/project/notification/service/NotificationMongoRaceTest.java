package com.project.notification.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.project.notification.application.mapper.NotificationMapper;
import com.project.notification.application.validator.NotificationAccessValidator;
import com.project.notification.config.NotificationIndexInitializer;
import com.project.notification.model.Notification;
import com.project.notification.model.NotificationDelivery;
import com.project.notification.repository.NotificationDeliveryRepository;
import com.project.notification.repository.NotificationRepository;
import com.project.notification.api.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "notification.mongo.integration", matches = "true")
class NotificationMongoRaceTest {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Test
    void serviceRecordUsesRealRepositoriesForConcurrentDuplicateEvents() throws Exception {
        try (MongoClient client = MongoClients.create(mongo.getReplicaSetUrl())) {
            MongoTemplate template = new MongoTemplate(client, "notification-race");
            new NotificationIndexInitializer(template).initialize();

            MongoRepositoryFactory repositoryFactory = new MongoRepositoryFactory(template);
            NotificationRepository notificationRepository =
                    repositoryFactory.getRepository(NotificationRepository.class);
            NotificationDeliveryRepository deliveryRepository =
                    repositoryFactory.getRepository(NotificationDeliveryRepository.class);
            NotificationService service = new NotificationService(
                    notificationRepository,
                    deliveryRepository,
                    null,
                    new NotificationMapper(),
                    new NotificationAccessValidator());

            UUID userId = UUID.randomUUID();
            String sourceEventId = "mongo-event-" + UUID.randomUUID();
            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                List<Callable<NotificationResponse>> calls = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    calls.add(() -> service.record(userId, null, "INAPP", "ORDER",
                            "Subject", "Body", sourceEventId));
                }

                List<NotificationResponse> responses = new ArrayList<>();
                for (var future : executor.invokeAll(calls)) {
                    responses.add(future.get());
                }

                assertThat(responses).allMatch(response -> response.id() != null);
                assertThat(responses).extracting(NotificationResponse::id)
                        .containsOnly(responses.get(0).id());
            } finally {
                executor.shutdownNow();
            }

            assertThat(notificationRepository.findBySourceEventId(sourceEventId))
                    .isPresent();
            assertThat(template.findAll(Notification.class)).hasSize(1);
            assertThat(template.findAll(NotificationDelivery.class)).hasSize(1);
            assertThat(template.indexOps(Notification.class).getIndexInfo())
                    .anyMatch(index -> index.isUnique()
                            && index.isSparse()
                            && index.getIndexFields().stream()
                            .anyMatch(field -> "sourceEventId".equals(field.getKey())));
            assertThat(template.indexOps(NotificationDelivery.class).getIndexInfo())
                    .anyMatch(index -> index.isUnique()
                            && index.getIndexFields().stream()
                            .anyMatch(field -> "notificationId".equals(field.getKey())));
        }
    }
}
