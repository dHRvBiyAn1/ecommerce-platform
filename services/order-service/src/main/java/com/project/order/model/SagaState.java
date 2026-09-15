package com.project.order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaState {

    public enum Stage {
        RESERVING,
        COMPENSATING,
        RETRYABLE,
        COMPLETED
    }

    public enum ResourceType {
        INVENTORY,
        COUPON
    }

    public enum Workflow {
        CHECKOUT,
        PAYMENT_COMPLETION,
        PAYMENT_FAILURE
    }

    public enum Action {
        RESERVE,
        COMMIT,
        RELEASE
    }

    public enum OperationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED
    }

    private Stage stage;
    private Workflow workflow;
    private int attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private String paymentId;

    @Builder.Default
    private List<Operation> operations = new ArrayList<>();

    @Builder.Default
    private List<CompensationCommand> compensationCommands = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompensationCommand {
        private ResourceType resourceType;
        private String resourceId;
        private int quantity;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Operation {
        private String id;
        private ResourceType resourceType;
        private Action action;
        private String resourceId;
        private int quantity;
        private OperationStatus status;
        private String leaseToken;
        private LocalDateTime leaseUntil;
    }
}
