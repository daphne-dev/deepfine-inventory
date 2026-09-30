package com.deepfine.inventory.dto;

public record InventoryResponse(Integer productId, String sku, String name, int quantity) {}
