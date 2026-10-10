package com.project.notification.repository;

import com.project.notification.model.NotificationDelivery;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationDeliveryRepository
    extends MongoRepository<NotificationDelivery, String> {

  Optional<NotificationDelivery> findByNotificationId(String notificationId);
}
