package com.project.cart.client;

import java.math.BigDecimal;
import java.util.List;

/** The catalog snapshot used to price a cart line. */
public record ProductSummary(String id, String sku, String name, List<String> imageUrls,
                             BigDecimal price, boolean active) {}
