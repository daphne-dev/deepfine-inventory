package com.deepfine.inventory.service;

import com.deepfine.inventory.domain.InventoryBalance;
import com.deepfine.inventory.domain.InventoryMovement;
import com.deepfine.inventory.domain.Product;
import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.dto.InboundResponse;
import com.deepfine.inventory.repository.InventoryBalanceRepository;
import com.deepfine.inventory.repository.InventoryMovementRepository;
import com.deepfine.inventory.repository.ProductRegistration;
import com.deepfine.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundService {

    private final ProductRegistration productRegistration;
    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;

    public InboundService(ProductRegistration productRegistration, ProductRepository productRepository,
            InventoryBalanceRepository balanceRepository, InventoryMovementRepository movementRepository) {
        this.productRegistration = productRegistration;
        this.productRepository = productRepository;
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public InboundResponse receive(InboundRequest request) {
        productRegistration.createIfAbsent(request.sku(), request.name());
        Product product = productRepository.findBySkuForUpdate(request.sku())
                .orElseThrow(() -> new IllegalStateException("등록한 상품을 찾을 수 없습니다."));
        product.requireName(request.name());

        InventoryBalance balance = balanceRepository.findByProductId(product.getId())
                .orElseGet(() -> new InventoryBalance(product));
        balance.receive(request.quantity());
        balanceRepository.saveAndFlush(balance);
        movementRepository.save(InventoryMovement.inbound(balance, request.quantity(), request.reason()));

        return new InboundResponse(product.getId(), product.getSku(), product.getName(), balance.getQuantity());
    }
}
