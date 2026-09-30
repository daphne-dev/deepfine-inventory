package com.deepfine.inventory.domain;

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
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_balance_id", nullable = false)
    private InventoryBalance balance;

    @Column(name = "movement_type", nullable = false, length = 16)
    private String movementType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 500)
    private String reason;

    protected InventoryMovement() {
    }

    private InventoryMovement(InventoryBalance balance, int quantity, String reason) {
        this.balance = balance;
        this.movementType = "INBOUND";
        this.quantity = quantity;
        this.balanceAfter = balance.getQuantity();
        this.reason = reason;
    }

    public static InventoryMovement inbound(InventoryBalance balance, int quantity, String reason) {
        return new InventoryMovement(balance, quantity, reason);
    }
}
