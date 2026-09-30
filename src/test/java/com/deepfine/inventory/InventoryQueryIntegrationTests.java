package com.deepfine.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.dto.OutboundRequest;
import com.deepfine.inventory.exception.ProductNotFoundException;
import com.deepfine.inventory.service.InventoryService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class InventoryQueryIntegrationTests {

    @Autowired private InventoryService inventoryService;

    @Test
    @DisplayName("입고와 출고 후 상품 정보와 현재 재고를 함께 조회한다")
    void getInventoryAfterInboundAndOutbound() {
        // given
        String sku = "QUERY-" + UUID.randomUUID();
        var inbound = inventoryService.receive(new InboundRequest(sku, "상품 A", 5, null));
        inventoryService.ship(new OutboundRequest(sku, 2, null));

        // when
        var inventory = inventoryService.getInventory(sku);

        // then
        assertEquals(inbound.productId(), inventory.productId());
        assertEquals(sku, inventory.sku());
        assertEquals("상품 A", inventory.name());
        assertEquals(3, inventory.quantity());
    }

    @Test
    @DisplayName("등록되지 않은 SKU의 재고 조회는 상품 없음 오류를 반환한다")
    void unknownProductCannotBeQueried() {
        // given
        String sku = "QUERY-" + UUID.randomUUID();

        // when
        var exception =
                assertThrows(
                        ProductNotFoundException.class, () -> inventoryService.getInventory(sku));

        // then
        assertEquals("상품을 찾을 수 없습니다. SKU: " + sku, exception.getMessage());
    }
}
