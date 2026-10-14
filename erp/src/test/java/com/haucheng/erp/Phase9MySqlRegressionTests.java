package com.haucheng.erp;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.dto.LoginRequest;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.mapper.OperationLogMapper;
import com.haucheng.erp.service.AuthService;
import com.haucheng.erp.service.QrService;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.DepartmentBriefView;
import com.haucheng.erp.vo.QrLabelView;
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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
class Phase9MySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired QrService qr;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean OperationLogMapper audit;
    private String prefix;
    private Long category, brand, unit, product, quantityProduct, device, oldDevice, department, otherDepartment, user, warehouse;

    @BeforeEach void setup() {
        prefix="P9_"+UUID.randomUUID().toString().replace("-","");
        category=id("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)",prefix,prefix);
        brand=id("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)",prefix,prefix);
        unit=id("INSERT INTO md_units(unit_code,unit_name) VALUES(?,?)",prefix,prefix);
        product=id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type,enable_qrcode) VALUES(?,?,?,?,?,'DEVICE',1)",prefix+"D",prefix+"D",category,brand,unit);
        quantityProduct=id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type,enable_qrcode) VALUES(?,?,?,?,?,'QUANTITY',1)",prefix+"Q",prefix+"Q",category,brand,unit);
        department=id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)",prefix,prefix);
        otherDepartment=id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)",prefix+"O",prefix+"O");
        user=id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)",prefix,"unused-test-hash",prefix,department);
        warehouse=id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)",prefix,prefix,department);
        device=newDevice("IN_USE",department,user,null);
        oldDevice=newDevice("SHIPPED",department,null,null);
        current("ALL", department, true);
    }

    @AfterEach void cleanup() {
        reset(audit);
        CurrentUserContext.clear();
        jdbc.update("DELETE FROM sys_qr_tokens WHERE product_id IN (?,?) OR device_id IN (?,?)",product,quantityProduct,device,oldDevice);
        jdbc.update("DELETE FROM sys_operation_log WHERE operator_user_id=?",user);
        jdbc.update("DELETE FROM sys_access_token WHERE user_id=?",user);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",user);
        jdbc.update("DELETE FROM inv_stocks WHERE product_id IN (?,?)",product,quantityProduct);
        jdbc.update("DELETE FROM asset_devices WHERE id IN (?,?)",device,oldDevice);
        jdbc.update("DELETE FROM wh_warehouses WHERE id=?",warehouse);
        jdbc.update("DELETE FROM sys_user WHERE id=?",user);
        jdbc.update("DELETE FROM sys_department WHERE id IN (?,?)",department,otherDepartment);
        jdbc.update("DELETE FROM md_products WHERE id IN (?,?)",product,quantityProduct);
        jdbc.update("DELETE FROM md_categories WHERE id=?",category);
        jdbc.update("DELETE FROM md_brands WHERE id=?",brand);
        jdbc.update("DELETE FROM md_units WHERE id=?",unit);
        assertEquals(0,count("SELECT COUNT(*) FROM sys_qr_tokens WHERE token IN (SELECT token FROM sys_qr_tokens WHERE product_id=? OR device_id=?)",product,device));
        assertEquals(0,count("SELECT COUNT(*) FROM md_products WHERE product_code LIKE ?",prefix+"%"));
        assertEquals(0,count("SELECT COUNT(*) FROM asset_devices WHERE device_code LIKE ?",prefix+"%"));
    }

    @Test void productAndDeviceHaveStableDistinctDecodableQr() throws Exception {
        QrLabelView p=qr.label("PRODUCT",product,true), d=qr.label("DEVICE",device,true);
        assertEquals(p.getContent(),qr.label("PRODUCT",product,true).getContent());
        assertEquals(d.getContent(),qr.label("DEVICE",device,true).getContent());
        assertNotEquals(p.getContent(),d.getContent());
        assertEquals(p.getContent(),decode(p.getSvg()));
        assertEquals(d.getContent(),decode(d.getSvg()));
        assertEquals(product,qr.scan(decode(p.getSvg())).getProduct().getId());
        assertEquals(device,qr.scan(decode(d.getSvg())).getDevice().getId());
        assertTrue(p.getSvg().contains("<svg"));
        assertEquals(2,count("SELECT COUNT(*) FROM sys_qr_tokens WHERE product_id=? OR device_id=?",product,device));
        assertEquals(2,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND operation_type='QR_GENERATE'",user));
    }

    @Test void historicalDevicesKeepIndependentPointers() {
        QrLabelView old=qr.label("DEVICE",oldDevice,true), next=qr.label("DEVICE",device,true);
        assertNotEquals(old.getContent(),next.getContent());
        assertEquals(oldDevice,qr.scan(old.getContent()).getDevice().getId());
        assertEquals("SHIPPED",qr.scan(old.getContent()).getDevice().getStatus());
        jdbc.update("UPDATE asset_devices SET status='SCRAPPED' WHERE id=?",oldDevice);
        assertEquals(oldDevice,qr.scan(old.getContent()).getDevice().getId());
        assertEquals("SCRAPPED",qr.scan(old.getContent()).getDevice().getStatus());
    }

    @Test void badContentUnknownTokenAndDisabledTokenAreRejected() {
        for(String input:List.of("", "ERPQR2:P:"+"A".repeat(43), "ERPQR1:P:bad", "x".repeat(257))) {
            assertThrows(BusinessException.class,()->qr.scan(input));
        }
        assertThrows(BusinessException.class,()->qr.scan("ERPQR1:D:"+"A".repeat(43)));
        QrLabelView label=qr.label("PRODUCT",product,true);
        assertThrows(BusinessException.class,()->qr.scan(label.getContent().replace(":P:",":D:")));
        jdbc.update("UPDATE sys_qr_tokens SET active=0 WHERE product_id=?",product);
        assertThrows(BusinessException.class,()->qr.scan(label.getContent()));
        assertThrows(BusinessException.class,()->qr.label("PRODUCT",product,false));
    }

    @Test void disabledProductCannotResolveButHistoricDeviceCan() {
        String content=qr.label("PRODUCT",product,true).getContent();
        jdbc.update("UPDATE md_products SET status=0 WHERE id=?",product);
        assertThrows(BusinessException.class,()->qr.scan(content));
        assertThrows(BusinessException.class,()->qr.label("PRODUCT",product,true));
        assertEquals(device,qr.scan(qr.label("DEVICE",device,true).getContent()).getDevice().getId());
    }

    @Test void productQrSettingControlsFirstIssueWithoutInvalidatingPrintedLabels() {
        jdbc.update("UPDATE md_products SET enable_qrcode=0 WHERE id=?",product);
        assertThrows(BusinessException.class,()->qr.label("PRODUCT",product,true));
        jdbc.update("UPDATE md_products SET enable_qrcode=1 WHERE id=?",product);
        String content=qr.label("PRODUCT",product,true).getContent();
        jdbc.update("UPDATE md_products SET enable_qrcode=0 WHERE id=?",product);
        assertEquals(content,qr.label("PRODUCT",product,true).getContent());
        assertEquals(product,qr.scan(content).getProduct().getId());
    }

    @Test void printViewHasSvgAndPrintIsolation() throws Exception {
        String page=Files.readString(Path.of("../erp-vue/src/views/asset/QrView.vue"));
        assertTrue(page.contains("@media print"));
        assertTrue(page.contains(".app-aside, .app-header, .screen-only { display: none !important; }"));
        assertTrue(page.contains("break-inside: avoid"));
        assertTrue(page.contains("window.print()"));
        assertTrue(page.contains("data:image/svg+xml"));
    }

    @Test void permissionsAndScopeAreRequiredAgainDuringScan() {
        QrLabelView label=qr.label("DEVICE",oldDevice,true);
        current("ALL",department,true);
        CurrentUserContext.require().getPermissions().remove("asset.qr.view");
        assertThrows(BusinessException.class,()->qr.scan(label.getContent()));
        assertThrows(BusinessException.class,()->qr.label("DEVICE",device,true));
        assertThrows(BusinessException.class,()->qr.batch("DEVICE",List.of(device)));
        current("SELF",department,true);
        assertThrows(BusinessException.class,()->qr.scan(label.getContent()));
        current("DEPARTMENT",otherDepartment,true);
        assertThrows(BusinessException.class,()->qr.label("DEVICE",oldDevice,true));
        current("ALL",department,false);
        assertThrows(BusinessException.class,()->qr.scan(label.getContent()));
        assertThrows(BusinessException.class,()->qr.label("DEVICE",device,true));
    }

    @Test void productStockIsReturnedOnlyWithStockPermissionAndScope() {
        jdbc.update("INSERT INTO inv_stocks(warehouse_id,product_id,quantity) VALUES(?,?,3)",warehouse,quantityProduct);
        String content=qr.label("PRODUCT",quantityProduct,true).getContent();
        assertEquals(1,qr.scan(content).getStocks().getTotal());
        current("SELF",department,true);
        assertEquals(0,qr.scan(content).getStocks().getTotal());
        CurrentUserContext.require().getPermissions().remove("inventory.stock.view");
        assertNull(qr.scan(content).getStocks());
    }

    @Test void batchBoundsAndReadableLabels() {
        assertThrows(BusinessException.class,()->qr.batch("DEVICE",List.of()));
        assertThrows(BusinessException.class,()->qr.batch("DEVICE",List.of(device,device)));
        assertThrows(BusinessException.class,()->qr.batch("DEVICE",Collections.nCopies(31,device)));
        QrLabelView label=qr.batch("DEVICE",List.of(device)).get(0);
        assertTrue(label.getCode().startsWith(prefix+"DEVICE"));
        assertEquals(prefix+"D",label.getName());
        assertEquals(1,count("SELECT COUNT(*) FROM sys_qr_tokens WHERE device_id=?",device));
    }

    @Test void failedBatchRollsBackEarlierTokenAndAudit() {
        assertThrows(BusinessException.class,()->qr.batch("DEVICE",List.of(device,Long.MAX_VALUE)));
        assertEquals(0,count("SELECT COUNT(*) FROM sys_qr_tokens WHERE device_id=?",device));
        assertEquals(0,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND operation_type='QR_GENERATE'",user));
    }

    @Test void qrOperationsNeverChangeInventoryOrDeviceHistory() {
        jdbc.update("INSERT INTO inv_stocks(warehouse_id,product_id,quantity) VALUES(?,?,3)",warehouse,quantityProduct);
        int stockBefore=count("SELECT quantity FROM inv_stocks WHERE warehouse_id=? AND product_id=?",warehouse,quantityProduct);
        int ledgersBefore=count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE product_id IN (?,?)",product,quantityProduct);
        int historyBefore=count("SELECT COUNT(*) FROM asset_device_history WHERE device_id IN (?,?)",device,oldDevice);
        String statusBefore=jdbc.queryForObject("SELECT status FROM asset_devices WHERE id=?",String.class,device);
        String content=qr.label("DEVICE",device,true).getContent();
        qr.scan(content);
        qr.batch("DEVICE",List.of(device,oldDevice));
        assertEquals(stockBefore,count("SELECT quantity FROM inv_stocks WHERE warehouse_id=? AND product_id=?",warehouse,quantityProduct));
        assertEquals(ledgersBefore,count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE product_id IN (?,?)",product,quantityProduct));
        assertEquals(historyBefore,count("SELECT COUNT(*) FROM asset_device_history WHERE device_id IN (?,?)",device,oldDevice));
        assertEquals(statusBefore,jdbc.queryForObject("SELECT status FROM asset_devices WHERE id=?",String.class,device));
    }

    @Test void databaseConstraintsAndRollbackProtectMapping() {
        QrLabelView label=qr.label("DEVICE",device,true);
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO sys_qr_tokens(token,resource_type,device_id) VALUES(?, 'DEVICE', ?)",label.getContent().substring(9),oldDevice));
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO sys_qr_tokens(token,resource_type,device_id) VALUES(?, 'DEVICE', ?)","B".repeat(43),device));
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO sys_qr_tokens(token,resource_type,device_id,product_id) VALUES(?, 'DEVICE', ?, ?)","C".repeat(43),oldDevice,product));
        doThrow(new IllegalStateException("audit rollback test")).when(audit).insert(eq(user),eq("QR_GENERATE"),anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->qr.label("PRODUCT",product,true));
        assertEquals(0,count("SELECT COUNT(*) FROM sys_qr_tokens WHERE product_id=?",product));
    }

    @Test void concurrentGenerateCreatesOneToken() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try {
            List<Future<String>> tasks=new ArrayList<>();
            for(int i=0;i<2;i++) tasks.add(pool.submit(()->{
                current("ALL",department,true);ready.countDown();start.await();
                try{return qr.label("DEVICE",device,true).getContent();}
                finally{CurrentUserContext.clear();}
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertEquals(tasks.get(0).get(30,TimeUnit.SECONDS),tasks.get(1).get(30,TimeUnit.SECONDS));
            assertEquals(1,count("SELECT COUNT(*) FROM sys_qr_tokens WHERE device_id=?",device));
        } finally { start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS)); }
    }

    @Test void httpAuditHasRequestIdAndBadPermissionsAreRejected() throws Exception {
        String bearer=login("SYSTEM_ADMIN");
        mvc.perform(post("/api/assets/qr/PRODUCT/"+product).header("Authorization","Bearer "+bearer)
                .header("X-Request-Id","p9-generate")).andExpect(status().isOk());
        assertEquals(1,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND operation_type='QR_GENERATE' AND request_id='p9-generate'",user));
        current("ALL",department,true);
        String content=qr.label("PRODUCT",product,false).getContent();
        mvc.perform(post("/api/assets/qr/scan").header("Authorization","Bearer "+bearer)
                .header("X-Request-Id","p9-scan").contentType("application/json")
                .content(json.writeValueAsString(Map.of("content",content)))).andExpect(status().isOk());
        assertEquals(1,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND operation_type='QR_SCAN' AND request_id='p9-scan'",user));
        mvc.perform(post("/api/assets/qr/DEVICE/labels").header("Authorization","Bearer "+bearer)
                .header("X-Request-Id","p9-print").contentType("application/json")
                .content(json.writeValueAsString(Map.of("ids",List.of(device))))).andExpect(status().isOk());
        assertEquals(1,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND operation_type='QR_PRINT_PREVIEW' AND request_id='p9-print'",user));
        mvc.perform(post("/api/assets/qr/scan").contentType("application/json")
                .content(json.writeValueAsString(Map.of("content",content)))).andExpect(status().isUnauthorized());
        String viewer=login("MANAGEMENT");
        mvc.perform(post("/api/assets/qr/PRODUCT/"+product).header("Authorization","Bearer "+viewer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/assets/qr/scan").header("Authorization","Bearer "+viewer)
                .contentType("application/json").content(json.writeValueAsString(Map.of("content",content))))
                .andExpect(status().isForbidden());
    }

    private String login(String role) {
        String password=UUID.randomUUID().toString();
        jdbc.update("UPDATE sys_user SET password_hash=? WHERE id=?",encoder.encode(password),user);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",user);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",user,role);
        LoginRequest r=new LoginRequest();r.setUsername(prefix);r.setPassword(password);
        return auth.login(r).getAccessToken();
    }

    private String decode(String svg) throws Exception {
        Matcher size=Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertTrue(size.find());int dimension=Integer.parseInt(size.group(1));
        BufferedImage image=new BufferedImage(dimension*6,dimension*6,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,image.getWidth(),image.getHeight());
        graphics.setColor(Color.BLACK);
        Matcher cells=Pattern.compile("M(\\d+) (\\d+)h1v1h-1z").matcher(svg);
        while(cells.find())graphics.fillRect(Integer.parseInt(cells.group(1))*6,Integer.parseInt(cells.group(2))*6,6,6);
        graphics.dispose();
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
    }

    private Long newDevice(String status,Long owner,Long assignee,Long warehouseId) {
        String code=prefix+"DEVICE"+UUID.randomUUID().toString().substring(0,8);
        return id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id,warehouse_id) VALUES(?,?,?,?,?,?)",code,product,status,owner,assignee,warehouseId);
    }
    private void current(String scope,Long home,boolean full) {
        CurrentUserView view=new CurrentUserView();view.setUserId(user);view.setDataScope(scope);
        view.setPermissions(new ArrayList<>(full ? jdbc.queryForList("SELECT permission_code FROM sys_permission",String.class) : List.of("asset.qr.view")));
        DepartmentBriefView d=new DepartmentBriefView();d.setId(home);view.setDepartment(d);CurrentUserContext.set(view);
    }
    private int count(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    private Long id(String sql,Object... args) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
