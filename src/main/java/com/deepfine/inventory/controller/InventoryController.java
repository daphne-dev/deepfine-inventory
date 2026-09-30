package com.deepfine.inventory.controller;

import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.dto.InboundResponse;
import com.deepfine.inventory.dto.OutboundRequest;
import com.deepfine.inventory.dto.OutboundResponse;
import com.deepfine.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "상품 입고", description = "SKU가 없으면 상품을 등록하고 재고를 증가시킵니다.")
    @ApiResponse(responseCode = "200", description = "입고 완료")
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "409", description = "기존 상품명 불일치 또는 재고 수량 초과")
    @PostMapping("/inbounds")
    public ResponseEntity<InboundResponse> receive(@Valid @RequestBody InboundRequest request) {
        return ResponseEntity.ok(inventoryService.receive(request));
    }

    @Operation(summary = "상품 출고", description = "등록된 상품의 재고를 차감하고 출고 이력을 기록합니다.")
    @ApiResponse(responseCode = "200", description = "출고 완료")
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "404", description = "등록되지 않은 SKU")
    @ApiResponse(responseCode = "409", description = "재고 부족")
    @PostMapping("/outbounds")
    public ResponseEntity<OutboundResponse> ship(@Valid @RequestBody OutboundRequest request) {
        return ResponseEntity.ok(inventoryService.ship(request));
    }
}
