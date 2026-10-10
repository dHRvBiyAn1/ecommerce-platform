package com.project.notification.application.validator;

import com.project.common.exception.ForbiddenOperationException;
import com.project.notification.model.Notification;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationAccessValidator {

  public void validateAccess(Notification notification, UUID requesterId, boolean administrator) {
    if (!administrator && !Objects.equals(notification.getUserId(), requesterId)) {
      throw new ForbiddenOperationException(
          "You do not have permission to modify this notification");
    }
  }
}
