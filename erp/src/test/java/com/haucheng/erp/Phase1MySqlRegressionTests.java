package com.haucheng.erp;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.dto.LoginRequest;
import com.haucheng.erp.entity.SysUser;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.UserMapper;
import com.haucheng.erp.service.AuthService;
import com.haucheng.erp.service.UserService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class Phase1MySqlRegressionTests {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserMapper mapper;
    @Autowired private UserService service;
    @Autowired private AuthService auth;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MockMvc mockMvc;
    private String prefix;
    private String password;
    private Long root;
    private Long child;
    private Long outside;
    private SysUser ownUser;
    private SysUser childUser;
    private SysUser outsideUser;

    @BeforeEach
    void setUp() {
        prefix = "REG_" + UUID.randomUUID().toString().replace("-", "");
        password = UUID.randomUUID().toString();
        root = department("ROOT", null);
        child = department("CHILD", root);
        outside = department("OUT", null);
        ownUser = user("OWN", root);
        childUser = user("CHILD", child);
        outsideUser = user("OUT", outside);
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void filtersPageAndCountWithoutExpandingDataScope() {
        for (Boolean enabled : new Boolean[]{null, true}) {
            current("ALL", root);
            assertPage(enabled, null, List.of(ownUser.getId(), childUser.getId(), outsideUser.getId()));
            assertPage(enabled, root, List.of(ownUser.getId()));
            assertPage(enabled, child, List.of(childUser.getId()));
            assertPage(enabled, outside, List.of(outsideUser.getId()));
            current("DEPARTMENT", root);
            assertPage(enabled, null, List.of(ownUser.getId(), childUser.getId()));
            assertPage(enabled, root, List.of(ownUser.getId()));
            assertPage(enabled, child, List.of(childUser.getId()));
            assertThrows(BusinessException.class, () -> service.getPage(prefix, enabled, outside, 1, 20));
            current("SELF", root);
            assertPage(enabled, null, List.of(ownUser.getId()));
            assertPage(enabled, root, List.of(ownUser.getId()));
            assertPage(enabled, child, List.of());
            assertPage(enabled, outside, List.of());
            var ownPage = service.getPage(null, enabled, root, 1, 20);
            assertEquals(1, ownPage.getTotal());
            assertEquals(List.of(ownUser.getId()), ownPage.getList().stream().map(x -> x.getId()).toList());
        }
        current("DEPARTMENT", null);
        assertPage(null, null, List.of());
        current("ALL", root);
        var page = service.getPage(prefix, true, null, 2, 1);
        assertEquals(3, page.getTotal());
        assertEquals(List.of(childUser.getId()), page.getList().stream().map(x -> x.getId()).toList());
    }

    @Test
    void persistsResponseRequestIdForEachHttpOperation() throws Exception {
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='SYSTEM_ADMIN'", ownUser.getId());
        LoginRequest login = new LoginRequest();
        login.setUsername(ownUser.getUsername());
        login.setPassword(password);
        String token = auth.login(login).getAccessToken();
        String previousId = null;
        for (String supplied : new String[]{prefix, "", " ", "x".repeat(65), "invalid/id", "x".repeat(64)}) {
            var request = post("/api/system/users/{id}/enable", childUser.getId())
                    .header("Authorization", "Bearer " + token);
            if (!supplied.isEmpty()) request.header("X-Request-Id", supplied);
            var response = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
            String requestId = response.getHeader("X-Request-Id");
            assertNotNull(requestId);
            assertTrue(requestId.matches("[A-Za-z0-9._-]{1,64}"));
            if (supplied.equals(prefix) || supplied.length() == 64) assertEquals(supplied, requestId);
            assertNotEquals(previousId, requestId);
            previousId = requestId;
            var log = jdbc.queryForMap("SELECT operator_user_id,operation_type,operation_target,request_id,detail FROM sys_operation_log WHERE request_id=?", requestId);
            assertEquals(ownUser.getId(), ((Number) log.get("operator_user_id")).longValue());
            assertEquals("USER_ENABLE", log.get("operation_type"));
            assertEquals(String.valueOf(childUser.getId()), log.get("operation_target"));
            assertEquals(requestId, log.get("request_id"));
            assertFalse(log.toString().contains(token));
            assertFalse(log.toString().contains(password));
            assertNull(CurrentUserContext.get());
        }
    }

    private void assertPage(Boolean enabled, Long departmentId, List<Long> expected) {
        var page = service.getPage(prefix, enabled, departmentId, 1, 20);
        assertEquals(expected.size(), page.getTotal());
        assertEquals(expected, page.getList().stream().map(x -> x.getId()).toList());
    }

    private void current(String scope, Long departmentId) {
        CurrentUserView current = new CurrentUserView();
        current.setUserId(ownUser.getId());
        current.setDataScope(scope);
        if (departmentId != null) {
            DepartmentBriefView department = new DepartmentBriefView();
            department.setId(departmentId);
            current.setDepartment(department);
        }
        CurrentUserContext.set(current);
    }

    private Long department(String suffix, Long parentId) {
        jdbc.update("INSERT INTO sys_department(department_code,department_name,parent_id) VALUES(?,?,?)", prefix + suffix, prefix + suffix, parentId);
        return jdbc.queryForObject("SELECT id FROM sys_department WHERE department_code=?", Long.class, prefix + suffix);
    }

    private SysUser user(String suffix, Long departmentId) {
        SysUser user = new SysUser();
        user.setUsername(prefix + suffix);
        user.setRealName(prefix + suffix);
        user.setPasswordHash(encoder.encode(password));
        user.setDepartmentId(departmentId);
        user.setStatus(true);
        mapper.insert(user);
        return user;
    }
}
