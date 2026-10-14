package com.haucheng.erp;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.dto.*;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.OperationLogMapper;
import com.haucheng.erp.service.*;
import com.haucheng.erp.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="ERP_MYSQL_TEST", matches="true")
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "erp.auth.seed-username=", "erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class Phase7MySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired StocktakeService stocktakes;
    @Autowired OpeningBalanceService openings;
    @Autowired InventoryOrderService orders;
    @Autowired AssetDeviceService devices;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean OperationLogMapper audit;

    private String prefix;
    private Long category, brand, unit, quantityProduct, deviceProduct, otherProduct;
    private Long department, outsideDepartment, operator, warehouse, outsideWarehouse;

    @BeforeEach void setup() {
        prefix = "P7_" + UUID.randomUUID().toString().replace("-", "");
        category = id("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)", prefix, prefix);
        brand = id("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)", prefix, prefix);
        unit = id("INSERT INTO md_units(unit_code,unit_name,precision_scale) VALUES(?,?,4)", prefix, prefix);
        quantityProduct = product("Q", "QUANTITY");
        deviceProduct = product("D", "DEVICE");
        otherProduct = product("O", "DEVICE");
        department = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix, prefix);
        outsideDepartment = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix + "O", prefix + "O");
        operator = id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)",
                prefix, "unused-test-hash", prefix, department);
        warehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)",
                prefix + "W", prefix + "W", department);
        outsideWarehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)",
                prefix + "O", prefix + "O", outsideDepartment);
        current("ALL", department);
    }

    @AfterEach void cleanup() {
        reset(audit);
        CurrentUserContext.clear();
        // Only rows linked to this test's generated IDs are removed.
        jdbc.update("DELETE FROM inv_stocktake_device_checks WHERE stocktake_id IN (SELECT id FROM inv_stocktakes WHERE warehouse_id IN (?,?))", warehouse, outsideWarehouse);
        jdbc.update("DELETE FROM inv_stocktake_items WHERE stocktake_id IN (SELECT id FROM inv_stocktakes WHERE warehouse_id IN (?,?))", warehouse, outsideWarehouse);
        jdbc.update("DELETE FROM inv_stocktakes WHERE warehouse_id IN (?,?)", warehouse, outsideWarehouse);
        for (String kind : List.of("inbound", "outbound")) {
            jdbc.update("DELETE c FROM inv_order_device_claims c JOIN inv_" + kind + "_devices d ON d.id=c." + kind + "_device_id JOIN inv_" + kind + "_orders o ON o.id=d.order_id WHERE o.warehouse_id IN (?,?)", warehouse, outsideWarehouse);
            jdbc.update("DELETE d FROM inv_" + kind + "_devices d JOIN inv_" + kind + "_orders o ON o.id=d.order_id WHERE o.warehouse_id IN (?,?)", warehouse, outsideWarehouse);
            jdbc.update("DELETE i FROM inv_" + kind + "_items i JOIN inv_" + kind + "_orders o ON o.id=i.order_id WHERE o.warehouse_id IN (?,?)", warehouse, outsideWarehouse);
            jdbc.update("DELETE FROM inv_" + kind + "_orders WHERE warehouse_id IN (?,?)", warehouse, outsideWarehouse);
        }
        List<Long> openingIds = jdbc.queryForList("SELECT id FROM inv_opening_balances WHERE warehouse_id IN (?,?)", Long.class, warehouse, outsideWarehouse);
        for (Long opening : openingIds) {
            jdbc.update("DELETE FROM inv_opening_balance_devices WHERE opening_id=?", opening);
            jdbc.update("DELETE FROM inv_opening_balance_items WHERE opening_id=?", opening);
            jdbc.update("DELETE FROM inv_opening_balances WHERE id=?", opening);
        }
        jdbc.update("DELETE FROM inv_stock_ledgers WHERE product_id IN (?,?,?)", quantityProduct, deviceProduct, otherProduct);
        jdbc.update("DELETE FROM inv_stocks WHERE product_id IN (?,?,?)", quantityProduct, deviceProduct, otherProduct);
        jdbc.update("DELETE FROM asset_device_history WHERE device_id IN (SELECT id FROM asset_devices WHERE product_id IN (?,?))", deviceProduct, otherProduct);
        jdbc.update("DELETE FROM asset_devices WHERE product_id IN (?,?)", deviceProduct, otherProduct);
        jdbc.update("DELETE FROM sys_operation_log WHERE operator_user_id=?", operator);
        jdbc.update("DELETE FROM sys_access_token WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user WHERE id=?", operator);
        jdbc.update("DELETE FROM wh_warehouses WHERE id IN (?,?)", warehouse, outsideWarehouse);
        jdbc.update("DELETE FROM sys_department WHERE id IN (?,?)", department, outsideDepartment);
        jdbc.update("DELETE FROM md_products WHERE category_id=?", category);
        jdbc.update("DELETE FROM md_categories WHERE id=?", category);
        jdbc.update("DELETE FROM md_brands WHERE id=?", brand);
        jdbc.update("DELETE FROM md_units WHERE id=?", unit);
    }

    @Test void draftCountingConfirmationAndQuantityGainAreVersioned() {
        openQuantity("10");
        StocktakeRequest request = request(warehouse, quantityProduct);
        StocktakeView draft = stocktakes.create(request);
        assertEquals("DRAFT", draft.getStatus());
        assertThrows(RuntimeException.class, () -> stocktakes.create(request));
        request.setVersion(draft.getVersion()); request.setRemark("checked");
        StocktakeView edited = stocktakes.update(draft.getId(), request);
        assertEquals("checked", edited.getRemark());
        assertThrows(BusinessException.class, () -> stocktakes.update(edited.getId(), request));
        StocktakeView counting = stocktakes.submit(edited.getId(), action(edited));
        assertEquals("COUNTING", counting.getStatus());
        assertAmount("10", counting.getItems().get(0).getSnapshotQuantity());
        assertThrows(BusinessException.class, () -> stocktakes.update(counting.getId(), request));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), count(counting, "13.5"));
        assertEquals("COUNTED", counted.getStatus());
        assertAmount("3.5", counted.getItems().get(0).getDifferenceQuantity());
        StocktakeView confirmed = stocktakes.confirm(counted.getId(), action(counted));
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertQuantity(quantityProduct, "13.5");
        assertEquals(1, confirmed.getLedgers().size());
        assertEquals("IN", confirmed.getLedgers().get(0).getDirection());
        assertThrows(BusinessException.class, () -> stocktakes.confirm(confirmed.getId(), action(counted)));
        assertThrows(BusinessException.class, () -> stocktakes.cancel(confirmed.getId(), action(confirmed)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inv_stock_ledgers WHERE business_type='STOCKTAKE' AND business_no=?", Integer.class, confirmed.getStocktakeNo()));
    }

    @Test void quantityLossZeroDifferenceAndNegativeCountGuard() {
        openQuantity("10");
        StocktakeView counting = stocktakes.submit(stocktakes.create(request(warehouse, quantityProduct)).getId(), actionVersion(0));
        StocktakeCountRequest negative = count(counting, "-1");
        assertThrows(BusinessException.class, () -> stocktakes.recordCount(counting.getId(), itemId(counting), negative));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), count(counting, "7"));
        StocktakeView confirmed = stocktakes.confirm(counted.getId(), action(counted));
        assertQuantity(quantityProduct, "7");
        assertEquals("OUT", confirmed.getLedgers().get(0).getDirection());
        StocktakeView zero = stocktakes.create(request(warehouse, quantityProduct));
        zero = stocktakes.submit(zero.getId(), action(zero));
        zero = stocktakes.recordCount(zero.getId(), itemId(zero), count(zero, "7"));
        zero = stocktakes.confirm(zero.getId(), action(zero));
        assertEquals(0, zero.getLedgers().size());
        assertQuantity(quantityProduct, "7");
    }

    @Test void emptyBookIsNotMaterializedUntilConfirmation() {
        StocktakeView draft = stocktakes.create(request(warehouse, quantityProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        assertAmount("0", counting.getItems().get(0).getSnapshotQuantity());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inv_stocks WHERE warehouse_id=? AND product_id=?", Integer.class, warehouse, quantityProduct));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), count(counting, "4"));
        StocktakeView confirmed = stocktakes.confirm(counted.getId(), action(counted));
        assertQuantity(quantityProduct, "4");
        assertEquals(1, confirmed.getLedgers().size());
    }

    @Test void deviceChecksRecordAnomaliesAndOnlyConfirmedMissingChangesInventory() {
        Long first = device(deviceProduct), second = device(deviceProduct);
        openDevices(first, second);
        StocktakeView draft = stocktakes.create(request(warehouse, deviceProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        String firstCode = code(first);
        StocktakeView anomalous = stocktakes.recordCount(counting.getId(), itemId(counting), countDevices(counting, firstCode, prefix + "UNKNOWN"));
        assertEquals(Set.of("MATCH", "MISSING", "UNREGISTERED"), differenceCodes(anomalous));
        assertThrows(BusinessException.class, () -> stocktakes.confirm(anomalous.getId(), action(anomalous)));
        assertQuantity(deviceProduct, "2");
        StocktakeView counted = stocktakes.recordCount(anomalous.getId(), itemId(anomalous), countDevices(anomalous, firstCode));
        StocktakeView confirmed = stocktakes.confirm(counted.getId(), action(counted));
        assertEquals("MISSING", jdbc.queryForObject("SELECT status FROM asset_devices WHERE id=?", String.class, second));
        assertEquals(warehouse, jdbc.queryForObject("SELECT warehouse_id FROM asset_devices WHERE id=?", Long.class, second));
        assertQuantity(deviceProduct, "1");
        assertEquals("OUT", confirmed.getLedgers().get(0).getDirection());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE device_id=? AND business_type='STOCKTAKE'", Integer.class, second));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inv_stocks s WHERE s.product_id=? AND s.quantity<>(SELECT COUNT(*) FROM asset_devices d WHERE d.warehouse_id=s.warehouse_id AND d.product_id=s.product_id AND d.status='IN_STOCK')", Integer.class, deviceProduct));
    }

    @Test void wrongProductOutsideWarehouseAbnormalStatusAndExtraAreStructuredAndBlocked() {
        Long expected = device(deviceProduct);
        openDevices(expected);
        Long wrong = device(otherProduct), outside = device(deviceProduct);
        Long abnormal = id("INSERT INTO asset_devices(device_code,product_id,status,warehouse_id) VALUES(?,?,'SHIPPED',?)", prefix + "ABNORMAL", deviceProduct, warehouse);
        StocktakeView draft = stocktakes.create(request(warehouse, deviceProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        Long extra = id("INSERT INTO asset_devices(device_code,product_id,status,warehouse_id) VALUES(?,?,'IN_STOCK',?)", prefix + "EXTRA", deviceProduct, warehouse);
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), countDevices(counting,
                code(expected), code(wrong), code(outside), code(abnormal), code(extra)));
        assertEquals(Set.of("MATCH", "WRONG_PRODUCT", "OUTSIDE_WAREHOUSE", "STATUS_ABNORMAL", "EXTRA"), differenceCodes(counted));
        assertThrows(BusinessException.class, () -> stocktakes.confirm(counted.getId(), action(counted)));
        assertEquals("COUNTED", stocktakes.getById(counted.getId()).getStatus());
        assertQuantity(deviceProduct, "1");
    }

    @Test void crossItemWrongProductRemainsARecordedDifference() {
        Long expected = device(deviceProduct);
        openDevices(expected);
        StocktakeView draft = stocktakes.create(request(warehouse, deviceProduct, otherProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        Long otherItem = counting.getItems().stream().filter(i -> i.getProductId().equals(otherProduct)).findFirst().orElseThrow().getId();
        StocktakeView recorded = stocktakes.recordCount(counting.getId(), otherItem, countDevices(counting, code(expected)));
        StocktakeDeviceCheckView difference = recorded.getItems().stream().filter(i -> i.getId().equals(otherItem))
                .findFirst().orElseThrow().getDevices().get(0);
        assertEquals("WRONG_PRODUCT", difference.getDifferenceCode());
        assertEquals(expected, difference.getDeviceId());
    }

    @Test void deviceRequiresIdentifiersAndStockLockPreventsUnsafeLoss() {
        Long expected = device(deviceProduct);
        openDevices(expected);
        StocktakeView draft = stocktakes.create(request(warehouse, deviceProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        assertThrows(BusinessException.class, () -> stocktakes.recordCount(counting.getId(), itemId(counting), count(counting, "1")));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), countDevices(counting, code(expected)));
        assertEquals("COUNTED", counted.getStatus());
        assertEquals(0, counted.getItems().get(0).getDifferenceQuantity().signum());
        openQuantity("10");
        jdbc.update("UPDATE inv_stocks SET locked_quantity=5 WHERE warehouse_id=? AND product_id=?", warehouse, quantityProduct);
        StocktakeView quantity = stocktakes.create(request(warehouse, quantityProduct));
        quantity = stocktakes.submit(quantity.getId(), action(quantity));
        quantity = stocktakes.recordCount(quantity.getId(), itemId(quantity), count(quantity, "3"));
        StocktakeView locked = quantity;
        assertThrows(BusinessException.class, () -> stocktakes.confirm(locked.getId(), action(locked)));
        assertQuantity(quantityProduct, "10");
    }

    @Test void ledgerRevisionRejectsBusinessChangesEvenWhenQuantityReturnsToSnapshot() {
        openQuantity("10");
        StocktakeView draft = stocktakes.create(request(warehouse, quantityProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), count(counting, "9"));
        executeOrder(true, "2");
        executeOrder(false, "2");
        assertQuantity(quantityProduct, "10");
        assertThrows(BusinessException.class, () -> stocktakes.confirm(counted.getId(), action(counted)));
        assertEquals("COUNTED", stocktakes.getById(counted.getId()).getStatus());
        assertEquals(0, stocktakes.getById(counted.getId()).getLedgers().size());
    }

    @Test void duplicateAndConcurrentConfirmationAdjustOnlyOnce() throws Exception {
        openQuantity("10");
        StocktakeView draft = stocktakes.create(request(warehouse, quantityProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), count(counting, "8"));
        assertEquals(1, race(() -> stocktakes.confirm(counted.getId(), action(counted)),
                () -> stocktakes.confirm(counted.getId(), action(counted))));
        assertQuantity(quantityProduct, "8");
        assertEquals(1, stocktakes.getById(counted.getId()).getLedgers().size());
    }

    @Test void confirmationAndInboundExecutionSerializeOnWarehouseStock() throws Exception {
        openQuantity("10");
        InventoryOrderItemRequest line = new InventoryOrderItemRequest();
        line.setProductId(quantityProduct); line.setQuantity(new BigDecimal("2")); line.setDeviceIds(List.of());
        InventoryOrderRequest request = new InventoryOrderRequest();
        request.setClientRequestId(UUID.randomUUID().toString()); request.setWarehouseId(warehouse);
        request.setItems(List.of(line));
        InventoryOrderView inbound = orders.create(true, request);
        InventoryOrderActionRequest orderAction = new InventoryOrderActionRequest();
        orderAction.setVersion(inbound.getVersion());
        inbound = orders.submit(true, inbound.getId(), orderAction);
        orderAction.setVersion(inbound.getVersion());
        InventoryOrderView approved = orders.approve(true, inbound.getId(), orderAction, true);
        StocktakeView draft = stocktakes.create(request(warehouse, quantityProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), count(counting, "9"));
        InventoryOrderActionRequest executeAction = new InventoryOrderActionRequest();
        executeAction.setVersion(approved.getVersion());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Long orderId = approved.getId();
            Future<Boolean> inboundResult = executor.submit(() -> {
                current("ALL", department); start.await();
                try { orders.execute(true, orderId, executeAction); return true; }
                finally { CurrentUserContext.clear(); }
            });
            Future<Boolean> countResult = executor.submit(() -> {
                current("ALL", department); start.await();
                try { stocktakes.confirm(counted.getId(), action(counted)); return true; }
                catch (BusinessException expected) { return false; }
                finally { CurrentUserContext.clear(); }
            });
            start.countDown();
            assertTrue(inboundResult.get(30, TimeUnit.SECONDS));
            boolean confirmed = countResult.get(30, TimeUnit.SECONDS);
            assertQuantity(quantityProduct, confirmed ? "11" : "12");
            assertEquals(confirmed ? "CONFIRMED" : "COUNTED", stocktakes.getById(counted.getId()).getStatus());
            assertEquals(confirmed ? 1 : 0, stocktakes.getById(counted.getId()).getLedgers().size());
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void auditFailureRollsBackDeviceStatusLedgerAndFinalState() {
        Long missing = device(deviceProduct);
        openDevices(missing);
        StocktakeView draft = stocktakes.create(request(warehouse, deviceProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        StocktakeView counted = stocktakes.recordCount(counting.getId(), itemId(counting), countDevices(counting));
        doThrow(new IllegalStateException("fixture audit failure")).when(audit).insert(any(), eq("STOCKTAKE_CONFIRM"), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> stocktakes.confirm(counted.getId(), action(counted)));
        reset(audit);
        assertEquals("COUNTED", stocktakes.getById(counted.getId()).getStatus());
        assertEquals("IN_STOCK", jdbc.queryForObject("SELECT status FROM asset_devices WHERE id=?", String.class, missing));
        assertQuantity(deviceProduct, "1");
        assertEquals(0, stocktakes.getById(counted.getId()).getLedgers().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE device_id=? AND business_type='STOCKTAKE'", Integer.class, missing));
    }

    @Test void permissionScopeAndHttpRequestIdAreEnforced() throws Exception {
        openQuantity("5");
        current("SELF", department);
        assertEquals(0, stocktakes.getPage(null, null, null, null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> stocktakes.create(request(warehouse, quantityProduct)));
        current("DEPARTMENT", department);
        assertThrows(BusinessException.class, () -> stocktakes.create(request(outsideWarehouse, quantityProduct)));
        current("ALL", department);
        StocktakeView draft = stocktakes.create(request(warehouse, quantityProduct));
        StocktakeView counting = stocktakes.submit(draft.getId(), action(draft));
        String employeeToken = token("EMPLOYEE");
        mvc.perform(get("/api/inventory/stocktakes").header("Authorization", "Bearer " + employeeToken)).andExpect(status().isForbidden());
        String adminToken = token("SYSTEM_ADMIN");
        String countRequestId = "p7-count-" + UUID.randomUUID();
        mvc.perform(put("/api/inventory/stocktakes/{id}/items/{itemId}/count", counting.getId(), itemId(counting))
                .header("Authorization", "Bearer " + adminToken).header("X-Request-Id", countRequestId)
                .contentType("application/json").content(json.writeValueAsString(count(counting, "6"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COUNTED"));
        current("ALL", department);
        StocktakeView counted = stocktakes.getById(counting.getId());
        assertEquals(countRequestId, jdbc.queryForObject("SELECT request_id FROM sys_operation_log WHERE operation_type='STOCKTAKE_COUNT' AND operation_target=?", String.class, String.valueOf(counting.getId())));
        String requestId = "p7-" + UUID.randomUUID();
        mvc.perform(post("/api/inventory/stocktakes/{id}/confirm", counted.getId())
                .header("Authorization", "Bearer " + adminToken).header("X-Request-Id", requestId)
                .contentType("application/json").content(json.writeValueAsString(action(counted))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertEquals(requestId, jdbc.queryForObject("SELECT request_id FROM inv_stock_ledgers WHERE business_type='STOCKTAKE' AND business_no=?", String.class, counted.getStocktakeNo()));
        assertEquals(requestId, jdbc.queryForObject("SELECT request_id FROM sys_operation_log WHERE operation_type='STOCKTAKE_CONFIRM' AND operation_target=?", String.class, String.valueOf(counted.getId())));
    }

    @Test void cancelFreezesDocumentWithoutAdjustment() {
        openQuantity("3");
        StocktakeView draft = stocktakes.create(request(warehouse, quantityProduct));
        StocktakeView cancelled = stocktakes.cancel(draft.getId(), action(draft));
        assertEquals("CANCELLED", cancelled.getStatus());
        assertThrows(BusinessException.class, () -> stocktakes.submit(cancelled.getId(), action(cancelled)));
        assertQuantity(quantityProduct, "3");
    }

    private StocktakeRequest request(Long warehouseId, Long... productIds) {
        StocktakeRequest request = new StocktakeRequest(); request.setClientRequestId(UUID.randomUUID().toString());
        request.setWarehouseId(warehouseId); request.setProductIds(List.of(productIds)); return request;
    }
    private StocktakeActionRequest action(StocktakeView view) { return actionVersion(view.getVersion()); }
    private StocktakeActionRequest actionVersion(long version) { StocktakeActionRequest r = new StocktakeActionRequest(); r.setVersion(version); return r; }
    private StocktakeCountRequest count(StocktakeView view, String quantity) {
        StocktakeCountRequest r = new StocktakeCountRequest(); r.setVersion(view.getVersion()); r.setQuantity(new BigDecimal(quantity)); return r;
    }
    private StocktakeCountRequest countDevices(StocktakeView view, String... codes) {
        StocktakeCountRequest r = new StocktakeCountRequest(); r.setVersion(view.getVersion()); r.setDeviceCodes(List.of(codes)); return r;
    }
    private Long itemId(StocktakeView view) { return view.getItems().get(0).getId(); }
    private Set<String> differenceCodes(StocktakeView view) {
        Set<String> codes = new HashSet<>();
        for (StocktakeDeviceCheckView check : view.getItems().get(0).getDevices()) codes.add(check.getDifferenceCode());
        return codes;
    }
    private void assertAmount(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private void assertQuantity(Long productId, String expected) {
        assertAmount(expected, jdbc.queryForObject("SELECT quantity FROM inv_stocks WHERE warehouse_id=? AND product_id=?", BigDecimal.class, warehouse, productId));
    }
    private void openQuantity(String quantity) {
        OpeningBalanceRequest request = new OpeningBalanceRequest(); request.setClientRequestId(UUID.randomUUID().toString()); request.setWarehouseId(warehouse);
        OpeningBalanceItemRequest item = new OpeningBalanceItemRequest(); item.setProductId(quantityProduct); item.setQuantity(new BigDecimal(quantity)); item.setDeviceIds(List.of());
        request.setItems(List.of(item));
        openings.complete(openings.create(request).getId());
    }
    private void openDevices(Long... deviceIds) {
        OpeningBalanceRequest request = new OpeningBalanceRequest(); request.setClientRequestId(UUID.randomUUID().toString()); request.setWarehouseId(warehouse);
        OpeningBalanceItemRequest item = new OpeningBalanceItemRequest(); item.setProductId(deviceProduct); item.setQuantity(BigDecimal.valueOf(deviceIds.length)); item.setDeviceIds(List.of(deviceIds));
        request.setItems(List.of(item));
        openings.complete(openings.create(request).getId());
    }
    private void executeOrder(boolean inbound, String quantity) {
        InventoryOrderItemRequest item = new InventoryOrderItemRequest(); item.setProductId(quantityProduct); item.setQuantity(new BigDecimal(quantity)); item.setDeviceIds(List.of());
        InventoryOrderRequest request = new InventoryOrderRequest(); request.setClientRequestId(UUID.randomUUID().toString()); request.setWarehouseId(warehouse); request.setItems(List.of(item));
        InventoryOrderView draft = orders.create(inbound, request);
        InventoryOrderActionRequest action = new InventoryOrderActionRequest(); action.setVersion(draft.getVersion());
        InventoryOrderView submitted = orders.submit(inbound, draft.getId(), action);
        action.setVersion(submitted.getVersion());
        InventoryOrderView approved = orders.approve(inbound, draft.getId(), action, true);
        action.setVersion(approved.getVersion());
        orders.execute(inbound, draft.getId(), action);
    }
    private String token(String role) {
        current("ALL", department);
        String secret = UUID.randomUUID().toString();
        jdbc.update("UPDATE sys_user SET password_hash=? WHERE id=?", encoder.encode(secret), operator);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", operator);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?", operator, role);
        LoginRequest login = new LoginRequest(); login.setUsername(prefix); login.setPassword(secret);
        return auth.login(login).getAccessToken();
    }
    private void current(String dataScope, Long departmentId) {
        CurrentUserView current = new CurrentUserView(); current.setUserId(operator); current.setDataScope(dataScope);
        if (departmentId != null) { DepartmentBriefView departmentView = new DepartmentBriefView(); departmentView.setId(departmentId); current.setDepartment(departmentView); }
        CurrentUserContext.set(current);
    }
    private Long device(Long productId) {
        DeviceCreateRequest request = new DeviceCreateRequest(); request.setProductId(productId); request.setDepartmentId(department);
        return devices.create(request).getId();
    }
    private String code(Long id) { return jdbc.queryForObject("SELECT device_code FROM asset_devices WHERE id=?", String.class, id); }
    private Long product(String suffix, String type) {
        return id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type) VALUES(?,?,?,?,?,?)",
                prefix + suffix, prefix + suffix, category, brand, unit, type);
    }
    private int race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) results.add(executor.submit(() -> {
                current("ALL", department); ready.countDown(); start.await();
                try { task.call(); return true; }
                catch (BusinessException expected) { return false; }
                finally { CurrentUserContext.clear(); }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(30, TimeUnit.SECONDS)) successes++;
            return successes;
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    private Long id(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); return statement; }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
