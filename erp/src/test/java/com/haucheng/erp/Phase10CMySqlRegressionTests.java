package com.haucheng.erp;

import com.haucheng.erp.auth.CurrentUserContext;
import com.haucheng.erp.controller.AttachmentController;
import com.haucheng.erp.config.RequestIdFilter;
import com.haucheng.erp.exception.BusinessException;
import com.haucheng.erp.service.AttachmentService;
import com.haucheng.erp.service.MinioObjectStorageService;
import com.haucheng.erp.service.NotificationService;
import com.haucheng.erp.vo.CurrentUserView;
import com.haucheng.erp.vo.DepartmentBriefView;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="ERP_MYSQL_TEST",matches="true")
@SpringBootTest(properties={"spring.datasource.url=${DB_URL}","spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true","erp.auth.seed-username=","erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true",
        "erp.minio.endpoint=${MINIO_ENDPOINT:}","erp.minio.bucket=${MINIO_BUCKET:}",
        "erp.minio.access-key=${MINIO_ACCESS_KEY:}","erp.minio.secret-key=${MINIO_SECRET_KEY:}"})
@Transactional
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class Phase10CMySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AttachmentService attachments;
    @Autowired NotificationService notifications;
    @Autowired MinioObjectStorageService storage;
    @Autowired AttachmentController controller;
    @Autowired MockMvc mvc;
    private String prefix;
    private Long department, otherDepartment, user, otherUser, category, brand, unit, product, device;
    private String objectKey;

    @BeforeEach void setup() {
        assertTrue(System.getenv("DB_URL").matches("jdbc:mysql://192\\.168\\.1\\.247:3307/erp(?:\\?.*)?"));
        prefix="P10C_"+UUID.randomUUID().toString().replace("-","");
        department=id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)",prefix,prefix);
        otherDepartment=id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)",prefix+"O",prefix+"O");
        user=id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)",prefix,"test-only",prefix,department);
        otherUser=id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)",prefix+"O","test-only",prefix+"O",otherDepartment);
        category=id("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)",prefix,prefix);
        brand=id("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)",prefix,prefix);
        unit=id("INSERT INTO md_units(unit_code,unit_name) VALUES(?,?)",prefix,prefix);
        product=id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type) VALUES(?,?,?,?,?,'DEVICE')",prefix,prefix,category,brand,unit);
        device=id("INSERT INTO asset_devices(device_code,product_id,status,department_id,user_id) VALUES(?,?,'IN_USE',?,?)",prefix,product,department,user);
        current(user,department,"ALL");
    }

    @AfterEach void clear() { CurrentUserContext.clear(); RequestContextHolder.resetRequestAttributes(); }

    @AfterTransaction void verifyRollback() {
        if (prefix == null) return;
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username LIKE ?",Long.class,prefix+"%"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=?",Long.class,user));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachments WHERE display_name LIKE ?",Long.class,prefix+"%"));
        if (objectKey != null) assertThrows(BusinessException.class,()->storage.get(objectKey,7));
    }

    @Test void ownInboxUnreadIdempotencyAndActiveRecipient() {
        notifications.send(user,"INBOUND",product,"已完成",prefix);
        notifications.send(user,"INBOUND",product,"已完成",prefix);
        assertEquals(1,notifications.getPage(1,20).getTotal());
        assertEquals(1,notifications.unreadCount());
        Long id=notifications.getPage(1,20).getList().get(0).getId();
        current(otherUser,otherDepartment,"SELF");
        assertEquals(0,notifications.getPage(1,20).getTotal());
        assertEquals(0,notifications.unreadCount());
        assertThrows(BusinessException.class,()->notifications.get(id));
        assertThrows(BusinessException.class,()->notifications.markRead(id));
        assertEquals(0,notifications.markAllRead());
        current(user,department,"ALL");
        notifications.markRead(id); notifications.markRead(id);
        assertEquals(0,notifications.unreadCount());
        assertEquals(0,notifications.markAllRead());
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",otherUser);
        notifications.send(otherUser,"OUTBOUND",product,"完成",prefix);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_notifications WHERE recipient_user_id=?",Integer.class,otherUser));
    }

    @Test void httpRejectsMissingPermissionAndOtherUsersNotification() throws Exception {
        notifications.send(user,"INBOUND",product,"已完成",prefix);
        Long notificationId=notifications.getPage(1,20).getList().get(0).getId();
        Long role=id("INSERT INTO sys_role(role_code,role_name,data_scope) VALUES(?,?,'SELF')",prefix+"R",prefix+"R");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code='notification.view'",role);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",otherUser,role);
        String token=UUID.randomUUID().toString();
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO sys_access_token(token_hash,user_id,expires_at) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 1 HOUR))",hash,otherUser);
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/notifications/"+notificationId).header("Authorization","Bearer "+token))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/"+notificationId+"/read").header("Authorization","Bearer "+token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments?businessType=DEVICE&businessId="+device).header("Authorization","Bearer "+token))
                .andExpect(status().isForbidden());
        current(user,department,"ALL");
        assertEquals(1,notifications.unreadCount());
    }

    @Test void attachmentRejectsUnsafeInputsAndOutOfScopeAccessBeforeStorage() {
        MockMultipartFile text=file(prefix+".txt","text/plain","content");
        assertThrows(BusinessException.class,()->attachments.list("INVALID",device));
        assertThrows(BusinessException.class,()->attachments.list("DEVICE",-1L));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"bad key",text));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req1",file("../secret.txt","text/plain","content")));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req1",file(prefix+".html","text/html","<script/>")));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req1",file(prefix+".pdf","application/pdf","plain")));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req1",file(prefix+".txt","application/pdf","content")));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req1",file("x".repeat(201)+".txt","text/plain","content")));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req1",file(prefix+".txt","text/plain","")));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req1",
                new MockMultipartFile("file",prefix+".txt","text/plain",new byte[20*1024*1024+1])));
        current(otherUser,otherDepartment,"SELF");
        assertThrows(BusinessException.class,()->attachments.list("DEVICE",device));
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,"req2",text));
        current(otherUser,otherDepartment,"DEPARTMENT");
        assertThrows(BusinessException.class,()->attachments.list("DEVICE",device));
        current(user,department,"DEPARTMENT");
        assertEquals(0,attachments.list("DEVICE",device).size());
    }

    @Test void missingObjectStorageConfigurationFailsClearly() {
        var unconfigured=new MinioObjectStorageService();
        ReflectionTestUtils.setField(unconfigured,"endpoint","");
        ReflectionTestUtils.setField(unconfigured,"bucket","");
        ReflectionTestUtils.setField(unconfigured,"accessKey","");
        ReflectionTestUtils.setField(unconfigured,"secretKey","");
        assertThrows(BusinessException.class,()->unconfigured.put("attachments/test",new byte[]{1},"text/plain"));
    }

    @Test @EnabledIfEnvironmentVariable(named="ERP_MINIO_TEST",matches="true")
    void realMinioUploadDownloadAndRollbackCompensation() throws Exception {
        MockMultipartFile file=file(prefix+"_中文.txt","text/plain","content");
        MockHttpServletRequest http=new MockHttpServletRequest();
        http.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE,prefix);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(http));
        var first=attachments.upload("DEVICE",device,prefix,file);
        var again=attachments.upload("DEVICE",device,prefix,file);
        assertEquals(first.id(),again.id());
        assertEquals("content",new String(attachments.download(first.id()).bytes(),StandardCharsets.UTF_8));
        assertEquals(1,attachments.list("DEVICE",device).size());
        assertEquals(prefix,jdbc.queryForObject("SELECT request_id FROM sys_operation_log WHERE operation_type='ATTACHMENT_UPLOAD' AND operator_user_id=?",String.class,user));
        objectKey=jdbc.queryForObject("SELECT object_key FROM sys_attachments WHERE id=?",String.class,first.id());
        assertArrayEquals("content".getBytes(StandardCharsets.UTF_8),storage.get(objectKey,7));
        var response=controller.download(first.id(),false);
        assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("filename*="));
        assertEquals("text/plain",response.getHeaders().getContentType().toString());
        String anonymous=System.getenv("MINIO_ENDPOINT")+"/"+System.getenv("MINIO_BUCKET")+"/"+objectKey;
        assertEquals(403,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(anonymous)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
        current(otherUser,otherDepartment,"SELF");
        assertThrows(BusinessException.class,()->attachments.download(first.id()));
        assertThrows(BusinessException.class,()->attachments.delete(first.id()));
        current(user,department,"ALL");
        assertThrows(BusinessException.class,()->attachments.upload("DEVICE",device,prefix,file(prefix+".txt","text/plain","different")));
        assertThrows(BusinessException.class,()->attachments.get(Long.MAX_VALUE));
    }

    @Test @EnabledIfEnvironmentVariable(named="ERP_MINIO_TEST",matches="true")
    void directObjectCanBeRemoved() {
        String key="attachments/"+UUID.randomUUID();
        storage.put(key,"content".getBytes(StandardCharsets.UTF_8),"text/plain");
        assertArrayEquals("content".getBytes(StandardCharsets.UTF_8),storage.get(key,7));
        storage.remove(key);
        assertThrows(BusinessException.class,()->storage.get(key,7));
    }

    @Test @EnabledIfEnvironmentVariable(named="ERP_MINIO_TEST",matches="true")
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void committedAttachmentDeleteRemovesObjectAndKeepsAudit() {
        Long attachmentId=null;
        String key=null;
        try {
            var uploaded=attachments.upload("DEVICE",device,prefix,file(prefix+"_中文.txt","text/plain","content"));
            attachmentId=uploaded.id();
            key=jdbc.queryForObject("SELECT object_key FROM sys_attachments WHERE id=?",String.class,attachmentId);
            assertEquals("READY",jdbc.queryForObject("SELECT status FROM sys_attachments WHERE id=?",String.class,attachmentId));
            assertEquals(1,attachments.list("DEVICE",device).size());
            attachments.delete(attachmentId);
            assertEquals("DELETED",jdbc.queryForObject("SELECT status FROM sys_attachments WHERE id=?",String.class,attachmentId));
            assertEquals(0,attachments.list("DEVICE",device).size());
            Long deletedId=attachmentId;
            assertThrows(BusinessException.class,()->attachments.get(deletedId));
            String deletedKey=key;
            assertThrows(BusinessException.class,()->storage.get(deletedKey,7));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND operation_type LIKE 'ATTACHMENT_%'",Integer.class,user));
        } finally {
            if (key != null) storage.remove(key);
            if (attachmentId != null) jdbc.update("DELETE FROM sys_attachments WHERE id=?",attachmentId);
            jdbc.update("DELETE FROM sys_operation_log WHERE operator_user_id=?",user);
            jdbc.update("DELETE FROM asset_devices WHERE id=?",device);
            jdbc.update("DELETE FROM md_products WHERE id=?",product);
            jdbc.update("DELETE FROM md_categories WHERE id=?",category);
            jdbc.update("DELETE FROM md_brands WHERE id=?",brand);
            jdbc.update("DELETE FROM md_units WHERE id=?",unit);
            jdbc.update("DELETE FROM sys_user WHERE id IN (?,?)",user,otherUser);
            jdbc.update("DELETE FROM sys_department WHERE id IN (?,?)",department,otherDepartment);
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachments WHERE display_name LIKE ?",Integer.class,prefix+"%"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username LIKE ?",Integer.class,prefix+"%"));
    }

    private MockMultipartFile file(String name,String type,String value) {
        return new MockMultipartFile("file",name,type,value.getBytes(StandardCharsets.UTF_8));
    }

    private void current(Long id,Long dept,String scope) {
        CurrentUserView view=new CurrentUserView(); view.setUserId(id); view.setDataScope(scope);
        DepartmentBriefView d=new DepartmentBriefView(); d.setId(dept); view.setDepartment(d);
        view.setPermissions(List.of("attachment.view","attachment.upload","attachment.delete","system.device.view","system.device.update"));
        CurrentUserContext.set(view);
    }

    private Long id(String sql,Object... args) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); return statement;},key);
        return key.getKey().longValue();
    }
}
