package com.deepfine.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.dto.OutboundRequest;
import com.deepfine.inventory.exception.InventoryRuleViolationException;
import com.deepfine.inventory.exception.ProductNotFoundException;
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
class OutboundIntegrationTests {

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
    @DisplayName("정상 출고는 재고를 차감하고 차감 후 수량을 출고 이력에 기록한다")
    void shipmentDecreasesBalanceAndRecordsMovement() {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", 10, null));

        // when
        var response = inventoryService.ship(new OutboundRequest(sku, 3, "주문 출고"));

        // then
        assertEquals(7, response.quantity());
        assertEquals(7, balanceOf(sku));
        assertEquals(1, outboundCountOf(sku));
        assertEquals(7, outboundBalanceAfterOf(sku));
    }

    @Test
    @DisplayName("등록되지 않은 SKU는 출고할 수 없다")
    void unknownProductCannotBeShipped() {
        // given
        String sku = newSku();

        // when
        var exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> inventoryService.ship(new OutboundRequest(sku, 1, null)));

        // then
        assertEquals("상품을 찾을 수 없습니다. SKU: " + sku, exception.getMessage());
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM products WHERE sku = ?", Integer.class, sku));
    }

    @Test
    @DisplayName("재고보다 많은 수량을 출고하면 재고와 이력이 변경되지 않는다")
    void insufficientStockDoesNotChangeBalanceOrHistory() {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", 2, null));

        // when
        var exception =
                assertThrows(
                        InventoryRuleViolationException.class,
                        () -> inventoryService.ship(new OutboundRequest(sku, 3, null)));

        // then
        assertEquals("재고 수량이 부족합니다.", exception.getMessage());
        assertEquals(2, balanceOf(sku));
        assertEquals(0, outboundCountOf(sku));
    }

    @Test
    @DisplayName("출고 이력 저장이 실패하면 재고 차감도 롤백된다")
    void movementFailureRollsBackShipment() {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", 5, null));

        // when
        assertThrows(
                RuntimeException.class,
                () -> inventoryService.ship(new OutboundRequest(sku, 1, "x".repeat(501))));

        // then
        assertEquals(5, balanceOf(sku));
        assertEquals(0, outboundCountOf(sku));
    }

    @Test
    @DisplayName("재고 5개에 동시 출고 12건을 요청하면 5건만 성공하고 재고는 0이 된다")
    void simultaneousShipmentsNeverMakeStockNegative() throws Exception {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", 5, null));
        int requestCount = 12;
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            requests.add(
                    () -> {
                        try {
                            inventoryService.ship(new OutboundRequest(sku, 1, null));
                            return true;
                        } catch (InventoryRuleViolationException exception) {
                            return false;
                        }
                    });
        }

        // when
        List<Boolean> results = executeConcurrently(requests);
        long successCount = results.stream().filter(Boolean::booleanValue).count();
        long shortageCount = results.size() - successCount;

        // then
        assertEquals(5, successCount);
        assertEquals(7, shortageCount);
        assertEquals(0, balanceOf(sku));
        assertEquals(5, outboundCountOf(sku));
    }

    @Test
    @DisplayName("같은 상품의 입고·출고가 동시에 처리되어도 최종 재고와 이력이 일치한다")
    void simultaneousInboundAndOutboundPreserveStock() throws Exception {
        // given
        String sku = newSku();
        inventoryService.receive(new InboundRequest(sku, "상품 A", 6, null));
        List<Callable<Boolean>> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(
                    () -> {
                        inventoryService.receive(new InboundRequest(sku, "상품 A", 1, null));
                        return true;
                    });
            requests.add(
                    () -> {
                        inventoryService.ship(new OutboundRequest(sku, 1, null));
                        return true;
                    });
        }

        // when
        List<Boolean> results = executeConcurrently(requests);

        // then
        assertEquals(12, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(6, balanceOf(sku));
        assertEquals(6, outboundCountOf(sku));
        assertEquals(7, inboundCountOf(sku));
    }

    private List<Boolean> executeConcurrently(List<Callable<Boolean>> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requests.size())) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> request : requests) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return request.call();
                                }));
            }
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady);
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
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

    private int outboundCountOf(String sku) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM inventory_movements m
                JOIN inventory_balances b ON b.id = m.inventory_balance_id
                JOIN products p ON p.id = b.product_id
                WHERE p.sku = ? AND m.movement_type = 'OUTBOUND'
                """,
                Integer.class,
                sku);
    }

    private int inboundCountOf(String sku) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM inventory_movements m
                JOIN inventory_balances b ON b.id = m.inventory_balance_id
                JOIN products p ON p.id = b.product_id
                WHERE p.sku = ? AND m.movement_type = 'INBOUND'
                """,
                Integer.class,
                sku);
    }

    private int outboundBalanceAfterOf(String sku) {
        return jdbcTemplate.queryForObject(
                """
                SELECT m.balance_after FROM inventory_movements m
                JOIN inventory_balances b ON b.id = m.inventory_balance_id
                JOIN products p ON p.id = b.product_id
                WHERE p.sku = ? AND m.movement_type = 'OUTBOUND'
                """,
                Integer.class,
                sku);
    }
}
