package com.haucheng.erp;

import com.haucheng.erp.dto.*;
import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.AuthMapper;
import com.haucheng.erp.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "erp.auth.seed-username=", "erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class Phase2MySqlRegressionTests {
    @Autowired private CategoryService categories;
    @Autowired private UnitService units;
    @Autowired private BrandService brands;
    @Autowired private ProductService products;
    @Autowired private AuthService auth;
    @Autowired private AuthMapper authMapper;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;

    @AfterEach void clearContext() { CurrentUserContext.clear(); }

    @Test
    void createsQuantityAndDeviceProductsAndEnforcesMasterDataGuards() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        CategoryRequest category = new CategoryRequest(); category.setCategoryCode("C" + suffix); category.setCategoryName("分类" + suffix);
        var root = categories.create(category);
        CategoryRequest child = new CategoryRequest(); child.setCategoryCode("CC" + suffix); child.setCategoryName("子分类" + suffix); child.setParentId(root.getId());
        var childView = categories.create(child);
        child.setParentId(childView.getId());
        assertThrows(BusinessException.class, () -> categories.update(root.getId(), child));

        UnitRequest unit = new UnitRequest(); unit.setUnitCode("U" + suffix); unit.setUnitName("单位" + suffix); unit.setPrecisionScale(2);
        var unitEntity = units.create(unit);
        BrandRequest brand = new BrandRequest(); brand.setBrandCode("B" + suffix); brand.setBrandName("品牌" + suffix);
        var brandEntity = brands.create(brand);

        ProductRequest quantity = product("P-Q" + suffix, "数量物资" + suffix, root.getId(), brandEntity.getId(), unitEntity.getId(), "QUANTITY");
        ProductRequest device = product("P-D" + suffix, "设备物资" + suffix, root.getId(), brandEntity.getId(), unitEntity.getId(), "DEVICE");
        var quantityView = products.create(quantity);
        assertEquals("QUANTITY", quantityView.getProductType());
        quantity.setProductName("数量物资更新" + suffix);
        assertEquals("数量物资更新" + suffix, products.update(quantityView.getId(), quantity).getProductName());
        products.updateStatus(quantityView.getId(), false);
        assertFalse(products.getById(quantityView.getId()).getStatus());
        assertEquals("DEVICE", products.create(device).getProductType());
        assertEquals(2, products.getPage(null, null, null, null, null, null, null, 1, 20).getTotal());

        categories.updateStatus(root.getId(), false);
        assertThrows(BusinessException.class, () -> products.create(product("P-X" + suffix, "禁用分类" + suffix, root.getId(), brandEntity.getId(), unitEntity.getId(), "QUANTITY")));
        categories.updateStatus(root.getId(), true);
        units.updateStatus(unitEntity.getId(), false);
        assertThrows(BusinessException.class, () -> products.create(product("P-Y" + suffix, "禁用单位" + suffix, root.getId(), brandEntity.getId(), unitEntity.getId(), "QUANTITY")));
        units.updateStatus(unitEntity.getId(), true);
        brands.updateStatus(brandEntity.getId(), false);
        assertThrows(BusinessException.class, () -> products.create(product("P-Z" + suffix, "禁用品牌" + suffix, root.getId(), brandEntity.getId(), unitEntity.getId(), "QUANTITY")));
        brands.updateStatus(brandEntity.getId(), true);

        assertThrows(BusinessException.class, () -> categories.delete(root.getId()));
        assertThrows(BusinessException.class, () -> units.delete(unitEntity.getId()));
        assertThrows(BusinessException.class, () -> brands.delete(brandEntity.getId()));
        assertThrows(BusinessException.class, () -> products.create(quantity));
    }

    @Test
    void rejectsRequestsWithoutMasterPermissionAndPersistsRequestIdAudit() throws Exception {
        String username = "p2_" + UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        SysUser user = new SysUser(); user.setUsername(username); user.setRealName(username); user.setPasswordHash(encoder.encode(password)); user.setStatus(true); authMapper.insert(user);
        Long employeeRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='EMPLOYEE'", Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user.getId(), employeeRole);
        LoginRequest login = new LoginRequest(); login.setUsername(username); login.setPassword(password);
        String token = auth.login(login).getAccessToken();
        mockMvc.perform(get("/api/master-data/products").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());

        String admin = "p2_admin_" + UUID.randomUUID().toString().replace("-", "");
        String adminPassword = UUID.randomUUID().toString();
        auth.seedAdmin(admin, adminPassword, admin, null);
        LoginRequest adminLogin = new LoginRequest(); adminLogin.setUsername(admin); adminLogin.setPassword(adminPassword);
        String adminToken = auth.login(adminLogin).getAccessToken();
        String requestId = "phase2-" + UUID.randomUUID();
        String body = "{\"categoryCode\":\"AUD_" + UUID.randomUUID().toString().replace("-", "") + "\",\"categoryName\":\"审计分类\"}";
        mockMvc.perform(post("/api/master-data/categories").header("Authorization", "Bearer " + adminToken).header("X-Request-Id", requestId).contentType("application/json").content(body)).andExpect(status().isOk());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND operation_type='CATEGORY_CREATE'", Integer.class, requestId);
        assertEquals(1, count);
        assertTrue(jdbc.queryForObject("SELECT detail FROM sys_operation_log WHERE request_id=?", String.class, requestId).contains("分类"));
    }

    private ProductRequest product(String code, String name, Long categoryId, Long brandId, Long unitId, String type) {
        ProductRequest r = new ProductRequest(); r.setProductCode(code); r.setProductName(name); r.setCategoryId(categoryId); r.setBrandId(brandId); r.setUnitId(unitId); r.setProductType(type);
        r.setSafetyStock(BigDecimal.ZERO); r.setMinStock(BigDecimal.ZERO); r.setMaxStock(BigDecimal.TEN); return r;
    }
}
