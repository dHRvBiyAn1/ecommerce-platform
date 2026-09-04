package com.project.inventory.application.validator;

import com.project.common.exception.ValidationException;
import com.project.inventory.api.dto.request.InventoryRequest;
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
    void rejectsNonPositiveReservationQuantity() {
        assertThatThrownBy(() -> validator.validateReservation(0, "order-123"))
                .isInstanceOf(ValidationException.class).hasMessage("Quantity must be positive");
    }

    @Test
    void rejectsAnUpdateThatWouldMakeAvailableStockNegative() {
        InventoryItem item = new InventoryItem();
        item.setReservedQuantity(5);
        InventoryRequest request = new InventoryRequest("product-1", "SKU-1", 4, 2, "warehouse-a");
        assertThatThrownBy(() -> validator.validateUpdate(item, request))
                .isInstanceOf(ValidationException.class).hasMessageContaining("reserved quantity");
    }

    @Test
    void acceptsAWellFormedReservationAndSafeUpdate() {
        InventoryItem item = new InventoryItem();
        item.setReservedQuantity(5);
        InventoryRequest request = new InventoryRequest("product-1", "SKU-1", 5, 2, "warehouse-a");
        assertThatCode(() -> validator.validateReservation(2, "order-123_ABC")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateUpdate(item, request)).doesNotThrowAnyException();
    }
}
