package com.haucheng.erp;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.dto.DeviceCreateRequest;
import com.haucheng.erp.dto.DeviceStatusRequest;
import com.haucheng.erp.dto.DeviceUpdateRequest;
import com.haucheng.erp.dto.ProductRequest;
import com.haucheng.erp.dto.LoginRequest;
import com.haucheng.erp.service.ProductService;
import com.haucheng.erp.vo.DeviceView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.mybatis.spring.SqlSessionTemplate;
import java.sql.Statement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.AuthMapper;
import com.haucheng.erp.mapper.UserMapper;
import com.haucheng.erp.service.AssetDeviceService;
import com.haucheng.erp.service.AuthService;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.DepartmentBriefView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "ERP_MYSQL_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "erp.auth.seed-username=", "erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class Phase3MySqlRegressionTests {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AssetDeviceService devices;
    @Autowired private AuthService auth;
    @Autowired private AuthMapper authMapper;
    @Autowired private UserMapper users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MockMvc mockMvc;
    @Autowired private ProductService products;
    @Autowired private ObjectMapper json;
    @Autowired private SqlSessionTemplate sqlSession;

    private String prefix;
    private Long category;
    private Long brand;
    private Long unit;
    private Long deviceProduct;
    private Long quantityProduct;
    private Long disabledProduct;
    private Long rootDepartment;
    private Long otherDepartment;
    private SysUser ownUser;
    private SysUser otherUser;

    @BeforeEach
    void setUp() {
        prefix = "P3_" + UUID.randomUUID().toString().replace("-", "");
        category = insertId("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)", prefix + "C", prefix + "C");
        unit = insertId("INSERT INTO md_units(unit_code,unit_name) VALUES(?,?)", prefix + "U", prefix + "U");
        brand = insertId("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)", prefix + "B", prefix + "B");
        deviceProduct = product(prefix + "D", "DEVICE", true, true, true);
        quantityProduct = product(prefix + "Q", "QUANTITY", false, false, false);
        disabledProduct = product(prefix + "X", "DEVICE", false, false, false);
        jdbc.update("UPDATE md_products SET status=0 WHERE id=?", disabledProduct);
        rootDepartment = insertId("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix + "R", prefix + "R");
        otherDepartment = insertId("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix + "O", prefix + "O");
        ownUser = user(prefix + "OWN", rootDepartment);
        otherUser = user(prefix + "OTHER", otherDepartment);
        current("ALL", rootDepartment, ownUser.getId());
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) cleanCommittedFixtures();
    }

    @Test
    void enforcesProductIdentityAndAssignmentRules() {
        DeviceCreateRequest valid = request("SN-001", "aa-bb-cc-dd-ee-01", "ASSET-001", rootDepartment, ownUser.getId(), ownUser.getId());
        var created = devices.create(valid);
        assertEquals("AA:BB:CC:DD:EE:01", created.getMacAddress());
        assertTrue(created.getDeviceCode().matches("DEV\\d{6,}"));
        DeviceCreateRequest duplicateCode = request("SN-CODE", "AA:BB:CC:DD:EE:15", null, rootDepartment, ownUser.getId(), ownUser.getId());
        duplicateCode.setDeviceCode(created.getDeviceCode());
        assertThrows(BusinessException.class, () -> devices.create(duplicateCode));

        DeviceCreateRequest quantity = request("Q-1", null, null, rootDepartment, ownUser.getId(), ownUser.getId()); quantity.setProductId(quantityProduct);
        assertThrows(BusinessException.class, () -> devices.create(quantity));
        DeviceCreateRequest missingSn = request(null, "AA:BB:CC:DD:EE:02", null, rootDepartment, ownUser.getId(), ownUser.getId());
        assertThrows(BusinessException.class, () -> devices.create(missingSn));
        DeviceCreateRequest missingMac = request("SN-002", null, null, rootDepartment, ownUser.getId(), ownUser.getId());
        assertThrows(BusinessException.class, () -> devices.create(missingMac));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-001", "AA:BB:CC:DD:EE:03", null, rootDepartment, ownUser.getId(), ownUser.getId())));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-003", "aabb.ccdd.ee01", null, rootDepartment, ownUser.getId(), ownUser.getId())));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-004", "AA:BB:CC:DD:EE:04", "ASSET-001", rootDepartment, ownUser.getId(), ownUser.getId())));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-005", "AA:BB:CC:DD:EE:05", null, rootDepartment, ownUser.getId(), otherUser.getId())));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-006", "AA:BB:CC:DD:EE:06", null, 999999L, ownUser.getId(), ownUser.getId())));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-007", "AA:BB:CC:DD:EE:07", null, rootDepartment, 999999L, ownUser.getId())));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-008", "AA:BB:CC:DD:EE:08", null, rootDepartment, ownUser.getId(), 999999L)));
        assertThrows(BusinessException.class, () -> devices.create(request("SN-009", "AA:BB:CC:DD:EE:09", null, rootDepartment, otherUser.getId(), ownUser.getId())));
        DeviceCreateRequest disabled = request("SN-010", "AA:BB:CC:DD:EE:10", null, rootDepartment, ownUser.getId(), ownUser.getId()); disabled.setProductId(disabledProduct);
        assertThrows(BusinessException.class, () -> devices.create(disabled));
        DeviceCreateRequest missingProduct = request("SN-011", "AA:BB:CC:DD:EE:11", null, rootDepartment, ownUser.getId(), ownUser.getId()); missingProduct.setProductId(999999L);
        assertThrows(BusinessException.class, () -> devices.create(missingProduct));
    }

    @Test
    void enforcesStatusHistoryFilteringAndDataScope() {
        var first = devices.create(request("SN-A", "AA:BB:CC:DD:EE:12", "ASSET-A", rootDepartment, ownUser.getId(), ownUser.getId()));
        var second = devices.create(request("SN-B", "AA:BB:CC:DD:EE:13", "ASSET-B", otherDepartment, otherUser.getId(), otherUser.getId()));
        DeviceStatusRequest inUse = new DeviceStatusRequest(); inUse.setStatus("IN_USE"); inUse.setReason("基础状态登记");
        devices.updateStatus(first.getId(), inUse);
        DeviceStatusRequest scrapped = new DeviceStatusRequest(); scrapped.setStatus("SCRAPPED"); scrapped.setReason("报废登记");
        devices.updateStatus(first.getId(), scrapped);
        DeviceStatusRequest illegal = new DeviceStatusRequest(); illegal.setStatus("IN_USE"); illegal.setReason("非法恢复");
        assertThrows(BusinessException.class, () -> devices.updateStatus(first.getId(), illegal));
        assertEquals(3, devices.getHistory(first.getId()).size());
        assertEquals(2, devices.getPage(null, null, null, null, null, 1, 1).getTotal());
        assertEquals(1, devices.getPage(null, "SCRAPPED", null, null, null, 1, 20).getTotal());
        assertEquals(1, devices.getPage("SN-B", null, null, null, null, 1, 20).getTotal());

        current("SELF", rootDepartment, ownUser.getId());
        assertEquals(List.of(first.getId()), devices.getPage(null, null, null, null, null, 1, 20).getList().stream().map(x -> x.getId()).toList());
        assertThrows(BusinessException.class, () -> devices.getById(second.getId()));
        current("DEPARTMENT", rootDepartment, ownUser.getId());
        assertEquals(List.of(first.getId()), devices.getPage(null, null, null, null, null, 1, 20).getList().stream().map(x -> x.getId()).toList());
    }

    @Test
    void persistsRequestIdAndRejectsMissingDevicePermission() throws Exception {
        String password = UUID.randomUUID().toString();
        SysUser admin = user(prefix + "ADMIN", rootDepartment);
        jdbc.update("UPDATE sys_user SET password_hash=? WHERE id=?", encoder.encode(password), admin.getId());
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='SYSTEM_ADMIN'", Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", admin.getId(), roleId);
        LoginRequest login = new LoginRequest(); login.setUsername(admin.getUsername()); login.setPassword(password);
        String token = auth.login(login).getAccessToken();
        String requestId = "phase3-" + UUID.randomUUID();
        String json = "{\"productId\":" + deviceProduct + ",\"serialNumber\":\"SN-HTTP\",\"macAddress\":\"AA-BB-CC-DD-EE-14\",\"departmentId\":" + rootDepartment + ",\"userId\":" + admin.getId() + ",\"custodianId\":" + admin.getId() + "}";
        mockMvc.perform(post("/api/assets/devices").header("Authorization", "Bearer " + token).header("X-Request-Id", requestId).contentType("application/json").content(json)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND operation_type='DEVICE_CREATE'", Integer.class, requestId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE request_id=? AND event_type='CREATE'", Integer.class, requestId));

        SysUser employee = user(prefix + "EMP", rootDepartment);
        String employeePassword = UUID.randomUUID().toString();
        jdbc.update("UPDATE sys_user SET password_hash=? WHERE id=?", encoder.encode(employeePassword), employee.getId());
        Long employeeRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='EMPLOYEE'", Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", employee.getId(), employeeRole);
        LoginRequest employeeLogin = new LoginRequest(); employeeLogin.setUsername(employee.getUsername()); employeeLogin.setPassword(employeePassword);
        String employeeToken = auth.login(employeeLogin).getAccessToken();
        mockMvc.perform(post("/api/assets/devices").header("Authorization", "Bearer " + employeeToken).contentType("application/json").content(json)).andExpect(status().isForbidden());
    }

    @Test
    void validatesDisabledReferencesDatesAndReservedLocations() {
        DeviceCreateRequest r = request("CHECK", "AA:BB:CC:DD:00:01", null, rootDepartment, ownUser.getId(), ownUser.getId());
        jdbc.update("UPDATE sys_department SET status=0 WHERE id=?", rootDepartment);
        assertEquals(40000, assertThrows(BusinessException.class, () -> devices.create(r)).getCode());
        jdbc.update("UPDATE sys_department SET status=1 WHERE id=?", rootDepartment);
        sqlSession.clearCache();
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", ownUser.getId());
        assertThrows(BusinessException.class, () -> devices.create(r));
        r.setUserId(null);
        assertThrows(BusinessException.class, () -> devices.create(r));
        jdbc.update("UPDATE sys_user SET status=1 WHERE id=?", ownUser.getId());
        sqlSession.clearCache();
        r.setDepartmentId(null);
        assertThrows(BusinessException.class, () -> devices.create(r));
        r.setDepartmentId(rootDepartment);
        r.setWarehouseId(999L);
        assertThrows(BusinessException.class, () -> devices.create(r));
        r.setWarehouseId(null);
        r.setLocationId(999L);
        assertThrows(BusinessException.class, () -> devices.create(r));
        r.setLocationId(null);
        r.setWarrantyStartAt(LocalDateTime.of(2026, 9, 10, 0, 0));
        r.setWarrantyEndAt(r.getWarrantyStartAt().minusDays(1));
        assertThrows(BusinessException.class, () -> devices.create(r));
        r.setWarrantyEndAt(r.getWarrantyStartAt().plusYears(1));
        r.setMacAddress("AA::BBCCDDEE01");
        assertThrows(BusinessException.class, () -> devices.create(r));
        r.setMacAddress("aabb.ccdd.ee01");
        assertEquals("AA:BB:CC:DD:EE:01", devices.create(r).getMacAddress());
    }

    @Test
    void permitsNullIdentifiersAndEnforcesRealUniqueKeysAndForeignKeys() {
        Long optional = product(prefix + "OPT", "DEVICE", false, false, false);
        DeviceCreateRequest r = new DeviceCreateRequest(); r.setProductId(optional);
        r.setSerialNumber("   "); r.setMacAddress(" "); r.setAssetNo("");
        var first = devices.create(r);
        var second = devices.create(r);
        assertNull(first.getSerialNumber()); assertNull(second.getMacAddress()); assertNull(first.getAssetNo());
        assertNotEquals(first.getDeviceCode(), second.getDeviceCode());
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO asset_devices(device_code,product_id) VALUES(?,?)", first.getDeviceCode(), optional));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO asset_devices(device_code,product_id) VALUES(?,?)", prefix + "BAD", Long.MAX_VALUE));
        var unique = devices.create(request("UNIQUE", "AA:BB:CC:DD:00:02", "ASSET", rootDepartment, ownUser.getId(), ownUser.getId()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE asset_devices SET serial_number=? WHERE id=?", unique.getSerialNumber(), first.getId()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE asset_devices SET mac_address=? WHERE id=?", unique.getMacAddress(), first.getId()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE asset_devices SET asset_no=? WHERE id=?", unique.getAssetNo(), first.getId()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE asset_devices SET department_id=? WHERE id=?", Long.MAX_VALUE, first.getId()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE asset_devices SET user_id=? WHERE id=?", Long.MAX_VALUE, first.getId()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE asset_devices SET custodian_id=? WHERE id=?", Long.MAX_VALUE, first.getId()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("DELETE FROM md_products WHERE id=?", optional));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO asset_device_history(device_id,event_type) VALUES(?,'CREATE')", Long.MAX_VALUE));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("DELETE FROM asset_devices WHERE id=?", unique.getId()));
    }

    @Test
    void protectsProductAttributesWithoutBreakingNormalUpdatesOrDisabledProductDevices() {
        var device = devices.create(request("PRODUCT", "AA:BB:CC:DD:00:03", null, rootDepartment, ownUser.getId(), ownUser.getId()));
        ProductRequest r = productRequest();
        r.setProductType("QUANTITY");
        ProductRequest type = r;
        assertThrows(BusinessException.class, () -> products.update(deviceProduct, type));
        r = productRequest(); r.setTrackSn(false); ProductRequest sn = r;
        assertThrows(BusinessException.class, () -> products.update(deviceProduct, sn));
        r = productRequest(); r.setTrackMac(false); ProductRequest mac = r;
        assertThrows(BusinessException.class, () -> products.update(deviceProduct, mac));
        r = productRequest(); r.setIsFixedAsset(false); ProductRequest asset = r;
        assertThrows(BusinessException.class, () -> products.update(deviceProduct, asset));
        r = productRequest(); r.setProductName(prefix + "Updated");
        assertEquals(r.getProductName(), products.update(deviceProduct, r).getProductName());
        products.updateStatus(deviceProduct, false);
        DeviceUpdateRequest update = updateRequest(device); update.setNotes("停用产品仍可维护设备");
        assertEquals(update.getNotes(), devices.update(device.getId(), update).getNotes());
        assertThrows(BusinessException.class, () -> devices.create(request("NEW", "AA:BB:CC:DD:00:04", null, null, null, null)));
    }

    @Test
    void recordsIdentityAssignmentAndDateHistoryAndLocksIdentityAfterLifecycleStarts() {
        var d = devices.create(request("EDIT", "AA:BB:CC:DD:00:05", null, rootDepartment, ownUser.getId(), ownUser.getId()));
        DeviceUpdateRequest r = updateRequest(d);
        r.setSerialNumber("CORRECTED");
        r.setInboundAt(LocalDateTime.of(2026, 9, 1, 8, 0));
        r.setDepartmentId(otherDepartment); r.setUserId(otherUser.getId()); r.setCustodianId(otherUser.getId());
        d = devices.update(d.getId(), r);
        assertEquals(4, d.getHistory().size());
        assertTrue(d.getHistory().stream().anyMatch(h -> h.getDetail().contains("EDIT -> CORRECTED")));
        assertTrue(d.getHistory().stream().anyMatch(h -> ownUser.getId().equals(h.getFromUserId()) && otherUser.getId().equals(h.getToUserId())));
        assertTrue(d.getHistory().stream().anyMatch(h -> h.getDetail().contains("2026-09-01T08:00")));
        r.setNotes("长期注意事项"); r.setRemark("基础备注");
        assertEquals(4, devices.update(d.getId(), r).getHistory().size());
        Long id = d.getId();
        changeStatus(id, "IN_USE");
        changeStatus(id, "IN_STOCK");
        r.setSerialNumber("REWRITE");
        assertThrows(BusinessException.class, () -> devices.update(id, r));
        assertFalse(devices.getById(id).getIdentityEditable());
    }

    @Test
    void reservesFutureStatusesAndMakesScrappingTerminal() {
        var d = devices.create(request("STATE", "AA:BB:CC:DD:00:06", null, null, null, null));
        assertThrows(BusinessException.class, () -> changeStatus(d.getId(), "IN_USE"));
        for (String target : List.of("BORROWED", "REPAIRING", "PENDING_EXCHANGE", "SHIPPED", "IN_STOCK")) {
            assertThrows(BusinessException.class, () -> changeStatus(d.getId(), target));
        }
        changeStatus(d.getId(), "SCRAPPED");
        for (String target : List.of("IN_STOCK", "IN_USE", "BORROWED", "REPAIRING", "PENDING_EXCHANGE", "SHIPPED", "SCRAPPED")) {
            assertThrows(BusinessException.class, () -> changeStatus(d.getId(), target));
        }
        DeviceUpdateRequest r = updateRequest(d); r.setDepartmentId(rootDepartment);
        assertThrows(BusinessException.class, () -> devices.update(d.getId(), r));
    }

    @Test
    void intersectsAllFiltersWithScopeAndProtectsEveryWriteAndHistory() {
        var own = devices.create(request("OWN", "AA:BB:CC:DD:00:07", null, rootDepartment, ownUser.getId(), null));
        var custody = devices.create(request("CUSTODY", "AA:BB:CC:DD:00:08", null, rootDepartment, null, ownUser.getId()));
        var outside = devices.create(request("OUTSIDE", "AA:BB:CC:DD:00:09", null, otherDepartment, otherUser.getId(), otherUser.getId()));
        Long child = insertId("INSERT INTO sys_department(department_code,department_name,parent_id) VALUES(?,?,?)", prefix + "CH", prefix + "CH", rootDepartment);
        devices.create(request("CHILD", "AA:BB:CC:DD:00:10", null, child, null, null));
        current("SELF", rootDepartment, ownUser.getId());
        assertEquals(2, devices.getPage(null, null, deviceProduct, null, null, 1, 20).getTotal());
        assertEquals(0, devices.getPage(null, null, null, otherDepartment, null, 1, 20).getTotal());
        assertEquals(0, devices.getPage(null, null, null, null, otherUser.getId(), 1, 20).getTotal());
        assertEquals(custody.getId(), devices.getById(custody.getId()).getId());
        assertThrows(BusinessException.class, () -> devices.getHistory(outside.getId()));
        assertThrows(BusinessException.class, () -> devices.update(own.getId(), updateRequest(own)));
        assertThrows(BusinessException.class, () -> changeStatus(own.getId(), "SCRAPPED"));
        assertThrows(BusinessException.class, () -> devices.create(request("SELFNEW", "AA:BB:CC:DD:00:11", null, rootDepartment, ownUser.getId(), null)));
        current("DEPARTMENT", rootDepartment, ownUser.getId());
        assertEquals(3, devices.getPage(null, null, null, null, null, 1, 20).getTotal());
        assertEquals(1, devices.getPage(null, null, null, child, null, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> devices.getPage(null, null, null, otherDepartment, null, 1, 20));
        assertThrows(BusinessException.class, () -> devices.update(outside.getId(), updateRequest(outside)));
        DeviceUpdateRequest move = updateRequest(own); move.setDepartmentId(otherDepartment); move.setUserId(otherUser.getId());
        assertThrows(BusinessException.class, () -> devices.update(own.getId(), move));
        current("DEPARTMENT", null, ownUser.getId());
        assertEquals(0, devices.getPage(null, null, null, null, null, 1, 20).getTotal());
    }

    @Test
    void verifiesPagingKeywordAndProductFiltersAgainstActualRows() {
        jdbc.update("UPDATE md_products SET model='ABC-24',specification='24 ports' WHERE id=?", deviceProduct);
        var a = devices.create(request("SEARCH-A", "AA:BB:CC:DD:00:12", "ASSET-A", rootDepartment, ownUser.getId(), ownUser.getId()));
        var b = devices.create(request("SEARCH-B", "AA:BB:CC:DD:00:13", "ASSET-B", otherDepartment, otherUser.getId(), otherUser.getId()));
        var page = devices.getPage(null, null, deviceProduct, null, null, 2, 1);
        assertEquals(2, page.getTotal()); assertEquals(1, page.getSize()); assertEquals(2, page.getPage());
        assertEquals(a.getId(), page.getList().get(0).getId());
        for (String keyword : List.of(a.getDeviceCode(), a.getSerialNumber(), "aabb.ccdd.0012", a.getAssetNo(), ownUser.getRealName(), prefix + "R")) {
            assertEquals(List.of(a.getId()), devices.getPage(keyword, null, null, null, null, 1, 20).getList().stream().map(DeviceView::getId).toList());
        }
        assertEquals(2, devices.getPage("ABC-24", null, null, null, null, 1, 20).getTotal());
        assertEquals(2, devices.getPage(prefix + "D", null, null, null, null, 1, 20).getTotal());
        assertEquals(0, devices.getPage(null, null, quantityProduct, null, null, 1, 20).getTotal());
        assertEquals(b.getId(), devices.getPage(null, null, null, otherDepartment, otherUser.getId(), 1, 20).getList().get(0).getId());
        assertEquals("24 ports", devices.getById(a.getId()).getSpecification());
        assertEquals(100, devices.getPage(null, null, null, null, null, 0, 1000).getSize());
    }

    @Test
    void optionsWorkWithOnlyDevicePermissionAndExposeOnlyNecessaryUserFields() throws Exception {
        String token = token(ownUser, "WAREHOUSE_ADMIN");
        mockMvc.perform(get("/api/system/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assets/devices/options").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].productType").value("DEVICE"))
                .andExpect(jsonPath("$.data[0].trackSn").value(true));
        mockMvc.perform(get("/api/assets/devices/options/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        for (int i = 0; i < 105; i++) {
            jdbc.update("INSERT INTO sys_user(username,real_name,password_hash,department_id) VALUES(?,?,?,?)",
                    prefix + "OPT" + i, prefix + "OPT" + i, "unused", rootDepartment);
        }
        var response = mockMvc.perform(get("/api/assets/devices/options/users").param("keyword", prefix + "OPT104")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].permissions").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].passwordHash").doesNotExist()).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("unused"));
        mockMvc.perform(get("/api/assets/devices/options/users").param("departmentId", otherDepartment.toString())
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assets/devices")).andExpect(status().isUnauthorized());
    }

    @Test
    void checksHttpUpdateStatusAuditAndReadOnlyIdentityBoundaries() throws Exception {
        var d = devices.create(request("HTTP", "AA:BB:CC:DD:00:14", null, rootDepartment, ownUser.getId(), ownUser.getId()));
        String token = token(ownUser, "SYSTEM_ADMIN");
        DeviceUpdateRequest update = updateRequest(d); update.setSerialNumber("HTTP-FIX");
        var node = json.valueToTree(update);
        ((tools.jackson.databind.node.ObjectNode) node).put("status", "SHIPPED").put("productId", quantityProduct);
        String updateId = "p3-update-" + UUID.randomUUID();
        mockMvc.perform(put("/api/assets/devices/{id}", d.getId()).header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", updateId).contentType("application/json").content(json.writeValueAsString(node)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.productId").value(deviceProduct));
        var response = mockMvc.perform(patch("/api/assets/devices/{id}/status", d.getId())
                        .header("Authorization", "Bearer " + token).header("X-Request-Id", "invalid/id")
                        .contentType("application/json").content("{\"status\":\"IN_USE\",\"reason\":\"基础状态登记\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String statusId = response.getHeader("X-Request-Id");
        assertTrue(statusId.matches("[A-Za-z0-9._-]{1,64}"));
        for (String id : List.of(updateId, statusId)) {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=?", Integer.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM asset_device_history WHERE request_id=?", Integer.class, id));
            assertFalse(jdbc.queryForObject("SELECT detail FROM sys_operation_log WHERE request_id=?", String.class, id).contains(token));
        }
        mockMvc.perform(patch("/api/assets/devices/{id}/status", d.getId()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"status\":\"SHIPPED\",\"reason\":\"非法转换\"}")).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/assets/devices/{id}/status", d.getId()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"status\":\"SCRAPPED\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/assets/devices").param("page", "invalid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        String employee = token(otherUser, "EMPLOYEE");
        for (String path : List.of("/api/assets/devices/" + d.getId(), "/api/assets/devices/" + d.getId() + "/history")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + employee)).andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/assets/devices/{id}", d.getId()).header("Authorization", "Bearer " + employee)
                .contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/assets/devices/{id}/status", d.getId()).header("Authorization", "Bearer " + employee)
                .contentType("application/json").content("{\"status\":\"SCRAPPED\",\"reason\":\"越权\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assets/devices").header("Authorization", "Bearer " + employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        SysUser noRole = user(prefix + "NOROLE", rootDepartment);
        mockMvc.perform(get("/api/assets/devices").header("Authorization", "Bearer " + token(noRole, null))).andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentNumberAllocationAndDuplicateIdentityHaveDatabaseProtection() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<DeviceView>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int index = i;
                tasks.add(() -> {
                    current("ALL", rootDepartment, ownUser.getId());
                    try {
                        return devices.create(request(prefix + index, String.format("02:00:00:00:10:%02X", index), null,
                                rootDepartment, ownUser.getId(), ownUser.getId()));
                    } finally { CurrentUserContext.clear(); }
                });
            }
            var futures = executor.invokeAll(tasks);
            Set<String> codes = new java.util.HashSet<>();
            for (var future : futures) codes.add(future.get(20, TimeUnit.SECONDS).getDeviceCode());
            assertEquals(8, codes.size());
            AtomicInteger accepted = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> duplicates = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                duplicates.add(executor.submit(() -> {
                    current("ALL", rootDepartment, ownUser.getId());
                    try {
                        ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                        devices.create(request(prefix + "RACE", "02:00:00:00:20:01", prefix + "ASSET",
                                rootDepartment, ownUser.getId(), ownUser.getId()));
                        accepted.incrementAndGet();
                    } catch (BusinessException e) { assertEquals(40900, e.getCode()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    finally { CurrentUserContext.clear(); }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (var future : duplicates) future.get(20, TimeUnit.SECONDS);
            assertEquals(1, accepted.get());
        } finally { executor.shutdownNow(); }
    }

    private void cleanCommittedFixtures() {
        jdbc.update("DELETE h FROM asset_device_history h JOIN asset_devices d ON d.id=h.device_id WHERE d.product_id=?", deviceProduct);
        jdbc.update("DELETE FROM asset_devices WHERE product_id=?", deviceProduct);
        jdbc.update("DELETE FROM sys_operation_log WHERE operator_user_id IN (?,?)", ownUser.getId(), otherUser.getId());
        jdbc.update("DELETE FROM sys_user WHERE id IN (?,?)", ownUser.getId(), otherUser.getId());
        jdbc.update("DELETE FROM sys_department WHERE id IN (?,?)", rootDepartment, otherDepartment);
        jdbc.update("DELETE FROM md_products WHERE id IN (?,?,?)", deviceProduct, quantityProduct, disabledProduct);
        jdbc.update("DELETE FROM md_categories WHERE id=?", category);
        jdbc.update("DELETE FROM md_units WHERE id=?", unit);
        jdbc.update("DELETE FROM md_brands WHERE id=?", brand);
    }

    private void changeStatus(Long id, String status) {
        DeviceStatusRequest r = new DeviceStatusRequest(); r.setStatus(status); r.setReason("基础状态登记");
        devices.updateStatus(id, r);
    }

    private String token(SysUser user, String role) {
        String password = UUID.randomUUID().toString();
        jdbc.update("UPDATE sys_user SET password_hash=? WHERE id=?", encoder.encode(password), user.getId());
        if (role != null) jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?", user.getId(), role);
        LoginRequest login = new LoginRequest(); login.setUsername(user.getUsername()); login.setPassword(password);
        return auth.login(login).getAccessToken();
    }

    private ProductRequest productRequest() {
        ProductRequest r = new ProductRequest();
        r.setProductCode(prefix + "D"); r.setProductName(prefix + "D");
        r.setCategoryId(category); r.setUnitId(unit); r.setBrandId(brand); r.setProductType("DEVICE");
        r.setTrackSn(true); r.setTrackMac(true); r.setIsFixedAsset(true);
        return r;
    }

    private DeviceUpdateRequest updateRequest(DeviceView d) {
        DeviceUpdateRequest r = new DeviceUpdateRequest();
        r.setSerialNumber(d.getSerialNumber()); r.setMacAddress(d.getMacAddress()); r.setAssetNo(d.getAssetNo());
        r.setDepartmentId(d.getDepartmentId()); r.setUserId(d.getUserId()); r.setCustodianId(d.getCustodianId());
        r.setPurchaseAt(d.getPurchaseAt()); r.setInboundAt(d.getInboundAt()); r.setFirstUseAt(d.getFirstUseAt());
        r.setWarrantyStartAt(d.getWarrantyStartAt()); r.setWarrantyEndAt(d.getWarrantyEndAt());
        r.setRemark(d.getRemark()); r.setNotes(d.getNotes()); return r;
    }

    private DeviceCreateRequest request(String serial, String mac, String assetNo, Long departmentId, Long userId, Long custodianId) {
        DeviceCreateRequest request = new DeviceCreateRequest(); request.setProductId(deviceProduct); request.setSerialNumber(serial); request.setMacAddress(mac); request.setAssetNo(assetNo); request.setDepartmentId(departmentId); request.setUserId(userId); request.setCustodianId(custodianId); return request;
    }

    private Long product(String code, String type, boolean trackSn, boolean trackMac, boolean fixedAsset) {
        jdbc.update("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type,track_sn,track_mac,is_fixed_asset,max_stock) VALUES(?,?,?,?,?,?,?,?,?,10)", code, code, category, brand, unit, type, trackSn, trackMac, fixedAsset);
        return jdbc.queryForObject("SELECT id FROM md_products WHERE product_code=?", Long.class, code);
    }

    private Long insertId(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private SysUser user(String username, Long departmentId) {
        SysUser user = new SysUser(); user.setUsername(username); user.setRealName(username); user.setPasswordHash(encoder.encode(UUID.randomUUID().toString())); user.setDepartmentId(departmentId); user.setStatus(true); users.insert(user); return user;
    }

    private void current(String scope, Long departmentId, Long userId) {
        CurrentUserView current = new CurrentUserView(); current.setUserId(userId); current.setDataScope(scope);
        if (departmentId != null) {
            DepartmentBriefView department = new DepartmentBriefView(); department.setId(departmentId); current.setDepartment(department);
        }
        CurrentUserContext.set(current);
    }
}
