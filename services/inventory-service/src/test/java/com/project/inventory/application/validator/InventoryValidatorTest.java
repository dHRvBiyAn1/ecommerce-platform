package com.project.inventory.application.validator;

import com.project.common.exception.ValidationException;
import com.project.inventory.generated.model.InventoryRequest;
import com.project.inventory.domain.model.InventoryItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryValidatorTest {
    private final InventoryValidator validator = new InventoryValidator();

    @Test
    void rejectsOrderIdsThatCannotBeUsedAsMongoReservationKeys() {
        assertThatThrownBy(() -> validator.validateReservation(2, "order.with.dot"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Order ID");
    }

    @Test
    void rejectsMissingBlankAndOverlongOrderIdsAfterAcceptingPositiveQuantity() {
        String overlongOrderId = "a".repeat(65);

        assertThatThrownBy(() -> validator.validateReservation(1, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Order ID must contain only letters, numbers, underscores, or hyphens");
        assertThatThrownBy(() -> validator.validateReservation(1, "   "))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Order ID must contain only letters, numbers, underscores, or hyphens");
        assertThatThrownBy(() -> validator.validateReservation(1, overlongOrderId))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Order ID must contain only letters, numbers, underscores, or hyphens");
    }

    @Test
    void rejectsNonPositiveReservationQuantity() {
        assertThatThrownBy(() -> validator.validateReservation(0, "order-123"))
                .isInstanceOf(ValidationException.class).hasMessage("Quantity must be positive");
    }

    @Test
    void rejectsAnUpdateThatWouldMakeAvailableStockNegative() {
        InventoryItem item = new InventoryItem();
        item.setReservedQuantity(5);
        InventoryRequest request = new InventoryRequest().productId("product-1").sku("SKU-1").quantity(4).lowStockThreshold(2).location("warehouse-a");
        assertThatThrownBy(() -> validator.validateUpdate(item, request))
                .isInstanceOf(ValidationException.class).hasMessageContaining("reserved quantity");
    }

    @Test
    void rejectsMissingOrNullQuantityWhenStockIsReserved() {
        InventoryItem item = new InventoryItem();
        item.setReservedQuantity(5);
        InventoryRequest missingQuantity = new InventoryRequest().productId("product-1").sku("SKU-1");
        InventoryRequest nullQuantity = new InventoryRequest().productId("product-1").sku("SKU-1").quantity(null);

        assertThatThrownBy(() -> validator.validateUpdate(item, missingQuantity))
                .isInstanceOf(ValidationException.class).hasMessageContaining("reserved quantity");
        assertThatThrownBy(() -> validator.validateUpdate(item, nullQuantity))
                .isInstanceOf(ValidationException.class).hasMessageContaining("reserved quantity");
    }

    @Test
    void acceptsMissingQuantityWhenNoStockIsReserved() {
        InventoryItem item = new InventoryItem();
        item.setReservedQuantity(0);
        InventoryRequest missingQuantity = new InventoryRequest().productId("product-1").sku("SKU-1");
        InventoryRequest nullQuantity = new InventoryRequest().productId("product-1").sku("SKU-1").quantity(null);

        assertThatCode(() -> validator.validateUpdate(item, missingQuantity)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateUpdate(item, nullQuantity)).doesNotThrowAnyException();
    }

    @Test
    void acceptsAWellFormedReservationAndSafeUpdate() {
        InventoryItem item = new InventoryItem();
        item.setReservedQuantity(5);
        InventoryRequest request = new InventoryRequest().productId("product-1").sku("SKU-1").quantity(5).lowStockThreshold(2).location("warehouse-a");
        assertThatCode(() -> validator.validateReservation(2, "order-123_ABC")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateUpdate(item, request)).doesNotThrowAnyException();
    }
}
