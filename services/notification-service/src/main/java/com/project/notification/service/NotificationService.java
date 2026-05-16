package com.project.notification.service;

import com.project.notification.event.InventoryEvent;
import com.project.notification.event.OrderEvent;
import com.project.notification.event.PaymentEvent;
import com.project.notification.event.UserEvent;
import com.project.notification.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final ConcurrentHashMap<String, Notification> notificationStore = new ConcurrentHashMap<>();

    public void handleUserEvent(UserEvent event) {
        log.info("Handling user event: type={}, userId={}", event.getType(), event.getUserId());

        switch (event.getType().toUpperCase()) {
            case "CREATED" -> {
                String subject = "Welcome to our ecommerce platform!";
                String body = String.format(
                        "Hello %s,<br><br>Your account has been created successfully. " +
                        "We're excited to have you on board!<br><br>Best regards,<br>The Ecommerce Team",
                        event.getUsername() != null ? event.getUsername() : event.getUserId());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "PASSWORD_CHANGED" -> {
                String subject = "Password Changed Successfully";
                String body = "Your password has been changed successfully. If you did not request this change, please contact support immediately.";
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "EMAIL_VERIFIED" -> {
                String subject = "Email Verified Successfully";
                String body = "Your email address has been verified successfully. You can now access all features of our platform.";
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "PROFILE_UPDATED" -> {
                String subject = "Profile Updated";
                String body = "Your profile information has been updated successfully.";
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "DELETED" -> {
                log.warn("User account deleted: userId={}", event.getUserId());
                String subject = "Account Deleted";
                String body = "Your account has been deleted as requested. We're sorry to see you go.";
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            default -> log.warn("Unknown user event type: {}", event.getType());
        }
    }

    public void handleOrderEvent(OrderEvent event) {
        log.info("Handling order event: type={}, orderId={}, userId={}", event.getType(), event.getOrderId(), event.getUserId());

        switch (event.getType().toUpperCase()) {
            case "CREATED" -> {
                String subject = "Order Confirmation - #" + event.getOrderId();
                String body = String.format(
                        "Thank you for your order!<br><br>" +
                        "Order ID: %s<br>" +
                        "Total Amount: $%s<br>" +
                        "Status: %s<br><br>" +
                        "We'll notify you when your order ships.",
                        event.getOrderId(), event.getTotalAmount(), event.getStatus());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "SHIPPED" -> {
                String subject = "Order Shipped - #" + event.getOrderId();
                String body = String.format(
                        "Great news! Your order #%s has been shipped.<br><br>" +
                        "Total Amount: $%s<br><br>" +
                        "Track your delivery on our website.",
                        event.getOrderId(), event.getTotalAmount());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "DELIVERED" -> {
                String subject = "Order Delivered - #" + event.getOrderId();
                String body = String.format(
                        "Your order #%s has been delivered. We hope you love your purchase!<br><br>" +
                        "Please leave a review on our website.",
                        event.getOrderId());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "CANCELLED" -> {
                String subject = "Order Cancelled - #" + event.getOrderId();
                String body = String.format(
                        "Your order #%s has been cancelled as requested.<br><br>" +
                        "If you have any questions, please contact our support team.",
                        event.getOrderId());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            default -> log.warn("Unknown order event type: {}", event.getType());
        }
    }

    public void handleInventoryEvent(InventoryEvent event) {
        log.info("Handling inventory event: type={}, productId={}, sku={}", event.getType(), event.getProductId(), event.getSku());

        switch (event.getType().toUpperCase()) {
            case "LOW_STOCK" -> {
                String subject = "Low Stock Alert - " + event.getProductName();
                String body = String.format(
                        "Low stock alert for product: %s<br><br>" +
                        "SKU: %s<br>" +
                        "Product ID: %s<br>" +
                        "Remaining Stock: %d<br><br>" +
                        "Please restock soon to avoid stockouts.",
                        event.getProductName(), event.getSku(), event.getProductId(), event.getRemainingStock());
                log.warn("LOW STOCK: product={}, sku={}, remaining={}", event.getProductName(), event.getSku(), event.getRemainingStock());
                Notification notification = Notification.builder()
                        .id(UUID.randomUUID().toString())
                        .userId("SELLER")
                        .type("EMAIL")
                        .subject(subject)
                        .body(body)
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationStore.put(notification.getId(), notification);
                log.info("Low stock alert created for product: {}", event.getProductName());
            }
            case "OUT_OF_STOCK" -> {
                String subject = "Out of Stock Alert - " + event.getProductName();
                String body = String.format(
                        "Product is now out of stock: %s<br><br>" +
                        "SKU: %s<br>" +
                        "Product ID: %s<br><br>" +
                        "Immediate restocking is required.",
                        event.getProductName(), event.getSku(), event.getProductId());
                log.warn("OUT OF STOCK: product={}, sku={}", event.getProductName(), event.getSku());
                Notification notification = Notification.builder()
                        .id(UUID.randomUUID().toString())
                        .userId("SELLER")
                        .type("EMAIL")
                        .subject(subject)
                        .body(body)
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationStore.put(notification.getId(), notification);
            }
            case "RESTOCKED" -> {
                String subject = "Product Restocked - " + event.getProductName();
                String body = String.format(
                        "Product has been restocked: %s<br><br>" +
                        "SKU: %s<br>" +
                        "New Quantity: %d",
                        event.getProductName(), event.getSku(), event.getQuantity());
                log.info("RESTOCKED: product={}, sku={}, quantity={}", event.getProductName(), event.getSku(), event.getQuantity());
                Notification notification = Notification.builder()
                        .id(UUID.randomUUID().toString())
                        .userId("SELLER")
                        .type("EMAIL")
                        .subject(subject)
                        .body(body)
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationStore.put(notification.getId(), notification);
            }
            default -> log.warn("Unknown inventory event type: {}", event.getType());
        }
    }

    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Handling payment event: type={}, paymentId={}, orderId={}", event.getType(), event.getPaymentId(), event.getOrderId());

        switch (event.getType().toUpperCase()) {
            case "COMPLETED" -> {
                String subject = "Payment Receipt - Order #" + event.getOrderId();
                String body = String.format(
                        "Your payment has been completed successfully.<br><br>" +
                        "Payment ID: %s<br>" +
                        "Order ID: %s<br>" +
                        "Amount: %s %s<br><br>" +
                        "Thank you for your purchase!",
                        event.getPaymentId(), event.getOrderId(), event.getCurrency(), event.getAmount());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "FAILED" -> {
                String subject = "Payment Failed - Order #" + event.getOrderId();
                String body = String.format(
                        "Your payment for order #%s has failed.<br><br>" +
                        "Payment ID: %s<br>" +
                        "Amount: %s %s<br><br>" +
                        "Please try again or use a different payment method.",
                        event.getOrderId(), event.getPaymentId(), event.getCurrency(), event.getAmount());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "REFUNDED" -> {
                String subject = "Payment Refunded - Order #" + event.getOrderId();
                String body = String.format(
                        "Your payment for order #%s has been refunded.<br><br>" +
                        "Payment ID: %s<br>" +
                        "Refund Amount: %s %s<br><br>" +
                        "The refund should appear in your account within 5-10 business days.",
                        event.getOrderId(), event.getPaymentId(), event.getCurrency(), event.getAmount());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            case "PENDING" -> {
                String subject = "Payment Pending - Order #" + event.getOrderId();
                String body = String.format(
                        "Your payment for order #%s is currently being processed.<br><br>" +
                        "Payment ID: %s<br>" +
                        "Amount: %s %s<br><br>" +
                        "We'll notify you once the payment is confirmed.",
                        event.getOrderId(), event.getPaymentId(), event.getCurrency(), event.getAmount());
                sendAndStore(event.getUserId(), event.getEmail(), "EMAIL", subject, body);
            }
            default -> log.warn("Unknown payment event type: {}", event.getType());
        }
    }

    private void sendAndStore(String userId, String email, String type, String subject, String body) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .type(type)
                .subject(subject)
                .body(body)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        try {
            emailService.sendEmail(email, subject, body);
            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
            log.info("Notification sent: id={}, userId={}, type={}, subject={}", notification.getId(), userId, type, subject);
        } catch (Exception e) {
            notification.setStatus("FAILED");
            log.error("Notification failed: id={}, userId={}, type={}, subject={}, error={}",
                    notification.getId(), userId, type, subject, e.getMessage());
        }

        notificationStore.put(notification.getId(), notification);
    }
}
