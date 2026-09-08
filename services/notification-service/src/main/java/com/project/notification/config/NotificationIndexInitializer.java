package com.project.notification.config;

import com.project.notification.model.Notification;
import com.project.notification.model.NotificationDelivery;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void initialize() {
        mongoTemplate.indexOps(Notification.class)
                .ensureIndex(new Index().on("sourceEventId", Sort.Direction.ASC).unique().sparse());
        mongoTemplate.indexOps(NotificationDelivery.class)
                .ensureIndex(new Index().on("notificationId", Sort.Direction.ASC).unique());
    }
}
