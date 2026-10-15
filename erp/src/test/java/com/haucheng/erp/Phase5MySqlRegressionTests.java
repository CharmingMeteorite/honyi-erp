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
import org.springframework.dao.DataIntegrityViolationException;
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

@EnabledIfEnvironmentVariable(named = "ERP_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "erp.auth.seed-username=", "erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class Phase5MySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryOrderService orders;
    @Autowired WarehouseService warehouses;
    @Autowired OpeningBalanceService openings;
    @Autowired StockService stocks;
    @Autowired InventoryOptionService options;
    @Autowired AssetDeviceService devices;
    @Autowired ProductService products;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean OperationLogMapper audit;
    private String prefix;
    private Long category, brand, unit, quantityProduct, deviceProduct, department, otherDepartment, childDepartment, operator, warehouse, otherWarehouse;

    @BeforeEach void setup() {
        prefix = "P5_" + UUID.randomUUID().toString().replace("-", "");
        category = id("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)", prefix, prefix);
        brand = id("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)", prefix, prefix);
        unit = id("INSERT INTO md_units(unit_code,unit_name,precision_scale) VALUES(?,?,4)", prefix, prefix);
        quantityProduct = product("Q", "QUANTITY"); deviceProduct = product("D", "DEVICE");
        department = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix, prefix);
        otherDepartment = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix + "O", prefix + "O");
        childDepartment = id("INSERT INTO sys_department(department_code,department_name,parent_id) VALUES(?,?,?)", prefix + "C", prefix + "C", department);
        operator = id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)", prefix, "unused-test-hash", prefix, department);
        current("ALL", department);
        warehouse = warehouses.create(warehouseRequest("W", department)).getId();
        otherWarehouse = warehouses.create(warehouseRequest("O", otherDepartment)).getId();
    }

    @AfterEach void cleanup() {
        reset(audit); CurrentUserContext.clear();
        // 只清理本方法精确关联的 fixture，保留现场数据和 Flyway history。
        for (String kind : List.of("inbound", "outbound")) {
            jdbc.update("DELETE c FROM inv_order_device_claims c JOIN inv_" + kind + "_devices d ON d.id=c." + kind + "_device_id JOIN inv_" + kind + "_orders o ON o.id=d.order_id WHERE o.warehouse_id IN (SELECT id FROM wh_warehouses WHERE warehouse_code LIKE ?)", prefix + "%");
            jdbc.update("DELETE d FROM inv_" + kind + "_devices d JOIN inv_" + kind + "_orders o ON o.id=d.order_id WHERE o.warehouse_id IN (SELECT id FROM wh_warehouses WHERE warehouse_code LIKE ?)", prefix + "%");
            jdbc.update("DELETE i FROM inv_" + kind + "_items i JOIN inv_" + kind + "_orders o ON o.id=i.order_id WHERE o.warehouse_id IN (SELECT id FROM wh_warehouses WHERE warehouse_code LIKE ?)", prefix + "%");
            jdbc.update("DELETE FROM inv_" + kind + "_orders WHERE warehouse_id IN (SELECT id FROM wh_warehouses WHERE warehouse_code LIKE ?)", prefix + "%");
        }
        List<Long> openingIds = jdbc.queryForList("SELECT id FROM inv_opening_balances WHERE warehouse_id IN (SELECT id FROM wh_warehouses WHERE warehouse_code LIKE ?)", Long.class, prefix + "%");
        for (Long opening : openingIds) {
            jdbc.update("DELETE FROM inv_opening_balance_devices WHERE opening_id=?", opening);
            jdbc.update("DELETE FROM inv_opening_balance_items WHERE opening_id=?", opening);
            jdbc.update("DELETE FROM inv_opening_balances WHERE id=?", opening);
        }
        jdbc.update("DELETE FROM inv_stock_ledgers WHERE product_id IN (SELECT id FROM md_products WHERE category_id=?)", category);
        jdbc.update("DELETE FROM inv_stocks WHERE product_id IN (SELECT id FROM md_products WHERE category_id=?)", category);
        jdbc.update("DELETE FROM asset_device_history WHERE device_id IN (SELECT d.id FROM asset_devices d JOIN md_products p ON p.id=d.product_id WHERE p.category_id=?)", category);
        jdbc.update("DELETE FROM asset_devices WHERE product_id IN (SELECT id FROM md_products WHERE category_id=?)", category);
        jdbc.update("DELETE FROM wh_warehouses WHERE warehouse_code LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM sys_operation_log WHERE operator_user_id=?", operator);
        jdbc.update("DELETE FROM sys_notifications WHERE recipient_user_id=?", operator);
        jdbc.update("DELETE FROM sys_access_token WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user WHERE id=?", operator);
        jdbc.update("DELETE FROM sys_department WHERE id=?", childDepartment);
        jdbc.update("DELETE FROM sys_department WHERE id IN (?,?)", department, otherDepartment);
        jdbc.update("DELETE FROM md_products WHERE category_id=?", category);
        jdbc.update("DELETE FROM md_categories WHERE id=?", category);
        jdbc.update("DELETE FROM md_brands WHERE id=?", brand);
        jdbc.update("DELETE FROM md_units WHERE id=?", unit);
    }

    @Test void quantityLifecycleDraftVersionRejectionAndFrozenCompletion() {
        for (boolean inbound : List.of(true, false)) {
            if (!inbound) execute(approved(true, order(warehouse, line(quantityProduct, "10"))), true);
            var request = order(warehouse, line(quantityProduct, "3.25"));
            var draft = orders.create(inbound, request);
            assertEquals("DRAFT", draft.getStatus()); assertNotNull(draft.getCreatorUserId());
            assertThrows(DataIntegrityViolationException.class, () -> orders.create(inbound, request));
            assertThrows(BusinessException.class, () -> orders.execute(inbound, draft.getId(), action(draft)));
            request.setVersion(draft.getVersion()); request.setRemark("updated");
            var edited = orders.update(inbound, draft.getId(), request);
            assertEquals("updated", edited.getRemark());
            assertThrows(BusinessException.class, () -> orders.update(inbound, draft.getId(), request));
            var pending = orders.submit(inbound, edited.getId(), action(edited));
            request.setVersion(pending.getVersion());
            assertThrows(BusinessException.class, () -> orders.update(inbound, pending.getId(), request));
            var rejected = orders.approve(inbound, pending.getId(), action(pending), false);
            assertEquals("REJECTED", rejected.getStatus());
            assertThrows(BusinessException.class, () -> orders.submit(inbound, rejected.getId(), action(rejected)));
            request.setVersion(rejected.getVersion());
            edited = orders.update(inbound, rejected.getId(), request);
            var resubmitted = orders.submit(inbound, edited.getId(), action(edited));
            var accepted = orders.approve(inbound, resubmitted.getId(), action(resubmitted), true);
            assertEquals("APPROVED", accepted.getStatus());
            assertEquals(0, accepted.getLedgers().size());
            var completed = execute(accepted, inbound);
            assertEquals("COMPLETED", completed.getStatus());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=? AND business_type=? AND business_id=?", Integer.class,
                    operator, inbound ? "INBOUND" : "OUTBOUND", completed.getId()));
            assertNotNull(completed.getExecutedAt()); assertEquals(operator, completed.getExecutorUserId());
            assertEquals(1, completed.getLedgers().size());
            var ledger = completed.getLedgers().get(0);
            assertEquals(inbound ? "INBOUND" : "OUTBOUND", ledger.getBusinessType());
            assertEquals(0, ledger.getBeforeQuantity().add(inbound ? ledger.getQuantity() : ledger.getQuantity().negate()).compareTo(ledger.getAfterQuantity()));
            assertThrows(BusinessException.class, () -> execute(accepted, inbound));
            assertThrows(BusinessException.class, () -> orders.cancel(inbound, completed.getId(), action(completed)));
            assertThrows(BusinessException.class, () -> orders.approve(inbound, completed.getId(), action(completed), true));
            request.setVersion(completed.getVersion());
            assertThrows(BusinessException.class, () -> orders.update(inbound, completed.getId(), request));
            assertTrue(completed.getLogs().stream().anyMatch(l -> l.getOperationType().endsWith("_REJECT")));
            assertEquals(1, orders.getPage(inbound, warehouse, "COMPLETED", completed.getOrderNo(), java.time.LocalDate.now(), java.time.LocalDate.now(), 1, 1).getTotal());
        }
        assertQuantity(warehouse, quantityProduct, "10");
    }

    @Test void deviceInboundOutboundUpdatesWarehouseHistoryAndExactStockSet() {
        DeviceCreateRequest r = new DeviceCreateRequest(); r.setProductId(deviceProduct); r.setDepartmentId(department);
        r.setSerialNumber(prefix + "SN"); r.setMacAddress("aabb.ccdd.eeff"); r.setAssetNo(prefix + "AS");
        Long device = devices.create(r).getId();
        var in = approved(true, order(warehouse, line(deviceProduct, "1", device)));
        assertEquals(0, stockCount());
        DeviceUpdateRequest edit = new DeviceUpdateRequest(); edit.setDepartmentId(department);
        assertThrows(BusinessException.class, () -> devices.update(device, edit));
        execute(in, true); assertQuantity(warehouse, deviceProduct, "1");
        assertEquals(warehouse, devices.getById(device).getWarehouseId());
        assertEquals("AA:BB:CC:DD:EE:FF", orders.getById(true, in.getId()).getItems().get(0).getDevices().get(0).getMacAddress());
        assertThrows(BusinessException.class, () -> orders.create(true, order(otherWarehouse, line(deviceProduct, "1", device))));
        assertThrows(BusinessException.class, () -> orders.create(false, order(otherWarehouse, line(deviceProduct, "1", device))));
        var out = approved(false, order(warehouse, line(deviceProduct, "1", device)));
        execute(out, false);
        assertQuantity(warehouse, deviceProduct, "0");
        var result = devices.getById(device);
        assertEquals("SHIPPED", result.getStatus()); assertNull(result.getWarehouseId());
        assertFalse(result.getIdentityEditable()); assertTrue(result.getAllowedStatuses().isEmpty());
        assertEquals(2, result.getHistory().stream().filter(h -> h.getBusinessNo() != null).count());
        var history = result.getHistory().get(0);
        assertEquals("OUTBOUND", history.getBusinessType()); assertEquals(warehouse, history.getFromWarehouseId()); assertNull(history.getToWarehouseId());
        assertThrows(BusinessException.class, () -> orders.create(false, order(warehouse, line(deviceProduct, "1", device))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(deviceProduct, "1", device))));
        assertTrue(stocks.checkConsistency(warehouse).isEmpty());
    }

    @Test void claimUniquenessCancellationRejectionAndOpeningCompatibility() {
        Long device = device(department);
        var draft = orders.create(true, order(warehouse, line(deviceProduct, "1", device)));
        assertThrows(BusinessException.class, () -> orders.create(true, order(otherWarehouse, line(deviceProduct, "1", device))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(deviceProduct, "1", device))));
        DeviceStatusRequest status = new DeviceStatusRequest(); status.setStatus("SCRAPPED"); status.setReason("claimed");
        assertThrows(BusinessException.class, () -> devices.updateStatus(device, status));
        var cancelled = orders.cancel(true, draft.getId(), action(draft));
        assertEquals("CANCELLED", cancelled.getStatus());
        assertThrows(BusinessException.class, () -> orders.submit(true, cancelled.getId(), action(cancelled)));
        draft = orders.create(true, order(warehouse, line(deviceProduct, "1", device)));
        var pending = orders.submit(true, draft.getId(), action(draft));
        var rejected = orders.approve(true, pending.getId(), action(pending), false);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?", Integer.class, device));
        var opening = openings.create(opening(warehouse, item(deviceProduct, "1", device)));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(deviceProduct, "1", device))));
        openings.complete(opening.getId());
        var request = order(warehouse, line(deviceProduct, "1", device)); request.setClientRequestId(rejected.getClientRequestId()); request.setVersion(rejected.getVersion());
        assertThrows(BusinessException.class, () -> orders.update(true, rejected.getId(), request));
        execute(approved(false, order(warehouse, line(deviceProduct, "1", device))), false);
        assertTrue(stocks.checkConsistency(warehouse).isEmpty());
    }

    @Test void disabledWarehouseProductAndBadItemsNeverExecute() {
        Long device = device(department);
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(deviceProduct, "1"))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(deviceProduct, "2", device))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(quantityProduct, "1", device))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(deviceProduct, "2", device, device))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(quantityProduct, "0"))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(quantityProduct, "1.00001"))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(quantityProduct, "1"), line(quantityProduct, "2"))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(Long.MAX_VALUE, "1"))));
        for (boolean inbound : List.of(true, false)) {
            var approved = approved(inbound, order(warehouse, line(quantityProduct, "2")));
            warehouses.updateStatus(warehouse, false);
            assertThrows(BusinessException.class, () -> execute(approved, inbound));
            assertThrows(BusinessException.class, () -> orders.create(inbound, order(warehouse, line(quantityProduct, "2"))));
            warehouses.updateStatus(warehouse, true); products.updateStatus(quantityProduct, false);
            assertThrows(BusinessException.class, () -> execute(approved, inbound));
            products.updateStatus(quantityProduct, true); execute(approved, inbound);
        }
        assertQuantity(warehouse, quantityProduct, "0");
    }

    @Test void insufficientAvailableStockAndMultipleItemsRollback() {
        execute(approved(true, order(warehouse, line(quantityProduct, "5"))), true);
        jdbc.update("UPDATE inv_stocks SET locked_quantity=4 WHERE warehouse_id=? AND product_id=?", warehouse, quantityProduct);
        var out = approved(false, order(warehouse, line(quantityProduct, "2")));
        assertThrows(BusinessException.class, () -> execute(out, false));
        assertQuantity(warehouse, quantityProduct, "5");
        assertEquals("APPROVED", orders.getById(false, out.getId()).getStatus());
        assertEquals(0, orders.getById(false, out.getId()).getLedgers().size());
        jdbc.update("UPDATE inv_stocks SET locked_quantity=0 WHERE warehouse_id=? AND product_id=?", warehouse, quantityProduct);
        execute(out, false); assertQuantity(warehouse, quantityProduct, "3");
        Long another = product("Q2", "QUANTITY");
        var multi = approved(false, order(warehouse, line(quantityProduct, "1"), line(another, "1")));
        assertThrows(BusinessException.class, () -> execute(multi, false));
        assertQuantity(warehouse, quantityProduct, "3");
        assertEquals(0, orders.getById(false, multi.getId()).getLedgers().size());
    }

    @Test void auditFailureRollsBackEveryTableForBothDirections() {
        Long device = device(department);
        for (boolean inbound : List.of(true, false)) {
            var accepted = approved(inbound, order(warehouse, line(quantityProduct, "2"), line(deviceProduct, "1", device)));
            String type = inbound ? "INBOUND_EXECUTE" : "OUTBOUND_EXECUTE";
            doThrow(new IllegalStateException("intentional rollback regression")).when(audit).insert(any(), eq(type), anyString(), anyString(), nullable(String.class));
            assertThrows(IllegalStateException.class, () -> execute(accepted, inbound)); reset(audit);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=? AND business_type=? AND business_id=?", Integer.class,
                    operator, inbound ? "INBOUND" : "OUTBOUND", accepted.getId()));
            assertEquals("APPROVED", orders.getById(inbound, accepted.getId()).getStatus());
            assertEquals(0, orders.getById(inbound, accepted.getId()).getLedgers().size());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE business_no=?", Integer.class, accepted.getOrderNo()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?", Integer.class, device));
            assertEquals(inbound ? null : warehouse, devices.getById(device).getWarehouseId());
            if (inbound) assertEquals(0, stockCount()); else assertQuantity(warehouse, quantityProduct, "2");
            execute(accepted, inbound);
        }
        assertQuantity(warehouse, quantityProduct, "0"); assertQuantity(warehouse, deviceProduct, "0");
    }

    @Test void consistencyFailureReportsWithoutRepairOrPartialExecution() {
        Long device = device(department);
        execute(approved(true, order(warehouse, line(deviceProduct, "1", device))), true);
        jdbc.update("UPDATE inv_stocks SET quantity=2 WHERE warehouse_id=? AND product_id=?", warehouse, deviceProduct);
        assertFalse(stocks.checkConsistency(warehouse).isEmpty());
        var out = approved(false, order(warehouse, line(deviceProduct, "1", device)));
        assertThrows(BusinessException.class, () -> execute(out, false));
        assertQuantity(warehouse, deviceProduct, "2"); assertEquals(warehouse, devices.getById(device).getWarehouseId());
        assertEquals(0, orders.getById(false, out.getId()).getLedgers().size());
    }

    @Test void departmentSelfAndDeviceScopesApplyToReadsOptionsAndWrites() {
        Long outsideDevice = device(otherDepartment);
        var own = orders.create(true, order(warehouse, line(quantityProduct, "1")));
        var outside = orders.create(true, order(otherWarehouse, line(quantityProduct, "1")));
        current("DEPARTMENT", department);
        assertEquals(1, orders.getPage(true, null, null, null, null, null, 1, 100).getTotal());
        assertThrows(BusinessException.class, () -> orders.getById(true, outside.getId()));
        assertThrows(BusinessException.class, () -> orders.submit(true, outside.getId(), action(outside)));
        assertThrows(BusinessException.class, () -> orders.create(true, order(otherWarehouse, line(quantityProduct, "1"))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(deviceProduct, "1", outsideDevice))));
        assertEquals(0, orders.getDeviceOptions(true, warehouse, deviceProduct, null, 1, 20).getTotal());
        current("SELF", department);
        assertEquals(0, orders.getPage(true, null, null, null, null, null, 1, 100).getTotal());
        assertThrows(BusinessException.class, () -> orders.submit(true, own.getId(), action(own)));
        current("DEPARTMENT", null);
        assertEquals(0, orders.getPage(true, null, null, null, null, null, 1, 100).getTotal());
        current("ALL", department);
        Long childWarehouse = warehouses.create(warehouseRequest("child", childDepartment)).getId();
        var child = orders.create(false, order(childWarehouse, line(quantityProduct, "1")));
        current("DEPARTMENT", department);
        assertEquals(child.getId(), orders.getById(false, child.getId()).getId());
    }

    @Test void concurrentSameDocumentAndDuplicateRequestHaveOneEffect() throws Exception {
        var request = order(warehouse, line(quantityProduct, "3"));
        assertEquals(1, race(() -> orders.create(true, request), () -> orders.create(true, request)));
        var draft = orders.getPage(true, warehouse, null, null, null, null, 1, 20).getList().get(0);
        assertEquals(1, race(() -> orders.submit(true, draft.getId(), action(draft)), () -> orders.submit(true, draft.getId(), action(draft))));
        var pending = orders.getById(true, draft.getId());
        assertEquals(1, race(() -> orders.approve(true, pending.getId(), action(pending), true), () -> orders.approve(true, pending.getId(), action(pending), false)));
        var decided = orders.getById(true, draft.getId());
        if ("REJECTED".equals(decided.getStatus())) {
            request.setVersion(decided.getVersion()); decided = orders.update(true, decided.getId(), request);
            decided = orders.submit(true, decided.getId(), action(decided)); decided = orders.approve(true, decided.getId(), action(decided), true);
        }
        var accepted = decided;
        assertEquals(1, race(() -> execute(accepted, true), () -> execute(accepted, true)));
        assertQuantity(warehouse, quantityProduct, "3");
        assertEquals(1, orders.getById(true, accepted.getId()).getLedgers().size());
    }

    @Test void concurrentQuantityTransactionsHaveContinuousLedgersAndNoNegativeStock() throws Exception {
        var a = approved(true, order(warehouse, line(quantityProduct, "5")));
        var b = approved(true, order(warehouse, line(quantityProduct, "7")));
        assertEquals(2, race(() -> execute(a, true), () -> execute(b, true)));
        assertQuantity(warehouse, quantityProduct, "12");
        var c = approved(false, order(warehouse, line(quantityProduct, "9")));
        var d = approved(false, order(warehouse, line(quantityProduct, "9")));
        assertEquals(1, race(() -> execute(c, false), () -> execute(d, false)));
        assertQuantity(warehouse, quantityProduct, "3");
        var e = approved(true, order(warehouse, line(quantityProduct, "4")));
        var f = approved(false, order(warehouse, line(quantityProduct, "2")));
        assertEquals(2, race(() -> execute(e, true), () -> execute(f, false)));
        assertQuantity(warehouse, quantityProduct, "5");
        var ledger = jdbc.queryForList("SELECT before_quantity,after_quantity FROM inv_stock_ledgers WHERE warehouse_id=? AND product_id=? ORDER BY id", warehouse, quantityProduct);
        BigDecimal before = BigDecimal.ZERO;
        for (var row : ledger) { assertEquals(0, before.compareTo((BigDecimal) row.get("before_quantity"))); before = (BigDecimal) row.get("after_quantity"); }
        assertEquals(0, before.compareTo(new BigDecimal("5")));
    }

    @Test void concurrentDeviceClaimsAndExecutionCannotDoubleMove() throws Exception {
        Long device = device(department);
        assertEquals(1, race(() -> orders.create(true, order(warehouse, line(deviceProduct, "1", device))), () -> orders.create(true, order(otherWarehouse, line(deviceProduct, "1", device)))));
        var in = orders.getPage(true, null, null, null, null, null, 1, 100).getList().get(0);
        var pending = orders.submit(true, in.getId(), action(in)); in = orders.approve(true, pending.getId(), action(pending), true);
        var acceptedIn = in;
        assertEquals(1, race(() -> execute(acceptedIn, true), () -> execute(acceptedIn, true)));
        Long target = in.getWarehouseId();
        assertEquals(1, race(() -> orders.create(false, order(target, line(deviceProduct, "1", device))), () -> orders.create(false, order(target, line(deviceProduct, "1", device)))));
        var out = orders.getPage(false, null, null, null, null, null, 1, 100).getList().get(0);
        pending = orders.submit(false, out.getId(), action(out)); var acceptedOut = orders.approve(false, pending.getId(), action(pending), true);
        assertEquals(1, race(() -> execute(acceptedOut, false), () -> execute(acceptedOut, false)));
        assertQuantity(target, deviceProduct, "0"); assertEquals("SHIPPED", devices.getById(device).getStatus());
        assertTrue(stocks.checkConsistency(target).isEmpty());
    }

    @Test void concurrentDistinctDevicesMatchAggregateStock() throws Exception {
        Long first = device(department), second = device(department);
        var a = approved(true, order(warehouse, line(deviceProduct, "1", first)));
        var b = approved(true, order(warehouse, line(deviceProduct, "1", second)));
        assertEquals(2, race(() -> execute(a, true), () -> execute(b, true))); assertQuantity(warehouse, deviceProduct, "2");
        var c = approved(false, order(warehouse, line(deviceProduct, "1", first)));
        var d = approved(false, order(warehouse, line(deviceProduct, "1", second)));
        assertEquals(2, race(() -> execute(c, false), () -> execute(d, false))); assertQuantity(warehouse, deviceProduct, "0");
        assertTrue(stocks.checkConsistency(warehouse).isEmpty());
    }

    @Test void httpPermissionsAuditRequestIdAndThreadLocalCleanup() throws Exception {
        String admin = token("SYSTEM_ADMIN");
        current("ALL", department); Long device = device(department);
        for (boolean inbound : List.of(true, false)) {
            String path = "/api/inventory/" + (inbound ? "inbounds" : "outbounds");
            var request = order(warehouse, line(quantityProduct, "2"), line(deviceProduct, "1", device));
            String body = mvc.perform(post(path).header("Authorization", "Bearer " + admin).header("X-Request-Id", prefix + "create").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Long id = json.readTree(body).path("data").path("id").asLong();
            mvc.perform(post(path).header("Authorization", "Bearer " + admin).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isConflict());
            long version = 0;
            for (String action : List.of("submit", "approve", "execute")) {
                mvc.perform(post(path + "/" + id + "/" + action).header("Authorization", "Bearer " + admin).header("X-Request-Id", prefix + action).contentType("application/json").content("{\"version\":" + version++ + "}")).andExpect(status().isOk());
                assertNull(CurrentUserContext.get());
            }
            mvc.perform(post(path + "/" + id + "/execute").header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"version\":2}")).andExpect(status().isConflict());
            mvc.perform(get(path + "/" + id)).andExpect(status().isUnauthorized());
            mvc.perform(delete(path + "/" + id).header("Authorization", "Bearer " + admin)).andExpect(status().isMethodNotAllowed());
            assertNull(CurrentUserContext.get());
            String kind = inbound ? "INBOUND" : "OUTBOUND";
            assertEquals(prefix + "approve", jdbc.queryForObject("SELECT request_id FROM sys_operation_log WHERE operation_type=? AND operation_target=?", String.class, kind + "_APPROVE", String.valueOf(id)));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM inv_stock_ledgers WHERE business_type=? AND request_id=? AND operator_user_id=?", Integer.class, kind, prefix + "execute", operator));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE business_type=? AND request_id=? AND device_id=?", Integer.class, kind, prefix + "execute", device));
            String readOnly = token("MANAGEMENT");
            mvc.perform(get(path).header("Authorization", "Bearer " + readOnly)).andExpect(status().isOk());
            for (String action : List.of("submit", "approve", "reject", "execute", "cancel")) mvc.perform(post(path + "/" + id + "/" + action).header("Authorization", "Bearer " + readOnly).contentType("application/json").content("{\"version\":3}")).andExpect(status().isForbidden());
            mvc.perform(post(path).header("Authorization", "Bearer " + readOnly).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isForbidden());
            admin = token("SYSTEM_ADMIN"); current("ALL", department);
        }
        String employee = token("EMPLOYEE");
        mvc.perform(get("/api/inventory/inbounds").header("Authorization", "Bearer " + employee)).andExpect(status().isForbidden());
        assertNull(CurrentUserContext.get());
    }

    @Test void realConstraintsHistoryAndProductFreezeAreEnforced() {
        var in = orders.create(true, order(warehouse, line(quantityProduct, "1")));
        var other = orders.create(true, order(warehouse, line(quantityProduct, "1")));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE inv_inbound_orders SET order_no=? WHERE id=?", in.getOrderNo(), other.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE inv_inbound_orders SET warehouse_id=? WHERE id=?", Long.MAX_VALUE, in.getId()));
        assertCheckConstraint(() -> jdbc.update("UPDATE inv_inbound_orders SET status='BOGUS' WHERE id=?", in.getId()));
        assertCheckConstraint(() -> jdbc.update("UPDATE inv_inbound_orders SET status='COMPLETED' WHERE id=?", in.getId()));
        assertCheckConstraint(() -> jdbc.update("UPDATE inv_inbound_items SET quantity=-1 WHERE order_id=?", in.getId()));
        ProductRequest r = new ProductRequest(); r.setProductCode(prefix + "Q"); r.setProductName(prefix + "Q"); r.setCategoryId(category); r.setBrandId(brand); r.setUnitId(unit); r.setProductType("DEVICE");
        assertThrows(BusinessException.class, () -> products.update(quantityProduct, r));
        assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND CAST(version AS UNSIGNED) BETWEEN 1 AND 10", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('inv_inbound_orders','inv_outbound_orders','inv_inbound_items','inv_outbound_items','inv_inbound_devices','inv_outbound_devices','inv_order_device_claims')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_order_claim_inbound'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_stocks' AND column_name='device_id'", Integer.class));
    }

    @Test void deviceOptionsAndIdentityRulesRemainNormalizedAndUnique() {
        DeviceCreateRequest r = new DeviceCreateRequest(); r.setProductId(deviceProduct); r.setDepartmentId(department); r.setDeviceCode(prefix + "device"); r.setSerialNumber(prefix + "serial"); r.setMacAddress("12-34-56-78-9a-bc"); r.setAssetNo(prefix + "asset");
        Long d = devices.create(r).getId();
        assertThrows(BusinessException.class, () -> devices.create(r));
        r.setDeviceCode(null); assertThrows(BusinessException.class, () -> devices.create(r));
        r.setSerialNumber(null); r.setMacAddress("1234.5678.9abc"); assertThrows(BusinessException.class, () -> devices.create(r));
        r.setMacAddress(null); assertThrows(BusinessException.class, () -> devices.create(r));
        assertEquals(1, orders.getDeviceOptions(true, warehouse, deviceProduct, prefix + "serial", 1, 1).getTotal());
        assertEquals("12:34:56:78:9A:BC", orders.getDeviceOptions(true, warehouse, deviceProduct, prefix, 1, 1).getList().get(0).getMacAddress());
        var draft = orders.create(true, order(warehouse, line(deviceProduct, "1", d)));
        assertEquals(0, orders.getDeviceOptions(true, warehouse, deviceProduct, null, 1, 20).getTotal());
        var pending = orders.submit(true, draft.getId(), action(draft));
        execute(orders.approve(true, pending.getId(), action(pending), true), true);
        assertEquals(1, orders.getDeviceOptions(false, warehouse, deviceProduct, null, 1, 20).getTotal());
        assertEquals(0, orders.getDeviceOptions(false, otherWarehouse, deviceProduct, null, 1, 20).getTotal());
    }

    private InventoryOrderRequest order(Long warehouseId, InventoryOrderItemRequest... items) { InventoryOrderRequest r = new InventoryOrderRequest(); r.setClientRequestId(UUID.randomUUID().toString()); r.setWarehouseId(warehouseId); r.setItems(List.of(items)); return r; }
    private InventoryOrderItemRequest line(Long productId, String quantity, Long... ids) { InventoryOrderItemRequest r = new InventoryOrderItemRequest(); r.setProductId(productId); r.setQuantity(new BigDecimal(quantity)); r.setDeviceIds(List.of(ids)); return r; }
    private InventoryOrderActionRequest action(InventoryOrderView view) { InventoryOrderActionRequest r = new InventoryOrderActionRequest(); r.setVersion(view.getVersion()); r.setReason("regression"); return r; }
    private InventoryOrderView approved(boolean inbound, InventoryOrderRequest request) { var draft = orders.create(inbound, request); var pending = orders.submit(inbound, draft.getId(), action(draft)); return orders.approve(inbound, pending.getId(), action(pending), true); }
    private InventoryOrderView execute(InventoryOrderView view, boolean inbound) { return orders.execute(inbound, view.getId(), action(view)); }
    private void assertCheckConstraint(Runnable action) {
        var error = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, action::run);
        assertEquals(3819, error.getSQLException().getErrorCode());
    }

    private int race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<?> action : List.of(first, second)) futures.add(executor.submit(() -> {
                current("ALL", department); ready.countDown(); start.await();
                try { action.call(); return true; }
                catch (BusinessException | DataIntegrityViolationException expected) { return false; }
                finally { CurrentUserContext.clear(); }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            int successes = 0; for (Future<Boolean> future : futures) if (future.get(30, TimeUnit.SECONDS)) successes++;
            return successes;
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
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
    private void current(String scope, Long departmentId) {
        CurrentUserView current = new CurrentUserView(); current.setUserId(operator); current.setDataScope(scope);
        if (departmentId != null) { DepartmentBriefView view = new DepartmentBriefView(); view.setId(departmentId); current.setDepartment(view); }
        CurrentUserContext.set(current);
    }
    private Long device(Long departmentId) { DeviceCreateRequest request = new DeviceCreateRequest(); request.setProductId(deviceProduct); request.setDepartmentId(departmentId); return devices.create(request).getId(); }
    private Long product(String suffix, String type) { return id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type) VALUES(?,?,?,?,?,?)", prefix + suffix, prefix + suffix, category, brand, unit, type); }
    private WarehouseRequest warehouseRequest(String suffix, Long departmentId) { WarehouseRequest r = new WarehouseRequest(); r.setWarehouseCode(prefix + suffix); r.setWarehouseName(prefix + suffix); r.setDepartmentId(departmentId); return r; }
    private OpeningBalanceRequest opening(Long warehouseId, OpeningBalanceItemRequest... items) { OpeningBalanceRequest r = new OpeningBalanceRequest(); r.setClientRequestId(UUID.randomUUID().toString()); r.setWarehouseId(warehouseId); r.setItems(List.of(items)); return r; }
    private OpeningBalanceItemRequest item(Long productId, String quantity, Long... deviceIds) { OpeningBalanceItemRequest r = new OpeningBalanceItemRequest(); r.setProductId(productId); r.setQuantity(new BigDecimal(quantity)); r.setDeviceIds(List.of(deviceIds)); return r; }
    private int stockCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM inv_stocks WHERE product_id IN (SELECT id FROM md_products WHERE category_id=?)", Integer.class, category); }
    private void assertQuantity(Long warehouseId, Long productId, String expected) { assertEquals(0, jdbc.queryForObject("SELECT quantity FROM inv_stocks WHERE warehouse_id=? AND product_id=?", BigDecimal.class, warehouseId, productId).compareTo(new BigDecimal(expected))); }
    private Long id(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); return statement; }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
