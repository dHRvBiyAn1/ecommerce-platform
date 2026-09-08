package com.project.notification.repository;

import com.project.notification.model.NotificationDelivery;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationDeliveryRepository extends MongoRepository<NotificationDelivery, String> {

    Optional<NotificationDelivery> findByNotificationId(String notificationId);
}
