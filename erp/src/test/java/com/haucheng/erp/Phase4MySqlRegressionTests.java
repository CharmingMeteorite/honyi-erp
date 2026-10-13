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
class Phase4MySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
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
        prefix = "P4_" + UUID.randomUUID().toString().replace("-", "");
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

    @Test void warehouseCrudUniqueStatusAndPermissionSeparation() throws Exception {
        WarehouseRequest changed = warehouseRequest("W", department); changed.setWarehouseName(prefix + "renamed");
        assertEquals(changed.getWarehouseName(), warehouses.update(warehouse, changed).getWarehouseName());
        assertThrows(BusinessException.class, () -> warehouses.create(changed));
        WarehouseRequest duplicateName = warehouseRequest("unique", department); duplicateName.setWarehouseName(changed.getWarehouseName());
        assertThrows(BusinessException.class, () -> warehouses.create(duplicateName));
        assertEquals(2, warehouses.getPage(prefix, null, 1, 1).getTotal());
        assertEquals(1, warehouses.getPage(prefix, null, 1, 1).getList().size());
        warehouses.updateStatus(warehouse, false); assertFalse(warehouses.getById(warehouse).getStatus());
        warehouses.updateStatus(warehouse, true);
        String token = token("WAREHOUSE_ADMIN");
        mvc.perform(get("/api/system/warehouses").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/system/warehouses").header("Authorization", "Bearer " + token).contentType("application/json").content("{\"warehouseCode\":\"\",\"warehouseName\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/system/warehouses/" + warehouse).header("Authorization", "Bearer " + token)).andExpect(status().is4xxClientError());
    }

    @Test void quantityOpeningCreatesRealLedgerAndRejectsRepeatExecutionAndRequest() {
        OpeningBalanceRequest request = opening(warehouse, item(quantityProduct, "12.25"));
        OpeningBalanceView draft = openings.create(request);
        assertEquals("DRAFT", draft.getStatus()); assertEquals(0, stockCount());
        assertThrows(DataIntegrityViolationException.class, () -> openings.create(request));
        openings.complete(draft.getId());
        assertQuantity(warehouse, quantityProduct, "12.25");
        var ledger = stocks.getLedgerPage(warehouse, quantityProduct, null, 1, 20);
        assertEquals(1, ledger.getTotal()); assertEquals("OPENING_BALANCE", ledger.getList().get(0).getBusinessType());
        assertEquals(0, ledger.getList().get(0).getBeforeQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(draft.getOpeningNo(), ledger.getList().get(0).getBusinessNo());
        assertThrows(BusinessException.class, () -> openings.complete(draft.getId()));
        assertThrows(BusinessException.class, () -> openings.update(draft.getId(), request));
        assertQuantity(warehouse, quantityProduct, "12.25");
        assertEquals(0, stocks.getPage(warehouse, quantityProduct, null, 1, 20).getList().get(0).getLockedQuantity().signum());
    }

    @Test void deviceOpeningUpdatesWarehouseHistoryAndProtectsOrdinaryDeviceEndpoints() {
        Long first = device(department), second = device(department);
        var draft = openings.create(opening(warehouse, item(deviceProduct, "2", first, second)));
        openings.complete(draft.getId()); assertQuantity(warehouse, deviceProduct, "2");
        assertEquals(warehouse, devices.getById(first).getWarehouseId());
        assertEquals("IN_STOCK", devices.getById(first).getStatus());
        assertTrue(devices.getById(first).getAllowedStatuses().isEmpty());
        DeviceUpdateRequest edit = new DeviceUpdateRequest(); edit.setDepartmentId(department); edit.setRemark("ordinary edit");
        assertEquals(warehouse, devices.update(first, edit).getWarehouseId());
        edit.setWarehouseId(otherWarehouse); assertThrows(BusinessException.class, () -> devices.update(first, edit));
        DeviceStatusRequest status = new DeviceStatusRequest(); status.setStatus("SCRAPPED"); status.setReason("should require business");
        assertThrows(BusinessException.class, () -> devices.updateStatus(first, status));
        assertThrows(BusinessException.class, () -> openings.create(opening(otherWarehouse, item(deviceProduct, "1", first))));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM inv_opening_balance_devices WHERE initialized_device_id IN (?,?)", Integer.class, first, second));
        assertEquals(1, devices.getHistory(first).stream().filter(h -> "OPENING_BALANCE".equals(h.getEventType())).count());
        assertTrue(stocks.checkConsistency(warehouse).isEmpty());
    }

    @Test void validatesDeviceMembershipStatusQuantityPrecisionAndDuplicates() {
        Long device = device(department);
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(deviceProduct, "1"))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(deviceProduct, "2", device))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(deviceProduct, "1.5", device))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(quantityProduct, "1", device))));
        Long otherProduct = product("D2", "DEVICE");
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(otherProduct, "1", device))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(deviceProduct, "2", device, device))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(quantityProduct, "0"))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(quantityProduct, "-1"))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(quantityProduct, "1.00001"))));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(quantityProduct, "1"), item(quantityProduct, "2"))));
        DeviceStatusRequest status = new DeviceStatusRequest(); status.setStatus("SCRAPPED"); status.setReason("test"); devices.updateStatus(device, status);
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(deviceProduct, "1", device))));
        assertEquals(0, stockCount());
    }

    @Test void scopesWarehouseStockLedgerOpeningOptionsAndEveryWrite() {
        Long childWarehouse = warehouses.create(warehouseRequest("child", childDepartment)).getId();
        Long ownDevice = device(department), otherDevice = device(otherDepartment);
        var own = openings.create(opening(warehouse, item(quantityProduct, "3")));
        var outside = openings.create(opening(otherWarehouse, item(quantityProduct, "4")));
        openings.complete(own.getId()); openings.complete(outside.getId());
        current("DEPARTMENT", department);
        assertEquals(Set.of(warehouse, childWarehouse), new HashSet<>(warehouses.getPage(prefix, null, 1, 100).getList().stream().map(WarehouseView::getId).toList()));
        assertEquals(1, stocks.getPage(null, quantityProduct, null, 1, 20).getTotal());
        assertEquals(1, stocks.getLedgerPage(null, quantityProduct, null, 1, 20).getTotal());
        assertEquals(1, openings.getPage(null, null, 1, 20).getTotal());
        assertEquals(List.of(ownDevice), options.getDevices(deviceProduct, null, 1, 20).getList().stream().map(OpeningDeviceOptionView::getId).toList());
        assertThrows(BusinessException.class, () -> warehouses.getById(otherWarehouse));
        assertThrows(BusinessException.class, () -> warehouses.updateStatus(otherWarehouse, false));
        assertThrows(BusinessException.class, () -> stocks.getPage(otherWarehouse, null, null, 1, 20));
        assertThrows(BusinessException.class, () -> stocks.checkConsistency(otherWarehouse));
        assertThrows(BusinessException.class, () -> openings.getById(outside.getId()));
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(deviceProduct, "1", otherDevice))));
        assertThrows(BusinessException.class, () -> openings.create(opening(otherWarehouse, item(quantityProduct, "1"))));
        current("SELF", department);
        assertEquals(0, stocks.getPage(null, quantityProduct, null, 1, 20).getTotal());
        assertEquals(0, openings.getPage(null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> warehouses.create(warehouseRequest("self", department)));
        current("DEPARTMENT", null);
        assertEquals(0, warehouses.getPage(prefix, null, 1, 20).getTotal());
    }

    @Test void disabledWarehouseAndProductRejectCreateEditAndCompletion() {
        var request = opening(warehouse, item(quantityProduct, "5")); var draft = openings.create(request);
        warehouses.updateStatus(warehouse, false);
        assertThrows(BusinessException.class, () -> openings.create(opening(warehouse, item(quantityProduct, "1"))));
        assertThrows(BusinessException.class, () -> openings.update(draft.getId(), request));
        assertThrows(BusinessException.class, () -> openings.complete(draft.getId()));
        warehouses.updateStatus(warehouse, true); products.updateStatus(quantityProduct, false);
        assertThrows(BusinessException.class, () -> openings.complete(draft.getId()));
        products.updateStatus(quantityProduct, true); openings.complete(draft.getId()); assertQuantity(warehouse, quantityProduct, "5");
    }

    @Test void draftEditReplacesItemsAndCompletedDocumentIsImmutable() {
        var request = opening(warehouse, item(quantityProduct, "5")); var draft = openings.create(request);
        Long d = device(department); request.setItems(List.of(item(deviceProduct, "1", d))); request.setWarehouseId(otherWarehouse);
        var updated = openings.update(draft.getId(), request); assertEquals(otherWarehouse, updated.getWarehouseId());
        assertEquals(1, updated.getItems().size()); assertEquals(deviceProduct, updated.getItems().get(0).getProductId());
        openings.complete(draft.getId()); assertQuantity(otherWarehouse, deviceProduct, "1");
        assertThrows(BusinessException.class, () -> openings.update(draft.getId(), request));
        assertEquals(0, stocks.getPage(null, quantityProduct, null, 1, 20).getTotal());
    }

    @Test void auditFailureRollsBackStockDeviceHistoryLedgerAndDocumentTogether() {
        Long d = device(department);
        var draft = openings.create(opening(warehouse, item(quantityProduct, "9"), item(deviceProduct, "1", d)));
        int historyBefore = devices.getHistory(d).size();
        doThrow(new IllegalStateException("injected audit failure")).when(audit).insert(any(), eq("STOCK_OPENING_COMPLETE"), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> openings.complete(draft.getId()));
        assertEquals(0, stockCount()); assertNull(devices.getById(d).getWarehouseId());
        assertEquals(historyBefore, devices.getHistory(d).size());
        assertEquals("DRAFT", openings.getById(draft.getId()).getStatus());
        assertEquals(0, stocks.getLedgerPage(warehouse, null, null, 1, 20).getTotal());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inv_opening_balance_devices WHERE opening_id=? AND initialized_device_id IS NOT NULL", Integer.class, draft.getId()));
        reset(audit); openings.complete(draft.getId()); assertQuantity(warehouse, quantityProduct, "9");
    }

    @Test void consistencyReportsTamperingAndMissingStocksWithoutRepair() {
        Long d = device(department);
        var draft = openings.create(opening(warehouse, item(deviceProduct, "1", d))); openings.complete(draft.getId());
        jdbc.update("UPDATE inv_stocks SET quantity=2 WHERE warehouse_id=? AND product_id=?", warehouse, deviceProduct);
        assertEquals(1, stocks.checkConsistency(warehouse).size()); assertQuantity(warehouse, deviceProduct, "2");
        Long another = device(department); var blocked = openings.create(opening(warehouse, item(deviceProduct, "1", another)));
        assertThrows(BusinessException.class, () -> openings.complete(blocked.getId())); assertNull(devices.getById(another).getWarehouseId());
        jdbc.update("DELETE FROM inv_stocks WHERE warehouse_id=? AND product_id=?", warehouse, deviceProduct);
        assertTrue(stocks.checkConsistency(warehouse).get(0).getMessage().contains("缺少"));
        assertEquals(0, stockCount());
        jdbc.update("INSERT INTO asset_devices(device_code,product_id,warehouse_id) VALUES(?,?,?)", prefix + "invalidQuantityDevice", quantityProduct, warehouse);
        assertEquals(2, stocks.checkConsistency(warehouse).size());
    }

    @Test void databaseConstraintsProtectNegativeLockedForeignKeysAndInitializedDevices() {
        var draft = openings.create(opening(warehouse, item(quantityProduct, "2"))); openings.complete(draft.getId());
        assertCheckConstraint(() -> jdbc.update("UPDATE inv_stocks SET quantity=-1 WHERE warehouse_id=?", warehouse));
        assertCheckConstraint(() -> jdbc.update("UPDATE inv_stocks SET locked_quantity=3 WHERE warehouse_id=?", warehouse));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO inv_stocks(warehouse_id,product_id) VALUES(?,?)", warehouse, quantityProduct));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO inv_stocks(warehouse_id,product_id) VALUES(?,?)", Long.MAX_VALUE, deviceProduct));
        Long d = device(department);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE asset_devices SET warehouse_id=? WHERE id=?", Long.MAX_VALUE, d));
        assertCheckConstraint(() -> jdbc.update("UPDATE inv_stock_ledgers SET after_quantity=99 WHERE warehouse_id=?", warehouse));
        assertQuantity(warehouse, quantityProduct, "2");
        var first = openings.create(opening(warehouse, item(deviceProduct, "1", d)));
        var second = openings.create(opening(otherWarehouse, item(deviceProduct, "1", d)));
        openings.complete(first.getId());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE inv_opening_balance_devices SET initialized_device_id=device_id WHERE opening_id=?", second.getId()));
    }

    @Test void concurrentQuantityCompletionsAccumulateWithoutLostUpdates() throws Exception {
        var a = openings.create(opening(warehouse, item(quantityProduct, "7")));
        var b = openings.create(opening(warehouse, item(quantityProduct, "11")));
        assertEquals(2, race(() -> openings.complete(a.getId()), () -> openings.complete(b.getId())));
        assertQuantity(warehouse, quantityProduct, "18");
        var ledgers = stocks.getLedgerPage(warehouse, quantityProduct, null, 1, 20).getList();
        assertEquals(2, ledgers.size());
        assertEquals(0, ledgers.get(0).getBeforeQuantity().compareTo(ledgers.get(1).getAfterQuantity()));
    }

    @Test void concurrentSameDocumentAndCreateRequestHaveSingleEffect() throws Exception {
        var request = opening(warehouse, item(quantityProduct, "4"));
        assertEquals(1, race(() -> openings.create(request), () -> openings.create(request)));
        var list = openings.getPage(warehouse, null, 1, 20); assertEquals(1, list.getTotal()); Long id = list.getList().get(0).getId();
        assertEquals(1, race(() -> openings.complete(id), () -> openings.complete(id)));
        assertQuantity(warehouse, quantityProduct, "4");
        assertEquals(1, stocks.getLedgerPage(warehouse, quantityProduct, null, 1, 20).getTotal());
    }

    @Test void concurrentSameDeviceAcrossWarehousesCanOnlyInitializeOnce() throws Exception {
        Long d = device(department);
        var a = openings.create(opening(warehouse, item(deviceProduct, "1", d)));
        var b = openings.create(opening(otherWarehouse, item(deviceProduct, "1", d)));
        assertEquals(1, race(() -> openings.complete(a.getId()), () -> openings.complete(b.getId())));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inv_opening_balance_devices WHERE initialized_device_id=?", Integer.class, d));
        assertEquals(0, jdbc.queryForObject("SELECT SUM(quantity) FROM inv_stocks WHERE product_id=?", BigDecimal.class, deviceProduct).compareTo(BigDecimal.ONE));
        assertTrue(stocks.checkConsistency(warehouse).isEmpty()); assertTrue(stocks.checkConsistency(otherWarehouse).isEmpty());
    }

    @Test void concurrentDistinctDevicesMaintainExactStockCount() throws Exception {
        Long d1 = device(department), d2 = device(department);
        var a = openings.create(opening(warehouse, item(deviceProduct, "1", d1)));
        var b = openings.create(opening(warehouse, item(deviceProduct, "1", d2)));
        assertEquals(2, race(() -> openings.complete(a.getId()), () -> openings.complete(b.getId())));
        assertQuantity(warehouse, deviceProduct, "2"); assertTrue(stocks.checkConsistency(warehouse).isEmpty());
    }

    @Test void httpPermissionsRequestIdAuditAndThreadLocalCleanup() throws Exception {
        String token = token("SYSTEM_ADMIN"); Long d = device(department);
        var request = opening(warehouse, item(deviceProduct, "1", d));
        String createId = "p4-create-" + UUID.randomUUID();
        String body = mvc.perform(post("/api/inventory/openings").header("Authorization", "Bearer " + token).header("X-Request-Id", createId).contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        Long id = json.readTree(body).get("data").get("id").asLong(); assertNull(CurrentUserContext.get());
        String completionId = "p4-complete-" + UUID.randomUUID();
        mvc.perform(post("/api/inventory/openings/" + id + "/complete").header("Authorization", "Bearer " + token).header("X-Request-Id", completionId))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", completionId));
        assertNull(CurrentUserContext.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inv_stock_ledgers WHERE request_id=?", Integer.class, completionId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND operation_type='STOCK_OPENING_COMPLETE'", Integer.class, completionId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE request_id=? AND event_type='OPENING_BALANCE'", Integer.class, completionId));
        mvc.perform(post("/api/inventory/openings/" + id + "/complete").header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
        mvc.perform(get("/api/inventory/stocks")).andExpect(status().isUnauthorized());
        String employee = token("EMPLOYEE");
        for (String path : List.of("/api/inventory/stocks", "/api/inventory/ledgers", "/api/inventory/openings", "/api/system/warehouses", "/api/inventory/options/devices?productId=" + deviceProduct)) {
            mvc.perform(get(path).header("Authorization", "Bearer " + employee)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/inventory/openings").header("Authorization", "Bearer " + employee).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isForbidden());
        assertNull(CurrentUserContext.get());
    }

    @Test void httpRejectsStockCrudAndWarehouseStatusBypassAndEnforcesReadOnlyRole() throws Exception {
        String admin = token("SYSTEM_ADMIN");
        String warehouseBody = json.writeValueAsString(warehouseRequest("W", department));
        warehouseBody = warehouseBody.substring(0, warehouseBody.length() - 1) + ",\"status\":false}";
        mvc.perform(put("/api/system/warehouses/" + warehouse).header("Authorization", "Bearer " + admin).contentType("application/json").content(warehouseBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(true));
        mvc.perform(put("/api/inventory/stocks").header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"quantity\":100}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value(40500));
        mvc.perform(post("/api/inventory/openings").header("Authorization", "Bearer " + admin).contentType("application/json").content(json.writeValueAsString(opening(warehouse, item(quantityProduct, "-1")))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory/stocks").param("page", "bad").header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
        String management = token("MANAGEMENT");
        mvc.perform(get("/api/inventory/ledgers").header("Authorization", "Bearer " + management)).andExpect(status().isOk());
        mvc.perform(post("/api/system/warehouses/" + warehouse + "/disable").header("Authorization", "Bearer " + management)).andExpect(status().isForbidden());
        mvc.perform(put("/api/system/warehouses/" + warehouse).header("Authorization", "Bearer " + management).contentType("application/json").content(warehouseBody)).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/openings").header("Authorization", "Bearer " + management).contentType("application/json").content(json.writeValueAsString(opening(warehouse, item(quantityProduct, "1")))))
                .andExpect(status().isForbidden());
        assertNull(CurrentUserContext.get());
    }

    @Test void deviceChangedAfterDraftIsRevalidatedAndEntireCompletionRollsBack() {
        Long d = device(department);
        var draft = openings.create(opening(warehouse, item(quantityProduct, "5"), item(deviceProduct, "1", d)));
        DeviceStatusRequest status = new DeviceStatusRequest(); status.setStatus("SCRAPPED"); status.setReason("draft is not a reservation");
        devices.updateStatus(d, status);
        assertThrows(BusinessException.class, () -> openings.complete(draft.getId()));
        assertEquals("DRAFT", openings.getById(draft.getId()).getStatus());
        assertEquals(0, stockCount()); assertEquals(0, stocks.getLedgerPage(warehouse, null, null, 1, 20).getTotal());
    }

    @Test void stockAndOpeningReferencesFreezeProductTypeAndUnit() {
        var draft = openings.create(opening(warehouse, item(quantityProduct, "1")));
        ProductRequest change = new ProductRequest(); change.setProductCode(prefix + "Q"); change.setProductName(prefix + "Q");
        change.setCategoryId(category); change.setBrandId(brand); change.setUnitId(unit); change.setProductType("DEVICE");
        assertThrows(BusinessException.class, () -> products.update(quantityProduct, change));
        change.setProductType("QUANTITY"); change.setProductName("ordinary product update");
        products.update(quantityProduct, change); openings.complete(draft.getId());
        assertQuantity(warehouse, quantityProduct, "1");
    }

    @Test void realSchemaIndexesForeignKeysAndFlywayAreApplied() {
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.0."));
        assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND CAST(version AS UNSIGNED) BETWEEN 1 AND 9", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0", Integer.class));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('wh_warehouses','inv_stocks','inv_stock_ledgers','inv_opening_balances','inv_opening_balance_items','inv_opening_balance_devices')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_asset_devices_warehouse'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='inv_opening_balance_devices' AND index_name='uk_inv_initialized_device' AND non_unique=0", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='inv_stocks' AND index_name='uk_inv_stocks_warehouse_product' AND non_unique=0", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inv_stocks' AND column_name='device_id'", Integer.class));
    }

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
