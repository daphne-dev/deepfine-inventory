package com.deepfine.inventory.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String sku) {
        super("상품을 찾을 수 없습니다. SKU: " + sku);
    }
}
