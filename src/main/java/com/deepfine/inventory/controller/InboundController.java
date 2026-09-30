package com.deepfine.inventory.controller;

import com.deepfine.inventory.service.InboundService;
import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.dto.InboundResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/inbounds")
public class InboundController {

    private final InboundService inboundService;

    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    @Operation(summary = "상품 입고", description = "SKU가 없으면 상품을 등록하고 재고를 증가시킵니다.")
    @ApiResponse(responseCode = "200", description = "입고 완료")
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
    @ApiResponse(responseCode = "409", description = "기존 상품명 불일치 또는 재고 수량 초과")
    @PostMapping
    public ResponseEntity<InboundResponse> receive(@Valid @RequestBody InboundRequest request) {
        return ResponseEntity.ok(inboundService.receive(request));
    }
}
