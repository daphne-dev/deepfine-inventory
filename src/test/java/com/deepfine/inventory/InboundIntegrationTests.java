package com.deepfine.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.exception.InventoryRuleViolationException;
import com.deepfine.inventory.service.InventoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class InboundIntegrationTests {

    @Autowired private InventoryService inventoryService;

    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<String> createdSkus = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String sku : createdSkus) {
            jdbcTemplate.update(
                    """
                    DELETE FROM inventory_movements WHERE inventory_balance_id IN (
                        SELECT b.id FROM inventory_balances b JOIN products p ON p.id = b.product_id
                        WHERE p.sku = ?)
                    """,
                    sku);
            jdbcTemplate.update(
                    """
                    DELETE FROM inventory_balances WHERE product_id IN (
                        SELECT id FROM products WHERE sku = ?)
                    """,
                    sku);
            jdbcTemplate.update("DELETE FROM products WHERE sku = ?", sku);
        }
    }

    @Test
    @DisplayName("신규·기존 상품 입고 시 재고와 입고 이력이 함께 누적된다")
    void newAndExistingProductReceiptsKeepBalanceAndHistoryTogether() {
        // given
        String sku = newSku();

        // when
        var first = inventoryService.receive(new InboundRequest(sku, "상품 A", 3, "첫 입고"));
        var second = inventoryService.receive(new InboundRequest(sku, "상품 A", 2, null));

        // then
        assertEquals(3, first.quantity());
        assertEquals(5, second.quantity());
        assertEquals(5, balanceOf(sku));
        assertEquals(2, movementCountOf(sku));
    }

    @Test
    @DisplayName("같은 SKU에 다른 상품명으로 입고하면 재고와 이력이 변경되지 않는다")
    void differentNameForSameSkuDoesNotChangeInventory() {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", 3, null));

        // when
        var exception =
                assertThrows(
                        InventoryRuleViolationException.class,
                        () -> inventoryService.receive(new InboundRequest(sku, "다른 상품", 2, null)));

        // then
        assertEquals("이미 등록된 SKU의 상품명과 일치하지 않습니다.", exception.getMessage());
        assertEquals(3, balanceOf(sku));
        assertEquals(1, movementCountOf(sku));
    }

    @Test
    @DisplayName("입고 후 재고가 정수 범위를 초과하면 재고와 이력이 변경되지 않는다")
    void quantityOverflowDoesNotChangeBalanceOrHistory() {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", Integer.MAX_VALUE, null));

        // when
        var exception =
                assertThrows(
                        InventoryRuleViolationException.class,
                        () -> inventoryService.receive(new InboundRequest(sku, "상품 A", 1, null)));

        // then
        assertEquals("재고 수량이 허용 범위를 초과합니다.", exception.getMessage());
        assertEquals(Integer.MAX_VALUE, balanceOf(sku));
        assertEquals(1, movementCountOf(sku));
    }

    @Test
    @DisplayName("입고 이력 저장이 실패하면 신규 상품과 재고 등록이 롤백된다")
    void movementInsertFailureRollsBackNewProductAndBalance() {
        // given
        String sku = newSku();

        // when
        assertThrows(
                RuntimeException.class,
                () ->
                        inventoryService.receive(
                                new InboundRequest(sku, "상품 A", 3, "x".repeat(501))));

        // then
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM products WHERE sku = ?", Integer.class, sku));
    }

    @Test
    @DisplayName("미등록 SKU의 동시 입고 10건은 상품 하나와 재고·이력 10건을 만든다")
    void simultaneousFirstReceiptsCreateOneProductAndAccumulateAllStock() throws Exception {
        // given
        String sku = newSku();
        int requestCount = 10;

        // when
        int successCount = runConcurrentReceipts(sku, requestCount);

        // then
        assertEquals(requestCount, successCount);
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM products WHERE sku = ?", Integer.class, sku));
        assertEquals(requestCount, balanceOf(sku));
        assertEquals(requestCount, movementCountOf(sku));
    }

    @Test
    @DisplayName("기존 상품의 동시 입고 10건은 재고 증가를 누락하지 않는다")
    void simultaneousReceiptsForExistingProductDoNotLoseUpdates() throws Exception {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", 5, null));
        int requestCount = 10;

        // when
        int successCount = runConcurrentReceipts(sku, requestCount);

        // then
        assertEquals(requestCount, successCount);
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM products WHERE sku = ?", Integer.class, sku));
        assertEquals(5 + requestCount, balanceOf(sku));
        assertEquals(1 + requestCount, movementCountOf(sku));
    }

    private int runConcurrentReceipts(String sku, int requestCount) throws Exception {
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                tasks.add(
                        () -> {
                            ready.countDown();
                            start.await();
                            inventoryService.receive(new InboundRequest(sku, "상품 A", 1, null));
                            return true;
                        });
            }
            List<Future<Boolean>> results = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                results.add(executor.submit(task));
            }
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady);
            int successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            return successCount;
        }
    }

    private String newSku() {
        String sku = "TEST-" + UUID.randomUUID();
        createdSkus.add(sku);
        return sku;
    }

    private int balanceOf(String sku) {
        return jdbcTemplate.queryForObject(
                """
                SELECT b.quantity FROM inventory_balances b
                JOIN products p ON p.id = b.product_id WHERE p.sku = ?
                """,
                Integer.class,
                sku);
    }

    private int movementCountOf(String sku) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM inventory_movements m
                JOIN inventory_balances b ON b.id = m.inventory_balance_id
                JOIN products p ON p.id = b.product_id WHERE p.sku = ?
                """,
                Integer.class,
                sku);
    }
}
