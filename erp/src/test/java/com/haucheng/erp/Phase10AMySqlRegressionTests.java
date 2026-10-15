package com.haucheng.erp;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.service.DashboardQueryService;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.DepartmentBriefView;
import com.haucheng.erp.vo.DashboardSummaryView;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@EnabledIfEnvironmentVariable(named = "ERP_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "erp.auth.seed-username=", "erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class Phase10AMySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardQueryService service;
    @Autowired MockMvc mvc;
    @Autowired SqlSessionTemplate sqlSession;
    private String prefix;
    private Long department, otherDepartment, user, otherUser, warehouse, otherWarehouse;
    private Long product, deviceProduct, firstDevice, secondDevice, otherDevice, inbound, outbound, requisition, stocktake;
    private DashboardSummaryView baseline;

    @BeforeEach void setup() {
        assertTrue(System.getenv("DB_URL").matches("jdbc:mysql://192\\.168\\.1\\.247:3307/erp(?:\\?.*)?"),
                "Tests must use the approved MySQL database");
        prefix = "P10A_" + UUID.randomUUID().toString().replace("-", "");
        current("ALL", true);
        baseline = service.summary();
        Long category = id("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)", prefix, prefix);
        Long brand = id("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)", prefix, prefix);
        Long unit = id("INSERT INTO md_units(unit_code,unit_name) VALUES(?,?)", prefix, prefix);
        department = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix, prefix);
        otherDepartment = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix+"O", prefix+"O");
        user = id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)", prefix, "test-only", prefix, department);
        otherUser = id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)", prefix+"O", "test-only", prefix+"O", otherDepartment);
        warehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)", prefix, prefix, department);
        otherWarehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)", prefix+"O", prefix+"O", otherDepartment);
        product = id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type,safety_stock) VALUES(?,?,?,?,?,'QUANTITY',4)", prefix+"Q", prefix+"Q", category, brand, unit);
        deviceProduct = id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type) VALUES(?,?,?,?,?,'DEVICE')", prefix+"D", prefix+"D", category, brand, unit);
        firstDevice = id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id,warehouse_id,serial_number) VALUES(?,?,'IN_STOCK',?,?,?,?)", prefix+"D1", deviceProduct, department, user, warehouse, prefix+"SN1");
        secondDevice = id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id,warehouse_id,serial_number) VALUES(?,?,'IN_STOCK',?,?,?,?)", prefix+"D2", deviceProduct, department, user, warehouse, prefix+"SN2");
        otherDevice = id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id,serial_number) VALUES(?,?,'IN_USE',?,?,?)", prefix+"D3", deviceProduct, otherDepartment, otherUser, prefix+"SN3");
        update("INSERT INTO inv_stocks(warehouse_id,product_id,quantity) VALUES(?,?,1)", warehouse, product);
        update("INSERT INTO inv_stocks(warehouse_id,product_id,quantity) VALUES(?,?,2)", warehouse, deviceProduct);
        update("INSERT INTO asset_device_history(device_id,event_type,to_status,detail) VALUES(?,'STATUS','IN_STOCK',?)", firstDevice, prefix);
        update("INSERT INTO asset_device_history(device_id,event_type,to_status,detail) VALUES(?,'STATUS','IN_STOCK',?)", firstDevice, prefix);
        update("INSERT INTO inv_stock_ledgers(warehouse_id,product_id,business_type,business_no,direction,quantity,before_quantity,after_quantity) VALUES(?,?,'OPENING',?,'IN',1,0,1)", warehouse, product, prefix);
        update("INSERT INTO inv_stock_ledgers(warehouse_id,product_id,business_type,business_no,direction,quantity,before_quantity,after_quantity) VALUES(?,?,'OPENING',?,'IN',1,0,1)", warehouse, product, prefix+"2");
        inbound = id("INSERT INTO inv_inbound_orders(order_no,client_request_id,warehouse_id,creator_user_id,status,submitted_at) VALUES(?,?,?,?,'PENDING_APPROVAL',NOW())", prefix+"IN", prefix+"IN", warehouse, user);
        update("INSERT INTO inv_inbound_items(order_id,product_id,product_type,quantity) VALUES(?,?,'QUANTITY',1)", inbound, product);
        update("INSERT INTO inv_inbound_items(order_id,product_id,product_type,quantity) VALUES(?,?,'DEVICE',1)", inbound, deviceProduct);
        outbound = id("INSERT INTO inv_outbound_orders(order_no,client_request_id,warehouse_id,creator_user_id,status,submitted_at) VALUES(?,?,?,?,'PENDING_APPROVAL',NOW())", prefix+"OUT", prefix+"OUT", otherWarehouse, otherUser);
        requisition = id("INSERT INTO apply_requisitions(order_no,client_request_id,warehouse_id,creator_user_id,department_id,status,submitted_at) VALUES(?,?,?,?,?,'PENDING_APPROVAL',NOW())", prefix+"REQ", prefix+"REQ", warehouse, user, department);
        stocktake = id("INSERT INTO inv_stocktakes(stocktake_no,client_request_id,warehouse_id,creator_user_id,status,submitted_at) VALUES(?,?,?,?,'COUNTED',NOW())", prefix+"ST", prefix+"ST", warehouse, user);
        update("INSERT INTO inv_stocktake_items(stocktake_id,product_id,product_type) VALUES(?,?,'QUANTITY')", stocktake, product);
        update("INSERT INTO inv_stocktake_items(stocktake_id,product_id,product_type) VALUES(?,?,'DEVICE')", stocktake, deviceProduct);
        update("INSERT INTO asset_repair_orders(order_no,client_request_id,device_id,device_code,product_id,product_code,product_name,applicant_user_id,department_id,reason,fault_phenomenon,fault_description,original_status,status,submitted_at) VALUES(?,?,?,?,?,?,?,?,?,'test','test','test','IN_STOCK','SUBMITTED',NOW())",
                prefix+"REPAIR", prefix+"REPAIR", firstDevice, prefix+"D1", deviceProduct, prefix+"D", prefix+"D", user, department);
        update("INSERT INTO asset_warranty_orders(order_no,client_request_id,device_id,device_code,product_id,product_code,product_name,applicant_user_id,department_id,reason,fault_description,warranty_status,status,submitted_at) VALUES(?,?,?,?,?,?,?,?,?,'test','test','VALID','SUBMITTED',NOW())",
                prefix+"WARRANTY", prefix+"WARRANTY", firstDevice, prefix+"D1", deviceProduct, prefix+"D", prefix+"D", user, department);
        update("INSERT INTO asset_exchange_orders(order_no,client_request_id,device_id,device_code,product_id,product_code,product_name,applicant_user_id,department_id,reason,original_status,new_device_id,new_device_code,new_warehouse_id,status,submitted_at) VALUES(?,?,?,?,?,?,?,?,?,'test','IN_USE',?,?,?,'SUBMITTED',NOW())",
                prefix+"EXCHANGE", prefix+"EXCHANGE", otherDevice, prefix+"D3", deviceProduct, prefix+"D", prefix+"D", otherUser, otherDepartment, secondDevice, prefix+"D2", warehouse);
        current("ALL", true);
    }

    @AfterEach void clear() { CurrentUserContext.clear(); }

    @AfterTransaction void verifyRollback() {
        if (prefix != null) {
            assertEquals(0, count("SELECT COUNT(*) FROM md_products WHERE product_code LIKE ?", prefix+"%"));
            assertEquals(0, count("SELECT COUNT(*) FROM asset_devices WHERE device_code LIKE ?", prefix+"%"));
            assertEquals(0, count("SELECT COUNT(*) FROM sys_user WHERE username LIKE ?", prefix+"%"));
            for (String[] field : List.of(new String[]{"md_categories", "category_code"}, new String[]{"md_brands", "brand_code"},
                    new String[]{"md_units", "unit_code"}, new String[]{"sys_department", "department_code"},
                    new String[]{"wh_warehouses", "warehouse_code"}, new String[]{"sys_role", "role_code"},
                    new String[]{"inv_inbound_orders", "order_no"}, new String[]{"inv_outbound_orders", "order_no"},
                    new String[]{"apply_requisitions", "order_no"}, new String[]{"inv_stocktakes", "stocktake_no"},
                    new String[]{"asset_repair_orders", "order_no"}, new String[]{"asset_warranty_orders", "order_no"},
                    new String[]{"asset_exchange_orders", "order_no"}, new String[]{"inv_stock_ledgers", "business_no"})) {
                assertEquals(0, count("SELECT COUNT(*) FROM " + field[0] + " WHERE " + field[1] + " LIKE ?", prefix+"%"), field[0]);
            }
            assertEquals(0, count("SELECT COUNT(*) FROM inv_stocks WHERE product_id IN (?,?)", product, deviceProduct));
            assertEquals(0, count("SELECT COUNT(*) FROM asset_device_history WHERE device_id IN (?,?,?)", firstDevice, secondDevice, otherDevice));
            assertEquals(0, count("SELECT COUNT(*) FROM sys_access_token WHERE user_id IN (?,?)", user, otherUser));
            assertEquals(0, count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id IN (?,?)", user, otherUser));
        }
    }

    @Test void summaryUsesPrimaryRowsAndVisibleStockOnly() {
        var summary = service.summary();
        for (String metric : List.of("products", "activeProducts", "devices", "activeWarehouses", "stockPositions", "stockProducts",
                "stockWarehouses", "lowStockProducts", "pendingInbounds", "pendingOutbounds", "pendingRequisitions",
                "pendingStocktakes", "activeRepairs", "activeWarranties", "pendingExchanges")) {
            long added = switch (metric) {
                case "products", "activeProducts", "activeWarehouses", "stockPositions", "stockProducts" -> 2;
                case "devices" -> 3;
                default -> 1;
            };
            assertEquals(baseline.getMetrics().get(metric) + added, summary.getMetrics().get(metric), metric);
        }
        assertEquals(baseline.getDeviceStatuses().get("IN_STOCK") + 2, summary.getDeviceStatuses().get("IN_STOCK"));
        assertEquals(baseline.getDeviceStatuses().get("IN_USE") + 1, summary.getDeviceStatuses().get("IN_USE"));
        assertEquals(0, baseline.getStockQuantity().add(new BigDecimal("3")).compareTo(summary.getStockQuantity()));
        assertTrue(summary.getRecent().size() <= 8);
        assertFalse(summary.getRecent().stream().anyMatch(row -> "PRODUCT".equals(row.getType())));
        var recent = summary.getRecent().stream().filter(row -> row.getTitle().startsWith(prefix)).toList();
        assertEquals(7, recent.size(), "One recent row per fixture business order");
        assertEquals(Set.of("INBOUND", "OUTBOUND", "REQUISITION", "STOCKTAKE", "REPAIR", "WARRANTY", "EXCHANGE"),
                recent.stream().map(row -> row.getType()).collect(Collectors.toSet()));
    }

    @Test void queryCoversTypesWithoutChildRowInflation() {
        for (String type : List.of("PRODUCT", "DEVICE", "WAREHOUSE", "INBOUND", "OUTBOUND", "REQUISITION", "STOCKTAKE", "REPAIR", "WARRANTY", "EXCHANGE")) {
            var page = service.query(type, prefix, null, null, null, 1, 20);
            assertTrue(page.getTotal() > 0, type);
            assertEquals(page.getTotal(), page.getList().size(), type);
        }
        assertEquals(1, service.query("INBOUND", prefix+"IN", null, null, null, 1, 20).getTotal());
        assertEquals(1, service.query("STOCKTAKE", prefix+"ST", null, null, null, 1, 20).getTotal());
        assertEquals(3, service.query("DEVICE", prefix+"D", null, null, null, 1, 20).getTotal());
        assertEquals(1, service.query("INBOUND", prefix+"Q", null, null, null, 1, 20).getTotal());
        assertEquals(1, service.query("INBOUND", prefix+"D", null, null, null, 1, 20).getTotal());
        assertEquals(1, service.query(null, prefix, null, null, null, 2, 1).getList().size());
        var page1 = service.query(null, prefix, null, null, null, 1, 1).getList().get(0);
        var page2 = service.query(null, prefix, null, null, null, 2, 1).getList().get(0);
        assertNotEquals(page1.getType()+page1.getId(), page2.getType()+page2.getId());
        assertEquals(page1, service.query(null, prefix, null, null, null, 1, 1).getList().get(0));
        assertEquals(0, service.query(null, prefix+"%_NO_MATCH", null, null, null, 1, 20).getTotal());
        assertEquals(0, service.query(null, prefix+"%", null, null, null, 1, 20).getTotal());
        assertEquals(0, service.query("INBOUND", prefix, "COMPLETED", null, null, 1, 20).getTotal());
        assertEquals(1, service.query("INBOUND", prefix, "PENDING_APPROVAL", LocalDate.now().minusDays(1), LocalDate.now(), 1, 20).getTotal());
    }

    @Test void departmentSelfAndPermissionBoundariesDiffer() {
        current("DEPARTMENT", true);
        assertEquals(0, service.query("OUTBOUND", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(1, service.query("INBOUND", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(2, service.query("DEVICE", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(1L, service.summary().getMetrics().get("activeWarehouses"));
        assertEquals(0L, service.summary().getMetrics().get("pendingOutbounds"));
        assertEquals(0L, service.summary().getMetrics().get("pendingExchanges"));
        assertTrue(service.summary().getRecent().stream().noneMatch(row -> "OUTBOUND".equals(row.getType()) || "EXCHANGE".equals(row.getType())));
        current("SELF", true);
        assertEquals(0, service.query("WAREHOUSE", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(2, service.query("DEVICE", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(1, service.query("REQUISITION", prefix, null, null, null, 1, 20).getTotal());
        assertFalse(service.summary().getMetrics().containsKey("stockPositions"));
        assertEquals(1L, service.summary().getMetrics().get("activeRepairs"));
        assertEquals(1L, service.summary().getMetrics().get("activeWarranties"));
        assertEquals(0L, service.summary().getMetrics().get("pendingExchanges"));
        current("ALL", false);
        assertEquals(2, service.query("PRODUCT", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(0, service.query(null, prefix+"D1", null, null, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> service.query("DEVICE", prefix, null, null, null, 1, 20));
        assertFalse(service.summary().getMetrics().containsKey("devices"));
    }

    @Test void anotherSelfUserSeesOnlyOwnBusinessRows() {
        var current = new CurrentUserView();
        current.setUserId(otherUser); current.setDataScope("SELF");
        current.setPermissions(jdbc.queryForList("SELECT permission_code FROM sys_permission", String.class));
        var home = new DepartmentBriefView(); home.setId(otherDepartment); current.setDepartment(home);
        CurrentUserContext.set(current);
        assertEquals(1, service.query("DEVICE", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(0, service.query("REQUISITION", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(1, service.query("EXCHANGE", prefix, null, null, null, 1, 20).getTotal());
        assertEquals(1L, service.summary().getMetrics().get("devices"));
    }

    @Test void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/query")).andExpect(status().isUnauthorized());
    }

    @Test void emptyPermissionsAndMissingDepartmentReturnNoScopedData() {
        current("ALL", true);
        CurrentUserContext.require().setPermissions(List.of());
        var empty = service.summary();
        assertTrue(empty.getMetrics().isEmpty());
        assertTrue(empty.getDeviceStatuses().isEmpty());
        assertTrue(empty.getRecent().isEmpty());
        assertNull(empty.getStockQuantity());
        assertEquals(0, service.query(null, null, null, null, null, 1, 20).getTotal());
        current("DEPARTMENT", true);
        CurrentUserContext.require().setDepartment(null);
        var scoped = service.summary();
        assertEquals(0L, scoped.getMetrics().get("devices"));
        assertEquals(0L, scoped.getMetrics().get("activeWarehouses"));
        assertEquals(0L, scoped.getMetrics().get("lowStockProducts"));
        assertEquals(0, scoped.getStockQuantity().signum());
        assertTrue(scoped.getRecent().isEmpty());
        assertEquals(0, service.query("DEVICE", prefix, null, null, null, 1, 20).getTotal());
    }

    @Test void lowStockUsesVisibleSumAndStrictEnabledThreshold() {
        update("INSERT INTO inv_stocks(warehouse_id,product_id,quantity) VALUES(?,?,3)", otherWarehouse, product);
        assertEquals(baseline.getMetrics().get("lowStockProducts"), service.summary().getMetrics().get("lowStockProducts"));
        assertEquals(baseline.getMetrics().get("stockProducts") + 2, service.summary().getMetrics().get("stockProducts"));
        current("DEPARTMENT", true);
        assertEquals(1L, service.summary().getMetrics().get("lowStockProducts"));
        update("UPDATE md_products SET safety_stock=1 WHERE id=?", product);
        assertEquals(0L, service.summary().getMetrics().get("lowStockProducts"));
        update("UPDATE inv_stocks SET quantity=0 WHERE product_id=? AND warehouse_id=?", product, warehouse);
        assertEquals(1L, service.summary().getMetrics().get("lowStockProducts"));
        update("UPDATE md_products SET status=0 WHERE id=?", product);
        assertEquals(0L, service.summary().getMetrics().get("lowStockProducts"));
        update("UPDATE md_products SET status=1,safety_stock=0 WHERE id=?", product);
        assertEquals(0L, service.summary().getMetrics().get("lowStockProducts"));
    }

    @Test void historicalDevicesAndDateBoundariesRemainSearchable() {
        update("UPDATE asset_devices SET status='SHIPPED',created_at='2020-05-10 23:59:59',asset_no=?,mac_address=? WHERE id=?",
                prefix+"ASSET", "02:10:AA:BB:CC:DD", otherDevice);
        var found = service.query("DEVICE", prefix+"SN3", "SHIPPED", LocalDate.of(2020,5,10), LocalDate.of(2020,5,10), 1, 20);
        assertEquals(1, found.getTotal());
        assertEquals(otherDevice, found.getList().get(0).getId());
        assertEquals("/assets/devices", found.getList().get(0).getRoute());
        assertEquals(0, service.query("DEVICE", prefix+"SN3", null, LocalDate.of(2020,5,11), null, 1, 20).getTotal());
        assertEquals(0, service.query("DEVICE", prefix+"SN3", null, null, LocalDate.of(2020,5,9), 1, 20).getTotal());
        update("UPDATE asset_devices SET status='SCRAPPED' WHERE id=?", otherDevice);
        assertEquals("SCRAPPED", service.query("DEVICE", prefix+"ASSET", null, null, null, 1, 20).getList().get(0).getStatus());
        assertEquals(otherDevice, service.query("DEVICE", "02:10:AA:BB:CC:DD", null, null, null, 1, 20).getList().get(0).getId());
        assertEquals(0, service.query(null, "' OR 1=1 --", null, null, null, 1, 20).getTotal());
    }

    @Test void relatedDeviceSnapshotsMatchOneOrderWithoutExpandingRows() {
        Long inboundItem = jdbc.queryForObject("SELECT id FROM inv_inbound_items WHERE order_id=? AND product_id=?", Long.class, inbound, deviceProduct);
        Long outboundItem = id("INSERT INTO inv_outbound_items(order_id,product_id,product_type,quantity) VALUES(?,?,'DEVICE',2)", outbound, deviceProduct);
        Long requisitionItem = id("INSERT INTO apply_requisition_items(order_id,product_id,product_type,quantity) VALUES(?,?,'DEVICE',2)", requisition, deviceProduct);
        Long stocktakeItem = jdbc.queryForObject("SELECT id FROM inv_stocktake_items WHERE stocktake_id=? AND product_id=?", Long.class, stocktake, deviceProduct);
        for (int n=1; n<=2; n++) {
            Long device = n==1 ? firstDevice : secondDevice;
            update("INSERT INTO inv_inbound_devices(order_id,item_id,device_id,device_code,serial_number) VALUES(?,?,?,?,?)", inbound, inboundItem, device, prefix+"D"+n, prefix+"SN"+n);
            update("INSERT INTO inv_outbound_devices(order_id,item_id,device_id,device_code,serial_number) VALUES(?,?,?,?,?)", outbound, outboundItem, device, prefix+"D"+n, prefix+"SN"+n);
            update("INSERT INTO apply_requisition_devices(order_id,item_id,device_id,device_code,serial_number) VALUES(?,?,?,?,?)", requisition, requisitionItem, device, prefix+"D"+n, prefix+"SN"+n);
            update("INSERT INTO inv_stocktake_device_checks(stocktake_id,item_id,device_id,device_code,serial_number,expected) VALUES(?,?,?,?,?,1)", stocktake, stocktakeItem, device, prefix+"D"+n, prefix+"SN"+n);
        }
        for (String type : List.of("INBOUND", "OUTBOUND", "REQUISITION", "STOCKTAKE")) {
            assertEquals(1, service.query(type, prefix+"SN", null, null, null, 1, 20).getTotal(), type);
        }
        current("DEPARTMENT", true);
        assertEquals(0, service.query("OUTBOUND", prefix+"SN", null, null, null, 1, 20).getTotal());
    }

    @Test void authenticatedHttpUsesPersistedRbacAndRequestIdWithoutAuditWrites() throws Exception {
        Long role = id("INSERT INTO sys_role(role_code,role_name,data_scope) VALUES(?,?,'DEPARTMENT')", prefix+"ROLE", prefix+"ROLE");
        update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code IN ('system.device.view','inventory.stock.view')", role);
        update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, role);
        String token = UUID.randomUUID().toString();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        update("INSERT INTO sys_access_token(token_hash,user_id,expires_at) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 1 HOUR))", hash, user);
        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + token).header("X-Request-Id", prefix))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", prefix))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.metrics.devices").value(2))
                .andExpect(jsonPath("$.data.metrics.products").doesNotExist());
        mvc.perform(get("/api/query").header("Authorization", "Bearer " + token).param("type", "DEVICE").param("keyword", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/query").header("Authorization", "Bearer " + token).param("type", "PRODUCT"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/query").header("Authorization", "Bearer " + token).param("size", "51"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/query").header("Authorization", "Bearer " + token).param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
        assertEquals(0, count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=?", user));
    }

    @Test void invalidInputAndReadOnlyCallsDoNotWriteBusinessTables() {
        assertThrows(BusinessException.class, () -> service.query("UNKNOWN", null, null, null, null, 1, 20));
        assertThrows(BusinessException.class, () -> service.query(null, "x".repeat(101), null, null, null, 1, 20));
        assertThrows(BusinessException.class, () -> service.query(null, null, null, null, null, 1, 51));
        assertThrows(BusinessException.class, () -> service.query(null, null, null, null, null, 1001, 1));
        assertThrows(BusinessException.class, () -> service.query(null, null, null, LocalDate.now(), LocalDate.now().minusDays(1), 1, 20));
        assertThrows(BusinessException.class, () -> service.query(null, null, "bad status", null, null, 1, 20));
        assertThrows(BusinessException.class, () -> service.query(null, null, null, null, LocalDate.MAX, 1, 20));
        assertThrows(BusinessException.class, () -> service.query(null, null, null, LocalDate.MIN, null, 1, 20));
        long ledgers = count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE business_no LIKE ?", prefix+"%");
        long histories = count("SELECT COUNT(*) FROM asset_device_history WHERE device_id=?", firstDevice);
        var deviceRows = jdbc.queryForList("SELECT id,status,warehouse_id,department_id,user_id,version FROM asset_devices WHERE id IN (?,?,?)", firstDevice, secondDevice, otherDevice);
        var stocks = jdbc.queryForList("SELECT * FROM inv_stocks WHERE product_id IN (?,?)", product, deviceProduct);
        service.summary(); service.query(null, prefix, null, null, null, 1, 20);
        assertEquals(ledgers, count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE business_no LIKE ?", prefix+"%"));
        assertEquals(histories, count("SELECT COUNT(*) FROM asset_device_history WHERE device_id=?", firstDevice));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=?", user));
        assertEquals(deviceRows, jdbc.queryForList("SELECT id,status,warehouse_id,department_id,user_id,version FROM asset_devices WHERE id IN (?,?,?)", firstDevice, secondDevice, otherDevice));
        assertEquals(stocks, jdbc.queryForList("SELECT * FROM inv_stocks WHERE product_id IN (?,?)", product, deviceProduct));
    }

    private void current(String dataScope, boolean allPermissions) {
        var current = new CurrentUserView();
        current.setUserId(user); current.setDataScope(dataScope);
        current.setPermissions(new ArrayList<>(allPermissions
                ? jdbc.queryForList("SELECT permission_code FROM sys_permission", String.class)
                : List.of("system.product.view")));
        var home = new DepartmentBriefView(); home.setId(department); current.setDepartment(home);
        CurrentUserContext.set(current);
    }

    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void update(String sql, Object... args) {
        jdbc.update(sql, args);
        // JDBC fixture changes bypass MyBatis, so discard its session cache inside this test transaction.
        sqlSession.clearCache();
    }
    private Long id(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        sqlSession.clearCache();
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
