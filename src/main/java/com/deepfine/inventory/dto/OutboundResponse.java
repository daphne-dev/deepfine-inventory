package com.deepfine.inventory.dto;

public record OutboundResponse(Integer productId, String sku, String name, int quantity) {}
