package com.project.notification.application.mapper;

import com.project.notification.generated.model.NotificationResponse;
import com.project.notification.model.Notification;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    implementationPackage = "com.project.notification.generated.mapper",
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface NotificationMapper {
  NotificationResponse toResponse(Notification notification);
}
