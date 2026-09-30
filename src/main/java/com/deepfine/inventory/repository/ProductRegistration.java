package com.deepfine.inventory.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRegistration {

    private final EntityManager entityManager;

    public ProductRegistration(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void createIfAbsent(String sku, String name) {
        entityManager.createNativeQuery("""
                INSERT INTO products (sku, name)
                VALUES (:sku, :name)
                ON CONFLICT (sku) DO NOTHING
                """)
                .setParameter("sku", sku)
                .setParameter("name", name)
                .executeUpdate();
    }
}
