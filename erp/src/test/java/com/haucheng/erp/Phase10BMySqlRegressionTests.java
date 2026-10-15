package com.haucheng.erp;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.dto.ReportRequest;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.service.ReportService;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.DepartmentBriefView;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "ERP_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {"spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "erp.auth.seed-username=", "erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class Phase10BMySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionTemplate sqlSession;
    @Autowired ReportService reports;
    @Autowired MockMvc mvc;
    private String prefix;
    private Long department, otherDepartment, user, otherUser, warehouse, otherWarehouse, product, deviceProduct;
    private Long firstDevice, secondDevice, historicalDevice;
    private Long inbound, outbound, requisition, stocktake;

    @BeforeEach void setup() {
        assertTrue(System.getenv("DB_URL").matches("jdbc:mysql://192\\.168\\.1\\.247:3307/erp(?:\\?.*)?"));
        prefix = "P10B_" + UUID.randomUUID().toString().replace("-", "");
        Long category = id("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)", prefix, prefix);
        Long brand = id("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)", prefix, prefix);
        Long unit = id("INSERT INTO md_units(unit_code,unit_name) VALUES(?,?)", prefix, prefix);
        department = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix, prefix);
        otherDepartment = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix+"O", prefix+"O");
        user = id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)", prefix, "test-only", prefix, department);
        otherUser = id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)", prefix+"O", "test-only", prefix+"O", otherDepartment);
        warehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)", prefix, prefix, department);
        otherWarehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)", prefix+"O", prefix+"O", otherDepartment);
        product = id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type,safety_stock) VALUES(?,?,?,?,?,'QUANTITY',5)", "001"+prefix, prefix, category, brand, unit);
        deviceProduct = id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type) VALUES(?,?,?,?,?,'DEVICE')", prefix+"D", prefix+"D", category, brand, unit);
        firstDevice = id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id,warehouse_id) VALUES(?,?,'IN_STOCK',?,?,?)", prefix+"D1", deviceProduct, department, user, warehouse);
        secondDevice = id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id,warehouse_id) VALUES(?,?,'IN_STOCK',?,?,?)", prefix+"D2", deviceProduct, department, user, warehouse);
        historicalDevice = id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id) VALUES(?,?,'SCRAPPED',?,?)", prefix+"D3", deviceProduct, otherDepartment, otherUser);
        update("INSERT INTO inv_stocks(warehouse_id,product_id,quantity,locked_quantity) VALUES(?,?,3,1)", warehouse, product);
        update("INSERT INTO inv_stocks(warehouse_id,product_id,quantity) VALUES(?,?,2)", warehouse, deviceProduct);
        update("INSERT INTO asset_device_history(device_id,event_type,to_status,detail) VALUES(?,'STATUS','IN_STOCK',?)", firstDevice, prefix);
        update("INSERT INTO asset_device_history(device_id,event_type,to_status,detail) VALUES(?,'STATUS','IN_STOCK',?)", firstDevice, prefix);
        update("INSERT INTO inv_stock_ledgers(warehouse_id,product_id,business_type,business_no,direction,quantity,before_quantity,after_quantity) VALUES(?,?,'OPENING',?,'IN',1,0,1)", warehouse, product, prefix+"L1");
        update("INSERT INTO inv_stock_ledgers(warehouse_id,product_id,business_type,business_no,direction,quantity,before_quantity,after_quantity) VALUES(?,?,'OPENING',?,'IN',1,0,1)", warehouse, product, prefix+"L2");
        inbound = id("INSERT INTO inv_inbound_orders(order_no,client_request_id,warehouse_id,creator_user_id) VALUES(?,?,?,?)", prefix+"IN", prefix+"IN", warehouse, user);
        id("INSERT INTO inv_inbound_items(order_id,product_id,product_type,quantity) VALUES(?,?,'QUANTITY',2)", inbound, product);
        Long inboundDeviceItem = id("INSERT INTO inv_inbound_items(order_id,product_id,product_type,quantity) VALUES(?,?,'DEVICE',2)", inbound, deviceProduct);
        for (Long device : List.of(firstDevice, secondDevice)) {
            update("INSERT INTO inv_inbound_devices(order_id,item_id,device_id,device_code) VALUES(?,?,?,?)", inbound, inboundDeviceItem, device, prefix+"D"+device);
        }
        outbound = id("INSERT INTO inv_outbound_orders(order_no,client_request_id,warehouse_id,creator_user_id,status,submitted_at,approver_user_id,approved_at,executor_user_id,executed_at) VALUES(?,?,?,?,'COMPLETED',NOW(),?,NOW(),?,NOW())", prefix+"OUT", prefix+"OUT", otherWarehouse, otherUser, otherUser, otherUser);
        id("INSERT INTO inv_outbound_items(order_id,product_id,product_type,quantity) VALUES(?,?,'QUANTITY',7)", outbound, product);
        requisition = id("INSERT INTO apply_requisitions(order_no,client_request_id,warehouse_id,creator_user_id,department_id) VALUES(?,?,?,?,?)", prefix+"REQ", prefix+"REQ", warehouse, user, department);
        id("INSERT INTO apply_requisition_items(order_id,product_id,product_type,quantity) VALUES(?,?,'QUANTITY',4)", requisition, product);
        stocktake = id("INSERT INTO inv_stocktakes(stocktake_no,client_request_id,warehouse_id,creator_user_id,status,submitted_at) VALUES(?,?,?,?,'COUNTED',NOW())", prefix+"ST", prefix+"ST", warehouse, user);
        id("INSERT INTO inv_stocktake_items(stocktake_id,product_id,product_type,snapshot_quantity,counted_quantity,difference_quantity) VALUES(?,?,'QUANTITY',3,4,1)", stocktake, product);
        Long stocktakeDeviceItem = id("INSERT INTO inv_stocktake_items(stocktake_id,product_id,product_type,snapshot_quantity,counted_quantity,difference_quantity) VALUES(?,?,'DEVICE',2,1,-1)", stocktake, deviceProduct);
        update("INSERT INTO inv_stocktake_device_checks(stocktake_id,item_id,device_id,device_code,expected,observed,difference_code) VALUES(?,?,?,?,1,0,'MISSING')", stocktake, stocktakeDeviceItem, firstDevice, prefix+"D1");
        update("INSERT INTO inv_stocktake_device_checks(stocktake_id,item_id,device_id,device_code,expected,observed,difference_code) VALUES(?,?,?,?,1,1,'MATCH')", stocktake, stocktakeDeviceItem, secondDevice, prefix+"D2");
        update("INSERT INTO asset_repair_orders(order_no,client_request_id,device_id,device_code,product_id,product_code,product_name,applicant_user_id,department_id,reason,fault_phenomenon,fault_description,original_status,notes,remark) VALUES(?,?,?,?,?,?,?,?,?,'fault','fault','fault','IN_STOCK','careful','remark')", prefix+"REP", prefix+"REP", firstDevice, prefix+"D1", deviceProduct, prefix+"D", prefix+"D", user, department);
        update("INSERT INTO asset_warranty_orders(order_no,client_request_id,device_id,device_code,product_id,product_code,product_name,applicant_user_id,department_id,reason,fault_description,warranty_status,decision) VALUES(?,?,?,?,?,?,?,?,?,'fault','fault','EXPIRED','REJECTED')", prefix+"WAR", prefix+"WAR", firstDevice, prefix+"D1", deviceProduct, prefix+"D", prefix+"D", user, department);
        update("INSERT INTO asset_exchange_orders(order_no,client_request_id,device_id,device_code,product_id,product_code,product_name,applicant_user_id,department_id,reason,original_status,new_device_id,new_device_code,new_warehouse_id) VALUES(?,?,?,?,?,?,?,?,?,'broken','IN_USE',?,?,?)", prefix+"EX", prefix+"EX", historicalDevice, prefix+"D3", deviceProduct, prefix+"D", prefix+"D", otherUser, otherDepartment, secondDevice, prefix+"D2", warehouse);
        current("ALL", true);
    }

    @AfterEach void clear() { CurrentUserContext.clear(); }

    @AfterTransaction void verifyRollback() {
        if (prefix == null) return;
        for (String[] table : List.of(new String[]{"md_products", "product_name"}, new String[]{"asset_devices", "device_code"},
                new String[]{"sys_user", "username"}, new String[]{"sys_department", "department_code"},
                new String[]{"wh_warehouses", "warehouse_code"}, new String[]{"inv_inbound_orders", "order_no"},
                new String[]{"inv_outbound_orders", "order_no"}, new String[]{"apply_requisitions", "order_no"},
                new String[]{"inv_stocktakes", "stocktake_no"}, new String[]{"asset_repair_orders", "order_no"},
                new String[]{"asset_warranty_orders", "order_no"}, new String[]{"asset_exchange_orders", "order_no"},
                new String[]{"inv_stock_ledgers", "business_no"}, new String[]{"sys_role", "role_code"})) {
            assertEquals(0L, count("SELECT COUNT(*) FROM " + table[0] + " WHERE " + table[1] + " LIKE ?", "%"+prefix+"%"), table[0]);
        }
        assertEquals(0L, count("SELECT COUNT(*) FROM inv_stocks WHERE product_id IN (?,?)", product, deviceProduct));
        assertEquals(0L, count("SELECT COUNT(*) FROM asset_device_history WHERE device_id=?", firstDevice));
        assertEquals(0L, count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id IN (?,?)", user, otherUser));
        assertEquals(0L, count("SELECT COUNT(*) FROM sys_access_token WHERE user_id IN (?,?)", user, otherUser));
    }

    @Test void allSevenReportsUseRealRowsWithoutJoinInflation() {
        for (String kind : List.of("STOCK","INBOUND","OUTBOUND","REQUISITION","DEVICE","STOCKTAKE","SERVICE")) {
            ReportRequest request = search(prefix);
            if ("STOCK".equals(kind)) request.setKeyword(null);
            var page = reports.page(kind, request);
            assertFalse(page.getPage().getList().isEmpty(), kind);
            assertEquals(page.getPage().getTotal(), page.getSummary().getRowCount(), kind);
        }
        ReportRequest request = search(prefix+"IN");
        var inboundPage = reports.page("INBOUND", request);
        assertEquals(2, inboundPage.getPage().getTotal());
        assertEquals(1L, inboundPage.getSummary().getDocumentCount());
        assertEquals(0, inboundPage.getSummary().getRequestedQuantity().compareTo(new BigDecimal("4")));
        assertEquals(0, inboundPage.getSummary().getExecutedQuantity().signum());
        update("UPDATE inv_inbound_orders SET status='COMPLETED',submitted_at=NOW(),approver_user_id=?,approved_at=NOW(),executor_user_id=?,executed_at=NOW() WHERE id=?",
                user, user, inbound);
        assertEquals(0, reports.page("INBOUND", search(prefix+"IN")).getSummary().getExecutedQuantity()
                .compareTo(new BigDecimal("4")));
        var outboundPage = reports.page("OUTBOUND", search(prefix+"OUT"));
        assertEquals(0, outboundPage.getSummary().getExecutedQuantity().compareTo(new BigDecimal("7")));
        var stock = reports.page("STOCK", byProduct(product));
        assertEquals(1, stock.getPage().getTotal());
        assertEquals(0, stock.getPage().getList().get(0).getAvailableQuantity().compareTo(new BigDecimal("2")));
        assertEquals(1L, stock.getSummary().getLowStockCount());
        assertEquals(3, reports.page("DEVICE", byProduct(deviceProduct)).getSummary().getDeviceCount());
        var inStockOnly = byProduct(deviceProduct); inStockOnly.setStatus("IN_STOCK");
        assertEquals(2, reports.page("DEVICE", inStockOnly).getSummary().getDeviceCount());
    }

    @Test void stocktakeIsPreliminaryAndStructuredDeviceAnomaliesRemainVisible() {
        var page = reports.page("STOCKTAKE", search(prefix+"ST"));
        assertEquals(2, page.getPage().getTotal());
        assertEquals(0, page.getSummary().getFinalDifferenceQuantity().signum());
        var deviceRow = page.getPage().getList().stream().filter(r -> "DEVICE".equals(r.getProductType())).findFirst().orElseThrow();
        assertEquals("MISSING", deviceRow.getDifferenceCode());
        assertEquals("SHORTAGE", deviceRow.getDifferenceDirection());
        assertEquals(1L, deviceRow.getDeviceExceptions());
        update("UPDATE inv_stocktakes SET status='CONFIRMED',confirmed_at=NOW(),confirmer_user_id=? WHERE id=?", user, stocktake);
        assertEquals(0, reports.page("STOCKTAKE", search(prefix+"ST")).getSummary().getFinalDifferenceQuantity().signum());
        assertEquals(new BigDecimal("-1.0000"), reports.page("STOCKTAKE", byProduct(deviceProduct)).getPage().getList().get(0).getFinalDifferenceQuantity());
    }

    @Test void serviceReportKeepsOldAndNewDevicesSeparateAndExposesWarrantyResult() {
        var all = reports.page("SERVICE", search(prefix));
        assertEquals(3, all.getPage().getTotal());
        assertEquals(3L, all.getSummary().getDocumentCount());
        var exchange = all.getPage().getList().stream().filter(r -> "EXCHANGE".equals(r.getServiceKind())).findFirst().orElseThrow();
        assertEquals(prefix+"D3", exchange.getDeviceCode());
        assertEquals(prefix+"D2", exchange.getNewDeviceCode());
        assertEquals("broken", exchange.getReason());
        var repair = all.getPage().getList().stream().filter(r -> "REPAIR".equals(r.getServiceKind())).findFirst().orElseThrow();
        assertEquals("careful", repair.getNotes());
        var warranty = reports.page("SERVICE", withKind("WARRANTY")).getPage().getList();
        assertEquals(1, warranty.size());
        assertEquals("EXPIRED", warranty.get(0).getWarrantyStatus());
        assertEquals("REJECTED", warranty.get(0).getDifferenceCode());
    }

    @Test void filtersPagingSpecialCharactersAndDatesAreBound() {
        var request = search(prefix);
        request.setSize(1);
        var first = reports.page("INBOUND", request).getPage();
        assertEquals(2, first.getTotal()); assertEquals(1, first.getList().size());
        request.setPage(2);
        assertEquals(1, reports.page("INBOUND", request).getPage().getList().size());
        request.setKeyword("%' OR 1=1 --");
        assertEquals(0, reports.page("INBOUND", request).getPage().getTotal());
        request.setKeyword(prefix);
        request.setFrom(LocalDate.now().plusDays(1));
        assertEquals(0, reports.page("INBOUND", request).getPage().getTotal());
        request.setFrom(LocalDate.now()); request.setTo(LocalDate.now());
        assertEquals(2, reports.page("INBOUND", request).getPage().getTotal());
        request.setStatus("APPROVED");
        assertEquals(0, reports.page("INBOUND", request).getPage().getTotal());
    }

    @Test void departmentAndSelfScopesApplyToPageSummaryAndExport() throws Exception {
        current("DEPARTMENT", true);
        assertEquals(0, reports.page("OUTBOUND", search(prefix)).getPage().getTotal());
        assertEquals(2, reports.page("DEVICE", byProduct(deviceProduct)).getPage().getTotal());
        assertEquals(0, reports.page("SERVICE", withKind("EXCHANGE")).getSummary().getRowCount());
        current("SELF", true);
        assertEquals(0, reports.page("STOCK", byProduct(product)).getPage().getTotal());
        assertEquals(0, reports.page("OUTBOUND", search(prefix)).getPage().getTotal());
        assertEquals(2, reports.page("DEVICE", byProduct(deviceProduct)).getPage().getTotal());
        assertEquals(1, reports.page("SERVICE", withKind("REPAIR")).getPage().getTotal());
        assertEquals(0, reports.page("SERVICE", withKind("EXCHANGE")).getPage().getTotal());
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reports.export("DEVICE", byProduct(deviceProduct))))) {
            assertEquals(3, book.getSheetAt(0).getPhysicalNumberOfRows());
        }
        currentOtherSelf();
        assertEquals(1, reports.page("DEVICE", byProduct(deviceProduct)).getPage().getTotal());
        assertEquals(1, reports.page("SERVICE", withKind("EXCHANGE")).getPage().getTotal());
    }

    @Test void generatedXlsxMatchesFilteredRowsAndUsesTextNumbersAndDates() throws Exception {
        var request = byProduct(product);
        var page = reports.page("STOCK", request);
        byte[] bytes = reports.export("STOCK", request);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheet("业务报表");
            assertNotNull(sheet);
            assertEquals(page.getPage().getTotal() + 1, sheet.getPhysicalNumberOfRows());
            assertEquals("产品编码", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("001"+prefix, sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(0).getCellType());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(6).getCellType());
            assertEquals(3.0, sheet.getRow(1).getCell(6).getNumericCellValue());
            assertNotNull(sheet.getPaneInformation()); assertNotNull(sheet.getCTWorksheet().getAutoFilter());
            assertEquals("A1:K2", sheet.getCTWorksheet().getAutoFilter().getRef());
        }
        var orders = search(prefix+"OUT");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reports.export("OUTBOUND", orders)))) {
            var sheet = book.getSheetAt(0);
            assertEquals(2, sheet.getPhysicalNumberOfRows());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(5).getCellType());
            assertTrue(sheet.getRow(1).getCell(5).getCellStyle().getDataFormatString().contains("yyyy"));
            assertEquals(7, sheet.getRow(1).getCell(8).getNumericCellValue());
        }
    }

    @Test void everyReportExportMatchesFilteredPageAndKeepsStableOrder() throws Exception {
        for (String type : List.of("STOCK", "INBOUND", "OUTBOUND", "REQUISITION", "DEVICE", "STOCKTAKE", "SERVICE")) {
            var request = search(type.equals("STOCK") ? null : prefix);
            if (type.equals("STOCK")) request.setProductId(product);
            var page = reports.page(type, request).getPage();
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(reports.export(type, request)))) {
                var sheet = book.getSheet("业务报表");
                assertEquals(page.getTotal() + 1, sheet.getPhysicalNumberOfRows(), type);
                assertEquals(page.getList().get(0).getNumber(), sheet.getRow(1).getCell(type.equals("STOCK") ? 0 : type.equals("DEVICE") ? 0 : type.equals("STOCKTAKE") ? 0 : type.equals("SERVICE") ? 1 : 0).getStringCellValue(), type);
            }
        }
        var onlyOnePage = search(prefix+"IN"); onlyOnePage.setSize(1);
        assertEquals(1, reports.page("INBOUND", onlyOnePage).getPage().getList().size());
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reports.export("INBOUND", onlyOnePage)))) {
            assertEquals(3, book.getSheetAt(0).getPhysicalNumberOfRows(), "Export must include all filtered rows, not only the UI page");
        }
    }

    @Test void permissionsAndValidationRejectUnsafeRequests() {
        CurrentUserContext.require().setPermissions(List.of("inventory.stock.view"));
        assertThrows(BusinessException.class, () -> reports.page("DEVICE", search(prefix)));
        assertThrows(BusinessException.class, () -> reports.export("SERVICE", withKind("REPAIR")));
        assertEquals(1, reports.page("STOCK", byProduct(product)).getPage().getTotal());
        CurrentUserContext.require().setPermissions(List.of("asset.warranty.view"));
        assertEquals(1, reports.page("SERVICE", withKind("WARRANTY")).getPage().getTotal());
        assertThrows(BusinessException.class, () -> reports.page("SERVICE", search(prefix)));
        current("ALL", true);
        var request = search(prefix); request.setSize(101);
        assertThrows(BusinessException.class, () -> reports.page("INBOUND", request));
        request.setSize(20); request.setKeyword("a".repeat(101));
        assertThrows(BusinessException.class, () -> reports.page("INBOUND", request));
        request.setKeyword(prefix); request.setWarehouseId(-1L);
        assertThrows(BusinessException.class, () -> reports.page("INBOUND", request));
        request.setWarehouseId(null); request.setServiceKind("BOGUS");
        assertThrows(BusinessException.class, () -> reports.page("SERVICE", request));
    }

    @Test void authenticatedHttpChecksPermissionAndReturnsChineseFilename() throws Exception {
        Long role = id("INSERT INTO sys_role(role_code,role_name,data_scope) VALUES(?,?,'DEPARTMENT')", prefix+"R", prefix+"R");
        update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code='inventory.stock.view'", role);
        update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, role);
        String token = UUID.randomUUID().toString();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        update("INSERT INTO sys_access_token(token_hash,user_id,expires_at) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 1 HOUR))", hash, user);
        mvc.perform(get("/api/reports/STOCK")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/DEVICE").header("Authorization", "Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/STOCK").header("Authorization", "Bearer "+token).param("productId", product.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(1));
        mvc.perform(get("/api/reports/STOCK/export").header("Authorization", "Bearer "+token).param("productId", product.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=")))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("spreadsheetml.sheet")));
        assertEquals(0L, count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=?", user));
    }

    private ReportRequest search(String keyword) { var request = new ReportRequest(); request.setKeyword(keyword); return request; }
    private ReportRequest byProduct(Long id) { var request = new ReportRequest(); request.setProductId(id); return request; }
    private ReportRequest withKind(String kind) { var request = search(prefix); request.setServiceKind(kind); return request; }
    private void current(String dataScope, boolean allPermissions) {
        var current = new CurrentUserView(); current.setUserId(user); current.setDataScope(dataScope);
        current.setPermissions(new ArrayList<>(allPermissions ? jdbc.queryForList("SELECT permission_code FROM sys_permission", String.class) : List.of()));
        var home = new DepartmentBriefView(); home.setId(department); current.setDepartment(home); CurrentUserContext.set(current);
    }
    private void currentOtherSelf() { current("SELF", true); CurrentUserContext.require().setUserId(otherUser);
        var home = new DepartmentBriefView(); home.setId(otherDepartment); CurrentUserContext.require().setDepartment(home); }
    private long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void update(String sql, Object... args) { jdbc.update(sql, args); sqlSession.clearCache(); }
    private Long id(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); return statement; }, key);
        sqlSession.clearCache(); return Objects.requireNonNull(key.getKey()).longValue();
    }
}
