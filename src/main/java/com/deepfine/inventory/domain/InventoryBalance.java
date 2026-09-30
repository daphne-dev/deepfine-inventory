package com.deepfine.inventory.domain;

import com.deepfine.inventory.exception.InventoryRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_balances")
public class InventoryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    protected InventoryBalance() {}

    public InventoryBalance(Product product) {
        this.product = product;
    }

    public void receive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("입고 수량은 양수여야 합니다.");
        }
        try {
            quantity = Math.addExact(quantity, amount);
        } catch (ArithmeticException exception) {
            throw new InventoryRuleViolationException("재고 수량이 허용 범위를 초과합니다.");
        }
    }

    public void ship(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("출고 수량은 양수여야 합니다.");
        }
        if (quantity < amount) {
            throw new InventoryRuleViolationException("재고 수량이 부족합니다.");
        }
        quantity -= amount;
    }

    public Integer getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }
}
