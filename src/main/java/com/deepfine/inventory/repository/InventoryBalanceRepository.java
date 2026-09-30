package com.deepfine.inventory.repository;

import com.deepfine.inventory.domain.InventoryBalance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Integer> {

    Optional<InventoryBalance> findByProductId(Integer productId);
}
