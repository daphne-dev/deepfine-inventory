package com.deepfine.inventory.dto;

public record InboundResponse(Integer productId, String sku, String name, int quantity) {
}
