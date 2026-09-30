package com.deepfine.inventory.repository;

import com.deepfine.inventory.domain.Product;
import com.deepfine.inventory.dto.InventoryResponse;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    @Modifying
    @Query(value = """
            INSERT INTO products (sku, name)
            VALUES (:sku, :name)
            ON CONFLICT (sku) DO NOTHING
            """,
            nativeQuery = true)
    int createIfAbsent(@Param("sku") String sku, @Param("name") String name);

    @Query("""
            select new com.deepfine.inventory.dto.InventoryResponse(
                product.id, product.sku, product.name, coalesce(balance.quantity, 0))
            from Product product
            left join InventoryBalance balance on balance.product = product
            where product.sku = :sku
            """)
    Optional<InventoryResponse> findInventoryBySku(@Param("sku") String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.sku = :sku")
    Optional<Product> findBySkuForUpdate(@Param("sku") String sku);
}
