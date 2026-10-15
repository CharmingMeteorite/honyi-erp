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
class Phase6MySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryOrderService orders;
    @Autowired RequisitionService requisitions;
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
        prefix = "P6_" + UUID.randomUUID().toString().replace("-", "");
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
        jdbc.update("DELETE c FROM inv_order_device_claims c JOIN apply_requisition_devices d ON d.id=c.requisition_device_id JOIN apply_requisitions o ON o.id=d.order_id WHERE o.creator_user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", prefix + "%");
        jdbc.update("DELETE d FROM apply_requisition_devices d JOIN apply_requisitions o ON o.id=d.order_id WHERE o.creator_user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", prefix + "%");
        jdbc.update("DELETE i FROM apply_requisition_items i JOIN apply_requisitions o ON o.id=i.order_id WHERE o.creator_user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM apply_requisitions WHERE creator_user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", prefix + "%");
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
        jdbc.update("DELETE FROM sys_operation_log WHERE operator_user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM sys_notifications WHERE recipient_user_id IN (SELECT id FROM sys_user WHERE username LIKE ?)", prefix + "%");
        jdbc.update("DELETE FROM sys_access_token WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user WHERE username LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM sys_department WHERE id=?", childDepartment);
        jdbc.update("DELETE FROM sys_department WHERE id IN (?,?)", department, otherDepartment);
        jdbc.update("DELETE FROM md_products WHERE category_id=?", category);
        jdbc.update("DELETE FROM md_categories WHERE id=?", category);
        jdbc.update("DELETE FROM md_brands WHERE id=?", brand);
        jdbc.update("DELETE FROM md_units WHERE id=?", unit);
    }

    @Test void lifecycleApprovalDoesNotChangeStockAndCompletionIsFrozen() {
        receive(line(quantityProduct, "10"));
        var request = request(line(quantityProduct, "3.25"));
        var draft = requisitions.create(request);
        assertEquals(operator, draft.getCreatorUserId()); assertEquals(department, draft.getDepartmentId());
        assertThrows(DataIntegrityViolationException.class, () -> requisitions.create(request));
        assertThrows(BusinessException.class, () -> issue(draft));
        request.setVersion(draft.getVersion()); request.setRemark("updated");
        var edited = requisitions.update(draft.getId(), request);
        assertThrows(BusinessException.class, () -> requisitions.update(draft.getId(), request));
        var pending = requisitions.submit(edited.getId(), action(edited));
        request.setVersion(pending.getVersion());
        assertThrows(BusinessException.class, () -> requisitions.update(draft.getId(), request));
        var missingReason = action(pending); missingReason.setReason(" ");
        assertThrows(BusinessException.class, () -> requisitions.approve(pending.getId(), missingReason, false));
        var rejected = requisitions.approve(pending.getId(), action(pending), false);
        assertThrows(BusinessException.class, () -> requisitions.submit(rejected.getId(), action(rejected)));
        request.setVersion(rejected.getVersion());
        var revised = requisitions.update(rejected.getId(), request);
        var resubmitted = requisitions.submit(revised.getId(), action(revised));
        var accepted = requisitions.approve(resubmitted.getId(), action(resubmitted), true);
        assertQuantity(warehouse, quantityProduct, "10"); assertTrue(accepted.getLedgers().isEmpty());
        var completed = issue(accepted);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=? AND business_type='REQUISITION' AND business_id=?", Integer.class,
                operator, completed.getId()));
        assertQuantity(warehouse, quantityProduct, "6.75");
        assertEquals("COMPLETED", completed.getStatus()); assertNotNull(completed.getExecutedAt());
        assertEquals(1, completed.getLedgers().size());
        var ledger = completed.getLedgers().get(0);
        assertEquals("REQUISITION", ledger.getBusinessType()); assertEquals("OUT", ledger.getDirection());
        assertEquals(0, ledger.getBeforeQuantity().subtract(ledger.getQuantity()).compareTo(ledger.getAfterQuantity()));
        assertThrows(BusinessException.class, () -> issue(accepted));
        assertThrows(BusinessException.class, () -> issue(completed));
        assertThrows(BusinessException.class, () -> requisitions.cancel(completed.getId(), action(completed)));
        request.setVersion(completed.getVersion());
        assertThrows(BusinessException.class, () -> requisitions.update(completed.getId(), request));
        assertTrue(completed.getLogs().stream().anyMatch(l -> "REQUISITION_REJECT".equals(l.getOperationType())));
        assertEquals(1, requisitions.getPage(warehouse, "COMPLETED", completed.getOrderNo(), java.time.LocalDate.now(), java.time.LocalDate.now(), 1, 1).getTotal());
    }

    @Test void deviceIssueChangesOwnershipHistoryAndCannotBeManuallyReturned() {
        Long device = storedDevice();
        var accepted = accepted(request(line(deviceProduct, "1", device)));
        var before = devices.getById(device);
        assertEquals("IN_STOCK", before.getStatus()); assertEquals(warehouse, before.getWarehouseId());
        assertNull(before.getUserId()); assertEquals(0, reqHistory(device)); assertQuantity(warehouse, deviceProduct, "1");
        issue(accepted);
        var after = devices.getById(device);
        assertEquals("IN_USE", after.getStatus()); assertNull(after.getWarehouseId());
        assertEquals(operator, after.getUserId()); assertEquals(operator, after.getCustodianId());
        assertEquals(department, after.getDepartmentId()); assertNotNull(after.getFirstUseAt());
        assertTrue(after.getAllowedStatuses().isEmpty()); assertFalse(after.getIdentityEditable());
        assertEquals(0, claims(device)); assertQuantity(warehouse, deviceProduct, "0");
        var history = after.getHistory().get(0);
        assertEquals("REQUISITION", history.getBusinessType()); assertEquals(accepted.getOrderNo(), history.getBusinessNo());
        assertEquals("IN_STOCK", history.getFromStatus()); assertEquals("IN_USE", history.getToStatus());
        assertEquals(warehouse, history.getFromWarehouseId()); assertNull(history.getToWarehouseId());
        assertNull(history.getFromUserId()); assertEquals(operator, history.getToUserId());
        assertEquals(operator, history.getToCustodianId()); assertEquals(1, reqHistory(device));
        DeviceStatusRequest status = new DeviceStatusRequest(); status.setStatus("IN_STOCK"); status.setReason("bypass");
        assertThrows(BusinessException.class, () -> devices.updateStatus(device, status));
        DeviceUpdateRequest edit = new DeviceUpdateRequest(); edit.setDepartmentId(department);
        assertThrows(BusinessException.class, () -> devices.update(device, edit));
        assertThrows(BusinessException.class, () -> requisitions.create(request(line(deviceProduct, "1", device))));
        assertThrows(BusinessException.class, () -> orders.create(true, order(warehouse, line(deviceProduct, "1", device))));
        assertTrue(stocks.checkConsistency(warehouse).isEmpty());
    }

    @Test void claimsExcludeOtherRequisitionsAndOutboundAndReleaseOnRejectCancel() {
        Long device = storedDevice();
        var request = request(line(deviceProduct, "1", device));
        var draft = requisitions.create(request);
        assertEquals(1, claims(device));
        assertEquals(0, requisitions.getDeviceOptions(warehouse, deviceProduct, null, 1, 20).getTotal());
        assertEquals(0, orders.getDeviceOptions(false, warehouse, deviceProduct, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> requisitions.create(request(line(deviceProduct, "1", device))));
        assertThrows(BusinessException.class, () -> orders.create(false, order(warehouse, line(deviceProduct, "1", device))));
        var pending = requisitions.submit(draft.getId(), action(draft));
        var rejected = requisitions.approve(draft.getId(), action(pending), false);
        assertEquals(0, claims(device)); assertQuantity(warehouse, deviceProduct, "1");
        var outbound = orders.create(false, order(warehouse, line(deviceProduct, "1", device)));
        request.setVersion(rejected.getVersion());
        assertThrows(BusinessException.class, () -> requisitions.update(draft.getId(), request));
        assertEquals("REJECTED", requisitions.getById(draft.getId()).getStatus());
        orders.cancel(false, outbound.getId(), action(outbound));
        var revised = requisitions.update(draft.getId(), request);
        var resubmitted = requisitions.submit(revised.getId(), action(revised));
        var approved = requisitions.approve(revised.getId(), action(resubmitted), true);
        requisitions.cancel(approved.getId(), action(approved)); assertEquals(0, claims(device));
        assertEquals(0, reqHistory(device));
        var available = requisitions.getDeviceOptions(warehouse, deviceProduct, null, 1, 20);
        assertEquals(1, available.getTotal()); assertEquals(device, available.getList().get(0).getDeviceId());
    }

    @Test void insufficientAvailableStockAndLaterItemFailureRollBackEarlierItems() {
        receive(line(quantityProduct, "5"));
        var accepted = accepted(request(line(quantityProduct, "2")));
        jdbc.update("UPDATE inv_stocks SET locked_quantity=4 WHERE warehouse_id=? AND product_id=?", warehouse, quantityProduct);
        assertThrows(BusinessException.class, () -> issue(accepted));
        assertQuantity(warehouse, quantityProduct, "5"); assertTrue(requisitions.getById(accepted.getId()).getLedgers().isEmpty());
        jdbc.update("UPDATE inv_stocks SET locked_quantity=0 WHERE warehouse_id=? AND product_id=?", warehouse, quantityProduct);
        Long secondProduct = product("Q2", "QUANTITY");
        var multi = accepted(request(line(quantityProduct, "1"), line(secondProduct, "1")));
        assertThrows(BusinessException.class, () -> issue(multi));
        assertQuantity(warehouse, quantityProduct, "5"); assertTrue(requisitions.getById(multi.getId()).getLedgers().isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inv_stocks WHERE product_id=?", Integer.class, secondProduct));
        issue(accepted); assertQuantity(warehouse, quantityProduct, "3");
    }

    @Test void lateAuditFailureRollsBackAllTablesAndAllowsRetry() {
        Long device = storedDevice(); receive(line(quantityProduct, "5"));
        var accepted = accepted(request(line(quantityProduct, "2"), line(deviceProduct, "1", device)));
        doThrow(new IllegalStateException("phase6 audit failure")).when(audit).insert(any(), eq("REQUISITION_ISSUE"), anyString(), anyString(), any());
        assertThrows(IllegalStateException.class, () -> issue(accepted));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=? AND business_type='REQUISITION' AND business_id=?", Integer.class,
                operator, accepted.getId()));
        reset(audit);
        var result = requisitions.getById(accepted.getId());
        assertEquals("APPROVED", result.getStatus()); assertEquals(accepted.getVersion(), result.getVersion());
        assertNull(result.getExecutedAt()); assertTrue(result.getLedgers().isEmpty());
        assertFalse(result.getLogs().stream().anyMatch(l -> "REQUISITION_ISSUE".equals(l.getOperationType())));
        assertQuantity(warehouse, quantityProduct, "5"); assertQuantity(warehouse, deviceProduct, "1");
        var d = devices.getById(device); assertEquals("IN_STOCK", d.getStatus()); assertEquals(warehouse, d.getWarehouseId());
        assertNull(d.getUserId()); assertNull(d.getCustodianId()); assertNull(d.getFirstUseAt());
        assertEquals(0, reqHistory(device)); assertEquals(1, claims(device));
        issue(accepted); assertQuantity(warehouse, quantityProduct, "3"); assertQuantity(warehouse, deviceProduct, "0");
        assertEquals(1, reqHistory(device)); assertEquals(0, claims(device));
    }

    @Test void invalidItemsDisabledReferencesAndIdentityChangesCannotIssue() {
        Long device = storedDevice();
        for (var line : List.of(line(quantityProduct, "0"), line(quantityProduct, "1.00001"), line(quantityProduct, "1", device),
                line(deviceProduct, "2", device), line(deviceProduct, "1"), line(deviceProduct, "2", device, device))) {
            assertThrows(BusinessException.class, () -> requisitions.create(request(line)));
        }
        assertThrows(BusinessException.class, () -> requisitions.create(request(line(quantityProduct, "1"), line(quantityProduct, "1"))));
        var wrongWarehouse = request(line(deviceProduct, "1", device)); wrongWarehouse.setWarehouseId(otherWarehouse);
        assertThrows(BusinessException.class, () -> requisitions.create(wrongWarehouse));
        var accepted = accepted(request(line(deviceProduct, "1", device)));
        warehouses.updateStatus(warehouse, false); assertThrows(BusinessException.class, () -> issue(accepted)); warehouses.updateStatus(warehouse, true);
        products.updateStatus(deviceProduct, false); assertThrows(BusinessException.class, () -> issue(accepted)); products.updateStatus(deviceProduct, true);
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", operator); assertThrows(BusinessException.class, () -> issue(accepted)); jdbc.update("UPDATE sys_user SET status=1 WHERE id=?", operator);
        jdbc.update("UPDATE sys_department SET status=0 WHERE id=?", department); assertThrows(BusinessException.class, () -> issue(accepted)); jdbc.update("UPDATE sys_department SET status=1 WHERE id=?", department);
        jdbc.update("UPDATE sys_user SET department_id=? WHERE id=?", otherDepartment, operator); assertThrows(BusinessException.class, () -> issue(accepted)); jdbc.update("UPDATE sys_user SET department_id=? WHERE id=?", department, operator);
        jdbc.update("UPDATE asset_devices SET serial_number=? WHERE id=?", prefix + "tamper", device);
        assertThrows(BusinessException.class, () -> issue(accepted)); jdbc.update("UPDATE asset_devices SET serial_number=NULL WHERE id=?", device);
        assertQuantity(warehouse, deviceProduct, "1"); assertEquals(0, reqHistory(device)); issue(accepted);
    }

    @Test void consistencyMismatchIsReportedWithoutRepair() {
        Long device = storedDevice(); var accepted = accepted(request(line(deviceProduct, "1", device)));
        jdbc.update("UPDATE inv_stocks SET quantity=2 WHERE warehouse_id=? AND product_id=?", warehouse, deviceProduct);
        assertThrows(BusinessException.class, () -> issue(accepted));
        assertQuantity(warehouse, deviceProduct, "2"); assertEquals("IN_STOCK", devices.getById(device).getStatus());
        assertEquals(1, claims(device)); assertEquals(0, reqHistory(device));
        assertTrue(requisitions.getById(accepted.getId()).getLedgers().isEmpty()); assertFalse(stocks.checkConsistency(warehouse).isEmpty());
    }

    @Test void selfAndDepartmentScopeApplyToListsDetailsOptionsAndWrites() {
        receive(line(quantityProduct, "5")); Long device = storedDevice();
        current("SELF", department);
        var own = requisitions.create(request(line(quantityProduct, "1"), line(deviceProduct, "1", device)));
        assertEquals(1, requisitions.getPage(null, null, own.getOrderNo(), null, null, 1, 20).getTotal());
        assertEquals(1, requisitions.getWarehouseOptions(prefix, 1, 100).getTotal());
        assertThrows(BusinessException.class, () -> requisitions.getDeviceOptions(otherWarehouse, deviceProduct, null, 1, 20));
        var pending = requisitions.submit(own.getId(), action(own));
        assertThrows(BusinessException.class, () -> requisitions.approve(own.getId(), action(pending), true));
        Long otherUser = user("second", department);
        CurrentUserContext.get().setUserId(otherUser);
        assertEquals(0, requisitions.getPage(null, null, own.getOrderNo(), null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> requisitions.getById(own.getId()));
        assertThrows(BusinessException.class, () -> requisitions.cancel(own.getId(), action(pending)));
        current("DEPARTMENT", otherDepartment);
        assertEquals(0, requisitions.getPage(null, null, own.getOrderNo(), null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> requisitions.getById(own.getId()));
        assertThrows(BusinessException.class, () -> requisitions.approve(own.getId(), action(pending), true));
        current("DEPARTMENT", department); CurrentUserContext.get().setUserId(otherUser);
        assertEquals(own.getId(), requisitions.getById(own.getId()).getId());
        assertThrows(BusinessException.class, () -> requisitions.cancel(own.getId(), action(pending)));
        var accepted = requisitions.approve(own.getId(), action(pending), true); issue(accepted);
        current("SELF", department);
        var result = requisitions.getById(own.getId());
        assertTrue(result.getLedgers().stream().allMatch(l -> l.getBeforeQuantity() == null && l.getAfterQuantity() == null));
        assertEquals(operator, devices.getById(device).getUserId());
        current("DEPARTMENT", null); assertEquals(0, requisitions.getPage(null, null, null, null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> requisitions.getById(own.getId()));
    }

    @Test void concurrentSameDocumentDuplicateRequestAndApprovalHaveOneWinner() throws Exception {
        receive(line(quantityProduct, "5"));
        var request = request(line(quantityProduct, "2"));
        assertEquals(1, race(() -> requisitions.create(request), () -> requisitions.create(request)));
        var draft = requisitions.getPage(null, "DRAFT", null, null, null, 1, 100).getList().stream()
                .filter(r -> request.getClientRequestId().equals(r.getClientRequestId())).findFirst().orElseThrow();
        assertEquals(1, race(() -> requisitions.submit(draft.getId(), action(draft)), () -> requisitions.submit(draft.getId(), action(draft))));
        var pending = requisitions.getById(draft.getId());
        assertEquals(1, race(() -> requisitions.approve(draft.getId(), action(pending), true), () -> requisitions.approve(draft.getId(), action(pending), true)));
        var accepted = requisitions.getById(draft.getId());
        assertEquals(1, race(() -> issue(accepted), () -> issue(accepted)));
        assertQuantity(warehouse, quantityProduct, "3"); assertEquals(1, requisitions.getById(draft.getId()).getLedgers().size());
    }

    @Test void concurrentIssueAndCancelCannotBothWin() throws Exception {
        Long device = storedDevice(); var accepted = accepted(request(line(deviceProduct, "1", device)));
        assertEquals(1, race(() -> issue(accepted), () -> requisitions.cancel(accepted.getId(), action(accepted))));
        var result = requisitions.getById(accepted.getId());
        boolean issued = "COMPLETED".equals(result.getStatus());
        assertTrue(issued || "CANCELLED".equals(result.getStatus()));
        assertQuantity(warehouse, deviceProduct, issued ? "0" : "1");
        assertEquals(issued ? 1 : 0, result.getLedgers().size()); assertEquals(issued ? 1 : 0, reqHistory(device)); assertEquals(0, claims(device));
    }

    @Test void concurrentQuantityIssuesAndOutboundNeverOverspend() throws Exception {
        receive(line(quantityProduct, "5"));
        var a = accepted(request(line(quantityProduct, "3"))); var b = accepted(request(line(quantityProduct, "3")));
        assertEquals(1, race(() -> issue(a), () -> issue(b))); assertQuantity(warehouse, quantityProduct, "2");
        var c = accepted(request(line(quantityProduct, "2")));
        var out = approved(false, order(warehouse, line(quantityProduct, "2")));
        assertEquals(1, race(() -> issue(c), () -> execute(out, false))); assertQuantity(warehouse, quantityProduct, "0");
        var ledgers = jdbc.queryForList("SELECT before_quantity,after_quantity,quantity FROM inv_stock_ledgers WHERE warehouse_id=? AND product_id=? AND direction='OUT' ORDER BY id", warehouse, quantityProduct);
        assertEquals(2, ledgers.size());
        assertEquals(0, ((BigDecimal) ledgers.get(0).get("after_quantity")).compareTo((BigDecimal) ledgers.get(1).get("before_quantity")));
    }

    @Test void concurrentDeviceClaimsAndDistinctIssuesPreserveExactStockSet() throws Exception {
        Long first = storedDevice(); Long second = storedDevice();
        assertEquals(1, race(() -> requisitions.create(request(line(deviceProduct, "1", first))),
                () -> requisitions.create(request(line(deviceProduct, "1", first)))));
        assertEquals(1, claims(first));
        var draft = requisitions.getPage(warehouse, "DRAFT", null, null, null, 1, 100).getList().stream()
                .filter(r -> r.getCreatorUserId().equals(operator)).findFirst().orElseThrow();
        requisitions.cancel(draft.getId(), action(draft));
        assertEquals(1, race(() -> requisitions.create(request(line(deviceProduct, "1", first))),
                () -> orders.create(false, order(warehouse, line(deviceProduct, "1", first)))));
        var reqs = requisitions.getPage(warehouse, "DRAFT", null, null, null, 1, 100).getList();
        for (var r : reqs) requisitions.cancel(r.getId(), action(r));
        for (var r : orders.getPage(false, warehouse, "DRAFT", null, null, null, 1, 100).getList()) orders.cancel(false, r.getId(), action(r));
        var a = accepted(request(line(deviceProduct, "1", first))); var b = accepted(request(line(deviceProduct, "1", second)));
        assertEquals(2, race(() -> issue(a), () -> issue(b)));
        assertQuantity(warehouse, deviceProduct, "0"); assertTrue(stocks.checkConsistency(warehouse).isEmpty());
        assertEquals(1, reqHistory(first)); assertEquals(1, reqHistory(second));
    }

    @Test void httpRolesValidationRequestIdAndThreadLocalCleanup() throws Exception {
        receive(line(quantityProduct, "5")); Long device = storedDevice();
        String employee = token("EMPLOYEE"), path = "/api/apply/requisitions";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", "Bearer " + employee).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value(40001));
        var request = request(line(quantityProduct, "1"), line(deviceProduct, "1", device));
        String body = mvc.perform(post(path).header("Authorization", "Bearer " + employee).header("X-Request-Id", prefix + "create")
                .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long id = json.readTree(body).get("data").get("id").asLong(); assertNull(CurrentUserContext.get());
        mvc.perform(post(path + "/" + id + "/submit").header("Authorization", "Bearer " + employee).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/" + id + "/approve").header("Authorization", "Bearer " + employee).contentType("application/json").content("{\"version\":1}"))
                .andExpect(status().isForbidden());
        String manager = token("DEPARTMENT_MANAGER");
        mvc.perform(post(path + "/" + id + "/approve").header("Authorization", "Bearer " + manager).header("X-Request-Id", prefix + "approve")
                .contentType("application/json").content("{\"version\":1}" )).andExpect(status().isOk());
        mvc.perform(post(path + "/" + id + "/issue").header("Authorization", "Bearer " + manager).contentType("application/json").content("{\"version\":2}"))
                .andExpect(status().isForbidden());
        String keeper = token("WAREHOUSE_ADMIN");
        mvc.perform(post(path + "/" + id + "/issue").header("Authorization", "Bearer " + keeper).header("X-Request-Id", prefix + "issue")
                .contentType("application/json").content("{\"version\":2}" )).andExpect(status().isOk());
        mvc.perform(post(path + "/" + id + "/issue").header("Authorization", "Bearer " + keeper).contentType("application/json").content("{\"version\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value(40900));
        mvc.perform(delete(path + "/" + id).header("Authorization", "Bearer " + keeper)).andExpect(status().isMethodNotAllowed());
        assertNull(CurrentUserContext.get());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM inv_stock_ledgers WHERE business_type='REQUISITION' AND request_id=?", Integer.class, prefix + "issue"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE business_type='REQUISITION' AND request_id=?", Integer.class, prefix + "issue"));
        assertEquals(prefix + "issue", jdbc.queryForObject("SELECT request_id FROM sys_operation_log WHERE operation_type='REQUISITION_ISSUE' AND operation_target=?", String.class, id.toString()));
        assertEquals(prefix + "approve", jdbc.queryForObject("SELECT request_id FROM sys_operation_log WHERE operation_type='REQUISITION_APPROVE' AND operation_target=?", String.class, id.toString()));
    }

    @Test void realSchemaConstraintsAndProductReferencesAreEnforced() {
        var draft = requisitions.create(request(line(quantityProduct, "1")));
        var other = requisitions.create(request(line(quantityProduct, "1")));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE apply_requisitions SET order_no=? WHERE id=?", draft.getOrderNo(), other.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE apply_requisitions SET warehouse_id=? WHERE id=?", Long.MAX_VALUE, draft.getId()));
        assertCheckConstraint(() -> jdbc.update("UPDATE apply_requisitions SET status='COMPLETED' WHERE id=?", draft.getId()));
        assertCheckConstraint(() -> jdbc.update("UPDATE apply_requisitions SET status='BOGUS' WHERE id=?", draft.getId()));
        assertCheckConstraint(() -> jdbc.update("UPDATE apply_requisition_items SET quantity=-1 WHERE order_id=?", draft.getId()));
        ProductRequest r = new ProductRequest(); r.setProductCode(prefix + "Q"); r.setProductName(prefix + "Q"); r.setCategoryId(category);
        r.setBrandId(brand); r.setUnitId(unit); r.setProductType("DEVICE");
        assertThrows(BusinessException.class, () -> products.update(quantityProduct, r));
        Long device = storedDevice(); var req = requisitions.create(request(line(deviceProduct, "1", device)));
        assertCheckConstraint(() -> jdbc.update("UPDATE inv_order_device_claims SET requisition_device_id=NULL WHERE device_id=?", device));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE inv_order_device_claims SET requisition_device_id=? WHERE device_id=?", Long.MAX_VALUE, device));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='11' AND success=1", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('apply_requisitions','apply_requisition_items','apply_requisition_devices')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_order_claim_requisition'", Integer.class));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'apply.requisition.%'", Integer.class));
        assertEquals(1, claims(device)); assertTrue(requisitions.getById(req.getId()).getLedgers().isEmpty());
    }

    private RequisitionRequest request(InventoryOrderItemRequest... items) {
        RequisitionRequest r = new RequisitionRequest(); r.setClientRequestId(UUID.randomUUID().toString());
        r.setWarehouseId(warehouse); r.setItems(List.of(items)); return r;
    }
    private InventoryOrderActionRequest action(RequisitionView view) {
        InventoryOrderActionRequest r = new InventoryOrderActionRequest(); r.setVersion(view.getVersion()); r.setReason("regression"); return r;
    }
    private RequisitionView accepted(RequisitionRequest request) {
        var draft = requisitions.create(request);
        var pending = requisitions.submit(draft.getId(), action(draft));
        return requisitions.approve(pending.getId(), action(pending), true);
    }
    private RequisitionView issue(RequisitionView view) { return requisitions.issue(view.getId(), action(view)); }
    private void receive(InventoryOrderItemRequest... items) { execute(approved(true, order(warehouse, items)), true); }
    private Long storedDevice() { Long d = device(department); receive(line(deviceProduct, "1", d)); return d; }
    private int claims(Long device) { return jdbc.queryForObject("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?", Integer.class, device); }
    private int reqHistory(Long device) { return jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE device_id=? AND business_type='REQUISITION'", Integer.class, device); }
    private Long user(String suffix, Long departmentId) { return id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)", prefix + suffix, "unused", prefix + suffix, departmentId); }

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
