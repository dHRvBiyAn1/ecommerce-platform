package com.project.notification.config;

import com.project.common.sampledata.SampleIds;
import com.project.notification.model.Notification;
import com.project.notification.model.Notification.Status;
import com.project.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seeds ~30 sample notifications spread across customers and categories so the
 * notification list and admin views are non-empty in dev. Mix of READ / SENT /
 * PENDING / FAILED to exercise the UI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements CommandLineRunner {

    private final NotificationRepository repository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        if (repository.count() > 0) {
            log.info("Notifications already populated ({}); skipping", repository.count());
            return;
        }

        var customers = SampleIds.CUSTOMERS;
        int seeded = 0;

        // Order-related notifications, one per seeded order
        for (int i = 0; i < 15; i++) {
            var c = customers.get(i % customers.size());
            String orderNumber = "ORD-SEED-" + (1000 + i);
            seeded += save(c, "ORDER", "EMAIL",
                    "Order " + orderNumber + " confirmed",
                    "Thanks for your order, " + c.displayName() + ". We'll send a tracking link soon.",
                    statusForIndex(i, 0),
                    "seed-order-" + (1000 + i));
        }

        // Payment receipts for the COMPLETED ones (idx 3..11 above)
        for (int i = 3; i <= 11; i++) {
            var c = customers.get(i % customers.size());
            seeded += save(c, "PAYMENT", "EMAIL",
                    "Payment receipt — PAY-SEED-" + (1000 + i),
                    "Your payment of Rs. ?? has been received. View invoice in your account.",
                    Status.SENT,
                    "seed-payment-" + (1000 + i));
        }

        // Inventory low-stock alerts (admin-style, sent to the first seller)
        var seller = SampleIds.SELLERS.get(0);
        for (int i = 0; i < 4; i++) {
            var p = SampleIds.PRODUCTS.get(i * 7 % SampleIds.PRODUCTS.size());
            Notification n = Notification.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(seller.id())
                    .recipient(seller.email())
                    .channel("EMAIL").category("INVENTORY")
                    .subject("Low stock: " + p.name())
                    .body("SKU " + p.sku() + " is below the low-stock threshold of 5.")
                    .status(i % 2 == 0 ? Status.SENT : Status.READ)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now().minusHours(i + 1L))
                    .sentAt(LocalDateTime.now().minusHours(i + 1L))
                    .readAt(i % 2 == 0 ? null : LocalDateTime.now().minusMinutes(20L * i + 5))
                    .sourceEventId("seed-lowstock-" + i)
                    .build();
            repository.save(n);
            seeded++;
        }

        // Welcome notifications for the first few customers
        for (int i = 0; i < 4; i++) {
            var c = customers.get(i);
            seeded += save(c, "ACCOUNT", "EMAIL",
                    "Welcome to the store, " + c.displayName().split(" ")[0] + "!",
                    "Your account is ready. Browse the new arrivals or pick up where you left off.",
                    Status.READ,
                    "seed-welcome-" + i);
        }

        log.info("Seeded {} notifications", seeded);
    }

    private int save(SampleIds.SampleUser c, String category, String channel,
                     String subject, String body, Status status, String sourceEventId) {
        LocalDateTime created = LocalDateTime.now().minusHours((sourceEventId.hashCode() & 0x7f) % 96);
        Notification n = Notification.builder()
                .id(UUID.randomUUID().toString())
                .userId(c.id()).recipient(c.email())
                .channel(channel).category(category)
                .subject(subject).body(body)
                .status(status).retryCount(status == Status.FAILED ? 3 : 0)
                .failureReason(status == Status.FAILED ? "SMTP gateway timeout" : null)
                .createdAt(created)
                .sentAt(status == Status.SENT || status == Status.READ ? created.plusSeconds(5) : null)
                .readAt(status == Status.READ ? created.plusMinutes(15) : null)
                .sourceEventId(sourceEventId)
                .build();
        repository.save(n);
        return 1;
    }

    /**
     * Status mapping by index — gives a mix of SENT / READ / PENDING / FAILED
     * across the 15 orders so the UI shows realistic state diversity.
     */
    private Status statusForIndex(int i, int offset) {
        return switch ((i + offset) % 5) {
            case 0 -> Status.READ;
            case 1, 2 -> Status.SENT;
            case 3 -> Status.PENDING;
            default -> Status.FAILED;
        };
    }
}
