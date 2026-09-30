package com.deepfine.inventory.domain;

import com.deepfine.inventory.exception.InventoryRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    protected Product() {
    }

    public Integer getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public void requireName(String requestedName) {
        if (!name.equals(requestedName)) {
            throw new InventoryRuleViolationException("이미 등록된 SKU의 상품명과 일치하지 않습니다.");
        }
    }
}
