package com.deepfine.inventory.service;

import com.deepfine.inventory.domain.InventoryBalance;
import com.deepfine.inventory.domain.InventoryMovement;
import com.deepfine.inventory.domain.Product;
import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.dto.InboundResponse;
import com.deepfine.inventory.dto.InventoryResponse;
import com.deepfine.inventory.dto.OutboundRequest;
import com.deepfine.inventory.dto.OutboundResponse;
import com.deepfine.inventory.exception.InventoryRuleViolationException;
import com.deepfine.inventory.exception.ProductNotFoundException;
import com.deepfine.inventory.repository.InventoryBalanceRepository;
import com.deepfine.inventory.repository.InventoryMovementRepository;
import com.deepfine.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryService(
            ProductRepository productRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.balanceRepository = balanceRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public InboundResponse receive(InboundRequest request) {
        productRepository.createIfAbsent(request.sku(), request.name());
        Product product =
                productRepository
                        .findBySkuForUpdate(request.sku())
                        .orElseThrow(() -> new IllegalStateException("등록한 상품을 찾을 수 없습니다."));
        product.requireName(request.name());

        InventoryBalance balance =
                balanceRepository
                        .findByProductId(product.getId())
                        .orElseGet(() -> new InventoryBalance(product));
        balance.receive(request.quantity());
        balanceRepository.saveAndFlush(balance);
        movementRepository.save(
                InventoryMovement.inbound(balance, request.quantity(), request.reason()));

        return new InboundResponse(
                product.getId(), product.getSku(), product.getName(), balance.getQuantity());
    }

    @Transactional
    public OutboundResponse ship(OutboundRequest request) {
        Product product =
                productRepository
                        .findBySkuForUpdate(request.sku())
                        .orElseThrow(() -> new ProductNotFoundException(request.sku()));
        InventoryBalance balance =
                balanceRepository
                        .findByProductId(product.getId())
                        .orElseThrow(() -> new InventoryRuleViolationException("재고 수량이 부족합니다."));

        balance.ship(request.quantity());
        balanceRepository.saveAndFlush(balance);
        movementRepository.save(
                InventoryMovement.outbound(balance, request.quantity(), request.reason()));

        return new OutboundResponse(
                product.getId(), product.getSku(), product.getName(), balance.getQuantity());
    }

    public InventoryResponse getInventory(String sku) {
        return productRepository
                .findInventoryBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }
}
