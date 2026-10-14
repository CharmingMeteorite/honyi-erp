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

@EnabledIfEnvironmentVariable(named="ERP_MYSQL_TEST", matches="true")
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "erp.auth.seed-username=", "erp.auth.seed-password=",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class Phase8MySqlRegressionTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AssetServiceOrderService service;
    @Autowired OpeningBalanceService openings;
    @Autowired InventoryOrderService orders;
    @Autowired AssetDeviceService devices;
    @Autowired QrService qr;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean OperationLogMapper audit;

    private String prefix;
    private Long category, brand, unit, quantityProduct, deviceProduct, otherProduct;
    private Long department, outsideDepartment, operator, warehouse, outsideWarehouse;

    @BeforeEach void setup() {
        prefix = "P8_" + UUID.randomUUID().toString().replace("-", "");
        category = id("INSERT INTO md_categories(category_code,category_name) VALUES(?,?)", prefix, prefix);
        brand = id("INSERT INTO md_brands(brand_code,brand_name) VALUES(?,?)", prefix, prefix);
        unit = id("INSERT INTO md_units(unit_code,unit_name,precision_scale) VALUES(?,?,4)", prefix, prefix);
        quantityProduct = product("Q", "QUANTITY");
        deviceProduct = product("D", "DEVICE");
        otherProduct = product("O", "DEVICE");
        department = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix, prefix);
        outsideDepartment = id("INSERT INTO sys_department(department_code,department_name) VALUES(?,?)", prefix + "O", prefix + "O");
        operator = id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)",
                prefix, "unused-test-hash", prefix, department);
        warehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)",
                prefix + "W", prefix + "W", department);
        outsideWarehouse = id("INSERT INTO wh_warehouses(warehouse_code,warehouse_name,department_id) VALUES(?,?,?)",
                prefix + "O", prefix + "O", outsideDepartment);
        current("ALL", department);
    }

    @AfterEach void cleanup() {
        reset(audit);
        CurrentUserContext.clear();
        jdbc.update("DELETE FROM sys_qr_tokens WHERE product_id IN (?,?,?) OR device_id IN (SELECT id FROM asset_devices WHERE product_id IN (?,?,?))",
                quantityProduct, deviceProduct, otherProduct, quantityProduct, deviceProduct, otherProduct);
        jdbc.update("DELETE FROM asset_service_requests WHERE operator_user_id=?", operator);
        jdbc.update("DELETE c FROM inv_order_device_claims c JOIN asset_exchange_devices d ON d.id=c.exchange_device_id JOIN asset_exchange_orders o ON o.id=d.order_id WHERE o.product_id IN (?,?)", deviceProduct, otherProduct);
        jdbc.update("DELETE d FROM asset_exchange_devices d JOIN asset_exchange_orders o ON o.id=d.order_id WHERE o.product_id IN (?,?)", deviceProduct, otherProduct);
        jdbc.update("DELETE FROM asset_exchange_orders WHERE product_id IN (?,?)", deviceProduct, otherProduct);
        jdbc.update("DELETE c FROM inv_order_device_claims c JOIN asset_repair_orders o ON o.id=c.repair_order_id WHERE o.product_id IN (?,?,?)", deviceProduct, otherProduct, quantityProduct);
        jdbc.update("DELETE FROM asset_repair_orders WHERE product_id IN (?,?,?)", deviceProduct, otherProduct, quantityProduct);
        jdbc.update("DELETE FROM asset_warranty_orders WHERE product_id IN (?,?,?)", deviceProduct, otherProduct, quantityProduct);
        jdbc.update("DELETE FROM inv_stocktake_device_checks WHERE stocktake_id IN (SELECT id FROM inv_stocktakes WHERE warehouse_id IN (?,?))", warehouse, outsideWarehouse);
        jdbc.update("DELETE FROM inv_stocktake_items WHERE stocktake_id IN (SELECT id FROM inv_stocktakes WHERE warehouse_id IN (?,?))", warehouse, outsideWarehouse);
        jdbc.update("DELETE FROM inv_stocktakes WHERE warehouse_id IN (?,?)", warehouse, outsideWarehouse);
        for (String kind : List.of("inbound", "outbound")) {
            jdbc.update("DELETE c FROM inv_order_device_claims c JOIN inv_" + kind + "_devices d ON d.id=c." + kind + "_device_id JOIN inv_" + kind + "_orders o ON o.id=d.order_id WHERE o.warehouse_id IN (?,?)", warehouse, outsideWarehouse);
            jdbc.update("DELETE d FROM inv_" + kind + "_devices d JOIN inv_" + kind + "_orders o ON o.id=d.order_id WHERE o.warehouse_id IN (?,?)", warehouse, outsideWarehouse);
            jdbc.update("DELETE i FROM inv_" + kind + "_items i JOIN inv_" + kind + "_orders o ON o.id=i.order_id WHERE o.warehouse_id IN (?,?)", warehouse, outsideWarehouse);
            jdbc.update("DELETE FROM inv_" + kind + "_orders WHERE warehouse_id IN (?,?)", warehouse, outsideWarehouse);
        }
        List<Long> openingIds = jdbc.queryForList("SELECT id FROM inv_opening_balances WHERE warehouse_id IN (?,?)", Long.class, warehouse, outsideWarehouse);
        for (Long opening : openingIds) {
            jdbc.update("DELETE FROM inv_opening_balance_devices WHERE opening_id=?", opening);
            jdbc.update("DELETE FROM inv_opening_balance_items WHERE opening_id=?", opening);
            jdbc.update("DELETE FROM inv_opening_balances WHERE id=?", opening);
        }
        jdbc.update("DELETE FROM inv_stock_ledgers WHERE product_id IN (?,?,?)", quantityProduct, deviceProduct, otherProduct);
        jdbc.update("DELETE FROM inv_stocks WHERE product_id IN (?,?,?)", quantityProduct, deviceProduct, otherProduct);
        jdbc.update("DELETE FROM asset_device_history WHERE device_id IN (SELECT id FROM asset_devices WHERE product_id IN (?,?,?))", deviceProduct, otherProduct, quantityProduct);
        jdbc.update("DELETE FROM asset_devices WHERE product_id IN (?,?,?)", deviceProduct, otherProduct, quantityProduct);
        jdbc.update("DELETE FROM sys_operation_log WHERE operator_user_id=?", operator);
        jdbc.update("DELETE FROM sys_access_token WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", operator);
        jdbc.update("DELETE FROM sys_user WHERE id=?", operator);
        jdbc.update("DELETE FROM sys_user WHERE username=?", prefix+"OTHER");
        jdbc.update("DELETE FROM wh_warehouses WHERE id IN (?,?)", warehouse, outsideWarehouse);
        jdbc.update("DELETE FROM sys_department WHERE id IN (?,?)", department, outsideDepartment);
        jdbc.update("DELETE FROM md_products WHERE category_id=?", category);
        jdbc.update("DELETE FROM md_categories WHERE id=?", category);
        jdbc.update("DELETE FROM md_brands WHERE id=?", brand);
        jdbc.update("DELETE FROM md_units WHERE id=?", unit);
        assertEquals(0,count("SELECT COUNT(*) FROM md_products WHERE product_code LIKE ?",prefix+"%"));
        assertEquals(0,count("SELECT COUNT(*) FROM asset_devices WHERE device_code LIKE ?",prefix+"%"));
        assertEquals(0,count("SELECT COUNT(*) FROM asset_service_requests WHERE operator_user_id=?",operator));
    }


    @Test void draftEditPersistsNotesAndCancelReleasesClaim() {
        Long device=usedDevice(); AssetServiceOrderRequest r=request(device); r.setNotes("注意事项"); r.setRemark("维修备注");
        AssetServiceOrderView draft=service.create("repair",r);
        assertEquals(draft.getId(),service.create("repair",r).getId());
        r.setVersion(draft.getVersion()); r.setNotes("更正注意事项"); r.setRemark("更正备注");
        AssetServiceOrderView edited=service.update("repair",draft.getId(),r);
        assertEquals("更正注意事项",edited.getNotes()); assertEquals("更正备注",edited.getRemark());
        assertEquals(edited.getVersion(),service.update("repair",draft.getId(),r).getVersion());
        assertThrows(BusinessException.class,()->perform("repair",edited,"complete"));
        AssetServiceOrderView cancelled=perform("repair",edited,"cancel"); assertEquals("CANCELLED",cancelled.getStatus());
        assertEquals(0,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?",device));
        assertThrows(BusinessException.class,()->perform("repair",cancelled,"submit"));
    }

    @Test void inventoryRepairLeavesAndReturnsWithRealLedgers() {
        Long device=stockDevice(); AssetServiceOrderView repairing=repairing(device);
        assertEquals("REPAIRING",state(device)); assertNull(warehouse(device)); assertEquals(0,stock());
        AssetServiceActionRequest a=action(repairing); a.setRemark("已更换接口"); a.setNotes("返库后观察");
        AssetServiceOrderView completed=service.action("repair",repairing.getId(),"complete",a);
        assertEquals("IN_STOCK",state(device)); assertEquals(warehouse,warehouse(device));assertEquals(1,stock());
        assertEquals("已更换接口",completed.getRemark()); assertEquals("返库后观察",completed.getNotes());
        assertEquals(List.of("REPAIR_OUT","REPAIR_RETURN"),completed.getLedgers().stream().map(StockLedgerView::getBusinessType).toList());
        assertEquals(2,count("SELECT COUNT(*) FROM asset_device_history WHERE device_id=? AND business_type='REPAIR'",device));
        assertEquals(completed.getVersion(),service.action("repair",repairing.getId(),"complete",a).getVersion());
        assertThrows(BusinessException.class,()->perform("repair",completed,"complete"));
        assertEquals("CLOSED",perform("repair",completed,"close").getStatus()); consistency();
    }

    @Test void inUseRepairRestoresAssignmentsWithoutInventory() {
        Long device=usedDevice(); AssetServiceOrderView completed=perform("repair",repairing(device),"complete");
        assertEquals("IN_USE",state(device));assertNull(warehouse(device));
        assertEquals(operator,user(device));assertEquals(department,deviceDepartment(device)); assertTrue(completed.getLedgers().isEmpty());
    }

    @Test void unrepairableUsedDeviceExchangesIntoUseWithoutChangingIdentity() {
        Long old=usedDevice(),next=stockDevice(); String oldCode=code(old),nextCode=code(next),sn=serial(old);
        AssetServiceOrderView failed=perform("repair",repairing(old),"unrepairable");
        assertEquals("UNREPAIRABLE",failed.getStatus());assertEquals("PENDING_EXCHANGE",state(old));
        AssetServiceOrderView done=perform("exchange",approvedExchange(old,next),"execute");
        assertEquals("SHIPPED",state(old));assertNull(user(old));assertEquals("IN_USE",state(next));assertEquals(operator,user(next));
        assertEquals(department,deviceDepartment(next)); assertNull(warehouse(next));assertEquals(0,stock());
        assertEquals(oldCode,code(old));assertEquals(nextCode,code(next));assertEquals(sn,serial(old));
        assertEquals(oldCode,done.getDeviceCode());assertEquals(nextCode,done.getNewDeviceCode());assertEquals("更换故障设备",done.getReason());
        assertEquals("EXCHANGE",done.getLedgers().get(0).getBusinessType());assertEquals("OUT",done.getLedgers().get(0).getDirection());
        assertEquals(2,count("SELECT COUNT(*) FROM asset_device_history WHERE business_type='EXCHANGE' AND device_id IN (?,?)",old,next)); consistency();
    }

    @Test void unrepairableStockDeviceReplacementShipsWithoutInventedUser() {
        Long old=stockDevice(),next=stockDevice(); perform("repair",repairing(old),"unrepairable"); assertEquals(1,stock());
        perform("exchange",approvedExchange(old,next),"execute");
        assertEquals("SHIPPED",state(next));assertNull(user(next));assertNull(warehouse(old));assertEquals(0,stock()); consistency();
    }

    @Test void validWarrantyCreatesIndependentLinkedRepairAndCompletesAfterRepair() {
        Long device=usedDevice(); warrantyDates(device,-1,1);
        AssetServiceOrderView accepted=warrantyAccepted(device);assertEquals("VALID",accepted.getWarrantyStatus());
        assertNull(accepted.getLinkedRepairId()); assertThrows(BusinessException.class,()->perform("warranty",accepted,"complete"));
        AssetServiceOrderView approved=perform("warranty",accepted,"approve");assertEquals("WARRANTY",approved.getDecision());
        assertEquals("IN_USE",state(device));assertNotNull(approved.getLinkedRepairId());
        assertThrows(BusinessException.class,()->perform("warranty",approved,"complete"));
        AssetServiceOrderView linked=service.getById("repair",approved.getLinkedRepairId());
        linked=perform("repair",linked,"submit");linked=perform("repair",linked,"accept");linked=perform("repair",linked,"start");perform("repair",linked,"complete");
        assertEquals("COMPLETED",perform("warranty",approved,"complete").getStatus());
    }

    @Test void expiredWarrantyCannotApproveButCanExplicitlyReject() {
        Long device=usedDevice();warrantyDates(device,-5,-1);
        AssetServiceOrderView accepted=warrantyAccepted(device);assertEquals("EXPIRED",accepted.getWarrantyStatus());
        assertThrows(BusinessException.class,()->perform("warranty",accepted,"approve"));
        AssetServiceOrderView rejected=perform("warranty",accepted,"reject");assertEquals("REJECTED",rejected.getStatus());assertEquals("REJECTED",rejected.getDecision());
        assertNull(rejected.getLinkedRepairId());assertEquals("IN_USE",state(device));
        assertThrows(BusinessException.class,()->perform("warranty",rejected,"reject"));
    }

    @Test void expiredWarrantyPaidRepairIsExplicitAndTraceable() {
        Long device=usedDevice();warrantyDates(device,-5,-1);
        AssetServiceOrderView paid=perform("warranty",warrantyAccepted(device),"paid-repair");
        assertEquals("EXPIRED",paid.getWarrantyStatus());assertEquals("PAID_REPAIR",paid.getDecision());assertNotNull(paid.getLinkedRepairId());
        assertEquals("PAID_REPAIR",service.getById("warranty",paid.getId()).getDecision());assertEquals("IN_USE",state(device));
    }

    @Test void unknownAndNotStartedWarrantyCannotSilentlyApprove() {
        AssetServiceOrderView unknown=warrantyAccepted(usedDevice());assertEquals("UNKNOWN",unknown.getWarrantyStatus());
        assertThrows(BusinessException.class,()->perform("warranty",unknown,"approve"));
        Long device=usedDevice();warrantyDates(device,1,5);AssetServiceOrderView future=warrantyAccepted(device);assertEquals("NOT_STARTED",future.getWarrantyStatus());
        assertThrows(BusinessException.class,()->perform("warranty",future,"approve"));
    }

    @Test void warrantySnapshotCannotBeRewrittenByLaterDeviceDates() {
        Long device=usedDevice();warrantyDates(device,-4,-1);AssetServiceOrderView expired=warrantyAccepted(device);
        warrantyDates(device,-1,10);assertThrows(BusinessException.class,()->perform("warranty",expired,"approve"));
        assertEquals("EXPIRED",service.getById("warranty",expired.getId()).getWarrantyStatus());
    }

    @Test void mutualExclusionIncludesInventoryOrdersAndRepair() {
        Long device=stockDevice();AssetServiceOrderView repair=service.create("repair",request(device));
        InventoryOrderRequest r=new InventoryOrderRequest();r.setWarehouseId(warehouse);r.setClientRequestId(UUID.randomUUID().toString());
        InventoryOrderItemRequest item=new InventoryOrderItemRequest();item.setProductId(deviceProduct);item.setQuantity(BigDecimal.ONE);item.setDeviceIds(List.of(device));r.setItems(List.of(item));
        assertThrows(BusinessException.class,()->orders.create(false,r));
        assertThrows(BusinessException.class,()->service.create("repair",request(device)));
        perform("repair",repair,"cancel");InventoryOrderView outbound=orders.create(false,r);
        assertThrows(BusinessException.class,()->service.create("repair",request(device)));
        assertEquals("DRAFT",outbound.getStatus());
    }

    @Test void exchangeClaimsBothDevicesAndRejectReleasesThem() {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");
        AssetServiceOrderView exchange=service.create("exchange",exchangeRequest(old,next));
        assertThrows(BusinessException.class,()->service.create("repair",request(next)));
        assertEquals(2,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id IN (?,?)",old,next));
        exchange=perform("exchange",exchange,"submit");perform("exchange",exchange,"reject");
        assertEquals(0,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id IN (?,?)",old,next));
        assertEquals("IN_STOCK",state(next));assertEquals(1,stock());
    }

    @Test void exchangeRejectsSameWrongProductAndUnavailableDevices() {
        Long old=usedDevice();perform("repair",repairing(old),"unrepairable");
        assertThrows(BusinessException.class,()->service.create("exchange",exchangeRequest(old,old)));
        Long other=device(otherProduct,"IN_STOCK",null); assertThrows(BusinessException.class,()->service.create("exchange",exchangeRequest(old,other)));
        Long used=usedDevice();assertThrows(BusinessException.class,()->service.create("exchange",exchangeRequest(old,used)));
        Long pending=device(deviceProduct,"PENDING_EXCHANGE",null);assertThrows(BusinessException.class,()->service.create("exchange",exchangeRequest(pending,stockDevice())));
    }

    @Test void concurrentRepairCreationHasOneOwner() throws Exception {
        Long device=usedDevice();assertEquals(1,race(()->service.create("repair",request(device)),()->service.create("repair",request(device))));
        assertEquals(1,count("SELECT COUNT(*) FROM asset_repair_orders WHERE device_id=?",device));
        assertEquals(1,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?",device));
    }

    @Test void concurrentRepairCompletionWritesOneReturn() throws Exception {
        Long device=stockDevice();AssetServiceOrderView repairing=repairing(device);
        assertEquals(1,race(()->perform("repair",repairing,"complete"),()->perform("repair",repairing,"complete")));
        assertEquals(1,stock());assertEquals(1,count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE business_type='REPAIR_RETURN' AND product_id=?",deviceProduct)); consistency();
    }

    @Test void concurrentExchangeExecutionWritesOneLedger() throws Exception {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");AssetServiceOrderView approved=approvedExchange(old,next);
        assertEquals(1,race(()->perform("exchange",approved,"execute"),()->perform("exchange",approved,"execute")));
        assertEquals(0,stock());assertEquals(1,count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE business_type='EXCHANGE' AND product_id=?",deviceProduct)); consistency();
    }

    @Test void repairAndExchangeCompeteForReplacementAtomically() throws Exception {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");
        assertEquals(1,race(()->service.create("exchange",exchangeRequest(old,next)),()->service.create("repair",request(next))));
        assertEquals(1,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?",next)); consistency();
    }

    @Test void selfDepartmentAndAllScopesAreEnforcedOnListsAndDetails() {
        Long own=usedDevice(),outside=device(deviceProduct,"IN_USE",null);jdbc.update("UPDATE asset_devices SET department_id=? WHERE id=?",outsideDepartment,outside);
        AssetServiceOrderView ownOrder=service.create("repair",request(own)),outsideOrder=service.create("repair",request(outside));
        current("DEPARTMENT",department);assertThrows(BusinessException.class,()->service.getById("repair",outsideOrder.getId()));
        assertEquals(1,service.getPage("repair",null,null,null,null,null,null,null,1,20).getTotal());
        current("SELF",department);assertEquals(2,service.getPage("repair",null,null,null,null,null,null,null,1,20).getTotal());
        AssetServiceOrderView submitted=perform("repair",ownOrder,"submit");assertThrows(BusinessException.class,()->perform("repair",submitted,"accept"));
        jdbc.update("UPDATE asset_repair_orders SET applicant_user_id=? WHERE id=?",otherUser(),outsideOrder.getId());
        assertThrows(BusinessException.class,()->service.getById("repair",outsideOrder.getId()));
        current("ALL",department);assertEquals(2,service.getPage("repair",null,null,null,null,null,null,null,1,20).getTotal());
    }

    @Test void permissionsAreCheckedInServiceAndHttp() throws Exception {
        Long device=usedDevice();CurrentUserContext.require().setPermissions(List.of("asset.repair.view"));
        assertThrows(BusinessException.class,()->service.create("repair",request(device)));
        mvc.perform(post("/api/assets/repairs").contentType("application/json").content(json.writeValueAsString(request(device)))).andExpect(status().isUnauthorized());
        String token=token("MANAGEMENT");
        mvc.perform(post("/api/assets/repairs").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(request(device)))).andExpect(status().isForbidden());
    }

    @Test void requestIdConnectsRepairDeviceHistoryLedgerAndAudit() throws Exception {
        Long device=stockDevice();AssetServiceOrderView repairing=repairing(device);String token=token("SYSTEM_ADMIN");
        mvc.perform(post("/api/assets/repairs/"+repairing.getId()+"/complete").header("Authorization","Bearer "+token).header("X-Request-Id","p8-repair-return")
                .contentType("application/json").content(json.writeValueAsString(action(repairing)))).andExpect(status().isOk());
        assertEquals(1,count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE product_id=? AND request_id='p8-repair-return'",deviceProduct));
        assertEquals(1,count("SELECT COUNT(*) FROM asset_device_history WHERE device_id=? AND request_id='p8-repair-return'",device));
        assertEquals(1,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND request_id='p8-repair-return'",operator));
    }

    @Test void auditFailureRollsBackRepairStartAndReturn() {
        Long device=stockDevice();AssetServiceOrderView accepted=perform("repair",perform("repair",service.create("repair",request(device)),"submit"),"accept");
        doThrow(new IllegalStateException("fixture audit failure")).when(audit).insert(any(),eq("REPAIR_START"),anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->perform("repair",accepted,"start")); reset(audit);
        assertEquals("IN_STOCK",state(device));assertEquals(1,stock());assertEquals("ACCEPTED",service.getById("repair",accepted.getId()).getStatus());
        AssetServiceOrderView repairing=perform("repair",accepted,"start");
        doThrow(new IllegalStateException("fixture audit failure")).when(audit).insert(any(),eq("REPAIR_COMPLETE"),anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->perform("repair",repairing,"complete"));reset(audit);
        assertEquals("REPAIRING",state(device));assertEquals(0,stock());assertEquals(1,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?",device));
        assertEquals(0,count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE product_id=? AND business_type='REPAIR_RETURN'",deviceProduct));consistency();
    }

    @Test void auditFailureRollsBackExchangeBothDevicesAndStock() {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");AssetServiceOrderView approved=approvedExchange(old,next);
        doThrow(new IllegalStateException("fixture audit failure")).when(audit).insert(any(),eq("EXCHANGE_EXECUTE"),anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->perform("exchange",approved,"execute"));reset(audit);
        assertEquals("PENDING_EXCHANGE",state(old));assertEquals("IN_STOCK",state(next));assertEquals(1,stock());
        assertEquals("APPROVED",service.getById("exchange",approved.getId()).getStatus());assertEquals(0,count("SELECT COUNT(*) FROM asset_device_history WHERE business_type='EXCHANGE' AND device_id=?",next));consistency();
    }

    @Test void changedAssignmentsAreRejectedInsteadOfOverwritten() {
        Long device=usedDevice();AssetServiceOrderView repairing=repairing(device);
        jdbc.update("UPDATE asset_devices SET user_id=?,version=version+1 WHERE id=?",otherUser(),device);
        assertThrows(BusinessException.class,()->perform("repair",repairing,"complete"));assertEquals("REPAIRING",state(device));assertNotEquals(operator,user(device));
    }

    @Test void ordinaryDeviceEditCannotBypassClaimsAndIdentityLocks() {
        Long device=usedDevice();AssetServiceOrderView repairing=repairing(device);
        DeviceUpdateRequest r=new DeviceUpdateRequest();r.setDepartmentId(department);r.setUserId(operator);r.setCustodianId(operator);r.setSerialNumber(serial(device));
        r.setNotes("维修期间更新说明");assertThrows(BusinessException.class,()->devices.update(device,r));
        jdbc.update("UPDATE asset_devices SET notes=?,version=version+1 WHERE id=?","维修期间更新说明",device);
        assertThrows(BusinessException.class,()->perform("repair",repairing,"complete"));assertEquals("维修期间更新说明",jdbc.queryForObject("SELECT notes FROM asset_devices WHERE id=?",String.class,device));
        r.setSerialNumber(prefix+"ALTERED");assertThrows(BusinessException.class,()->devices.update(device,r));
        Long unstock=device(deviceProduct,"IN_STOCK",null);service.create("repair",request(unstock));
        DeviceStatusRequest status=new DeviceStatusRequest();status.setStatus("SCRAPPED");status.setReason("不能绕过");assertThrows(BusinessException.class,()->devices.updateStatus(unstock,status));
    }

    @Test void unavailableStockAndQuantityProductsAreRejected() {
        Long device=stockDevice();AssetServiceOrderView accepted=perform("repair",perform("repair",service.create("repair",request(device)),"submit"),"accept");
        jdbc.update("UPDATE inv_stocks SET locked_quantity=quantity WHERE warehouse_id=? AND product_id=?",warehouse,deviceProduct);
        assertThrows(BusinessException.class,()->perform("repair",accepted,"start"));assertEquals("IN_STOCK",state(device));assertEquals(1,stock());
        Long illegal=device(quantityProduct,"IN_STOCK",null);assertThrows(BusinessException.class,()->service.create("repair",request(illegal)));
    }

    @Test void optionsAreFilteredOnServerAndSchemaHasConstraints() {
        Long available=stockDevice(),used=usedDevice();AssetServiceOrderView claim=service.create("repair",request(available));
        assertFalse(service.getDeviceOptions("repair",false,deviceProduct,null,1,100).getList().stream().anyMatch(d->d.getId().equals(available)));
        assertFalse(service.getDeviceOptions("warranty",false,deviceProduct,null,1,100).getList().stream().anyMatch(d->d.getId().equals(available)));
        assertTrue(service.getDeviceOptions("repair",false,deviceProduct,null,1,100).getList().stream().anyMatch(d->d.getId().equals(used)));
        perform("repair",claim,"cancel");assertEquals(1,service.getDeviceOptions("exchange",true,deviceProduct,null,1,100).getTotal());
        current("SELF",department);assertEquals(0,service.getDeviceOptions("exchange",true,deviceProduct,null,1,100).getTotal());current("ALL",department);
        assertTrue(count("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name='asset_exchange_orders' AND constraint_type='FOREIGN KEY'")>=10);
        assertTrue(count("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name='asset_repair_orders' AND constraint_type='CHECK'")>=4);
        assertEquals(24,count("SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'asset.repair.%' OR permission_code LIKE 'asset.warranty.%' OR permission_code LIKE 'asset.exchange.%'"));
    }

    @Test void concurrentWarrantyApprovalCreatesOneLinkedRepair() throws Exception {
        Long device=usedDevice();warrantyDates(device,-1,1);AssetServiceOrderView accepted=warrantyAccepted(device);
        assertEquals(1,race(()->perform("warranty",accepted,"approve"),()->perform("warranty",accepted,"approve")));
        assertEquals(1,count("SELECT COUNT(*) FROM asset_repair_orders WHERE warranty_id=?",accepted.getId()));
        assertEquals(1,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id=?",device));
    }

    @Test void changedVersionRejectsRepairRestorationEvenWhenAssignmentsLookSame() {
        Long device=usedDevice();AssetServiceOrderView repairing=repairing(device);
        jdbc.update("UPDATE asset_devices SET version=version+1 WHERE id=?",device);
        assertThrows(BusinessException.class,()->perform("repair",repairing,"complete"));
        assertEquals("REPAIRING",state(device));
    }

    @Test void exchangeRejectsReplacementChangedSinceDraft() {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");
        AssetServiceOrderView draft=service.create("exchange",exchangeRequest(old,next));
        jdbc.update("UPDATE asset_devices SET version=version+1 WHERE id=?",next);
        assertThrows(BusinessException.class,()->perform("exchange",draft,"submit"));
        assertEquals(1,stock());consistency();
    }

    @Test void warrantyCancellationAndPaginationPreserveApplicantScope() {
        Long device=usedDevice();AssetServiceOrderView draft=service.create("warranty",request(device));
        assertEquals(1,service.getPage("warranty",null,"DRAFT",device,deviceProduct,operator,null,null,1,1).getTotal());
        AssetServiceOrderView cancelled=perform("warranty",draft,"cancel");
        assertEquals("CANCELLED",cancelled.getStatus());assertThrows(BusinessException.class,()->perform("warranty",cancelled,"submit"));
    }

    @Test void exchangeApprovalAndCancellationAreVersioned() {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");
        AssetServiceOrderView draft=service.create("exchange",exchangeRequest(old,next));
        AssetServiceOrderView submitted=perform("exchange",draft,"submit");
        assertThrows(BusinessException.class,()->perform("exchange",submitted,"execute"));
        AssetServiceOrderView cancelled=perform("exchange",submitted,"cancel");
        assertEquals("CANCELLED",cancelled.getStatus());assertEquals(0,count("SELECT COUNT(*) FROM inv_order_device_claims WHERE device_id IN (?,?)",old,next));
        assertThrows(BusinessException.class,()->perform("exchange",cancelled,"approve"));
    }

    @Test void warrantyCannotApproveWhenAnotherRepairOwnsDevice() {
        Long device=usedDevice();warrantyDates(device,-1,1);AssetServiceOrderView accepted=warrantyAccepted(device);
        AssetServiceOrderView repair=service.create("repair",request(device));
        assertThrows(BusinessException.class,()->perform("warranty",accepted,"approve"));
        perform("repair",repair,"cancel");assertEquals("APPROVED",perform("warranty",accepted,"approve").getStatus());
    }

    @Test void warrantyCannotBeCreatedDuringActiveExchange() {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");
        service.create("exchange",exchangeRequest(old,next));
        assertThrows(BusinessException.class,()->service.create("warranty",request(old)));
    }

    @Test void requestIdConnectsExchangeOldNewHistoryStockAndAudit() throws Exception {
        Long old=usedDevice(),next=stockDevice();perform("repair",repairing(old),"unrepairable");
        QrLabelView oldQr=qr.label("DEVICE",old,true),newQr=qr.label("DEVICE",next,true);
        assertNotEquals(oldQr.getContent(),newQr.getContent());
        AssetServiceOrderView approved=approvedExchange(old,next);String token=token("SYSTEM_ADMIN");
        mvc.perform(post("/api/assets/exchanges/"+approved.getId()+"/execute").header("Authorization","Bearer "+token).header("X-Request-Id","p8-exchange")
                .contentType("application/json").content(json.writeValueAsString(action(approved)))).andExpect(status().isOk());
        assertEquals(1,count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE product_id=? AND business_type='EXCHANGE' AND request_id='p8-exchange'",deviceProduct));
        assertEquals(2,count("SELECT COUNT(*) FROM asset_device_history WHERE business_type='EXCHANGE' AND device_id IN (?,?) AND request_id='p8-exchange'",old,next));
        assertEquals(1,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND operation_type='EXCHANGE_EXECUTE' AND request_id='p8-exchange'",operator));
        current("ALL",department);
        assertEquals(old,qr.scan(oldQr.getContent()).getDevice().getId());
        assertEquals(next,qr.scan(newQr.getContent()).getDevice().getId());
        assertEquals("SHIPPED",qr.scan(oldQr.getContent()).getDevice().getStatus());
        assertEquals("IN_USE",qr.scan(newQr.getContent()).getDevice().getStatus());
        assertEquals(oldQr.getContent(),qr.label("DEVICE",old,true).getContent());
        assertEquals(newQr.getContent(),qr.label("DEVICE",next,true).getContent());
    }

    @Test void requestIdConnectsWarrantyDecisionAndLinkedRepairAudit() throws Exception {
        Long device=usedDevice();warrantyDates(device,-1,1);AssetServiceOrderView accepted=warrantyAccepted(device);
        String token=token("SYSTEM_ADMIN");mvc.perform(post("/api/assets/warranties/"+accepted.getId()+"/approve").header("Authorization","Bearer "+token)
                .header("X-Request-Id","p8-warranty").contentType("application/json").content(json.writeValueAsString(action(accepted)))).andExpect(status().isOk());
        assertEquals(2,count("SELECT COUNT(*) FROM sys_operation_log WHERE operator_user_id=? AND request_id='p8-warranty'",operator));
        assertEquals(0,count("SELECT COUNT(*) FROM inv_stock_ledgers WHERE product_id=? AND request_id='p8-warranty'",deviceProduct));
    }

    @Test void changedRequestPayloadAndStaleVersionsCannotReplay() {
        Long device=usedDevice();AssetServiceOrderRequest r=request(device);AssetServiceOrderView draft=service.create("repair",r);
        r.setReason("different");assertThrows(BusinessException.class,()->service.create("repair",r));
        AssetServiceActionRequest a=action(draft);AssetServiceOrderView submitted=service.action("repair",draft.getId(),"submit",a);
        a.setResult("different");assertThrows(BusinessException.class,()->service.action("repair",draft.getId(),"submit",a));
        assertThrows(BusinessException.class,()->perform("repair",draft,"submit"));assertEquals("SUBMITTED",submitted.getStatus());
    }

    private AssetServiceOrderRequest request(Long device) {
        AssetServiceOrderRequest r=new AssetServiceOrderRequest();r.setDeviceId(device);r.setClientRequestId(UUID.randomUUID().toString());r.setReason("设备故障申请");r.setFaultPhenomenon("无法启动");r.setFaultDescription("启动后无法正常工作");return r;
    }
    private AssetServiceOrderRequest exchangeRequest(Long old,Long next) {AssetServiceOrderRequest r=request(old);r.setNewDeviceId(next);r.setReason("更换故障设备");return r;}
    private AssetServiceActionRequest action(AssetServiceOrderView o) {AssetServiceActionRequest r=new AssetServiceActionRequest();r.setVersion(o.getVersion());r.setClientRequestId(UUID.randomUUID().toString());r.setResult("已核实并处理");return r;}
    private AssetServiceOrderView perform(String kind,AssetServiceOrderView o,String action) {return service.action(kind,o.getId(),action,action(o));}
    private AssetServiceOrderView repairing(Long device) {AssetServiceOrderView o=service.create("repair",request(device));o=perform("repair",o,"submit");o=perform("repair",o,"accept");return perform("repair",o,"start");}
    private AssetServiceOrderView warrantyAccepted(Long device) {AssetServiceOrderView o=service.create("warranty",request(device));return perform("warranty",perform("warranty",o,"submit"),"accept");}
    private AssetServiceOrderView approvedExchange(Long old,Long next) {AssetServiceOrderView o=service.create("exchange",exchangeRequest(old,next));return perform("exchange",perform("exchange",o,"submit"),"approve");}
    private Long device(Long product,String status,Long warehouseId) {String code=prefix+UUID.randomUUID().toString().substring(0,8);return id("INSERT INTO asset_devices(device_code,serial_number,product_id,status,department_id,user_id,custodian_id,warehouse_id) VALUES(?,?,?,?,?,?,?,?)",code,code+"SN",product,status,department,"IN_USE".equals(status)?operator:null,"IN_USE".equals(status)?operator:null,warehouseId);}
    private Long usedDevice() {return device(deviceProduct,"IN_USE",null);}
    private Long stockDevice() {
        Long d=device(deviceProduct,"IN_STOCK",null);OpeningBalanceRequest r=new OpeningBalanceRequest();r.setWarehouseId(warehouse);r.setClientRequestId(UUID.randomUUID().toString());
        OpeningBalanceItemRequest item=new OpeningBalanceItemRequest();item.setProductId(deviceProduct);item.setQuantity(BigDecimal.ONE);item.setDeviceIds(List.of(d));r.setItems(List.of(item));openings.complete(openings.create(r).getId());return d;
    }
    private void warrantyDates(Long device,int start,int end) {jdbc.update("UPDATE asset_devices SET warranty_start_at=DATE_ADD(NOW(),INTERVAL ? DAY),warranty_end_at=DATE_ADD(NOW(),INTERVAL ? DAY) WHERE id=?",start,end,device);}
    private String state(Long d){return jdbc.queryForObject("SELECT status FROM asset_devices WHERE id=?",String.class,d);}
    private String code(Long d){return jdbc.queryForObject("SELECT device_code FROM asset_devices WHERE id=?",String.class,d);}
    private String serial(Long d){return jdbc.queryForObject("SELECT serial_number FROM asset_devices WHERE id=?",String.class,d);}
    private Long warehouse(Long d){return jdbc.queryForObject("SELECT warehouse_id FROM asset_devices WHERE id=?",Long.class,d);}
    private Long user(Long d){return jdbc.queryForObject("SELECT user_id FROM asset_devices WHERE id=?",Long.class,d);}
    private Long deviceDepartment(Long d){return jdbc.queryForObject("SELECT department_id FROM asset_devices WHERE id=?",Long.class,d);}
    private int stock(){return jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM inv_stocks WHERE product_id=?",Integer.class,deviceProduct);}
    private int count(String sql,Object... args){return jdbc.queryForObject(sql,Integer.class,args);}
    private void consistency(){assertEquals(0,count("SELECT COUNT(*) FROM inv_stocks s WHERE s.product_id=? AND s.quantity<>(SELECT COUNT(*) FROM asset_devices d WHERE d.product_id=s.product_id AND d.warehouse_id=s.warehouse_id AND d.status='IN_STOCK')",deviceProduct));}
    private Long otherUser(){Long existing=jdbc.query("SELECT id FROM sys_user WHERE username=?",rs->rs.next()?rs.getLong(1):null,prefix+"OTHER");return existing==null?id("INSERT INTO sys_user(username,password_hash,real_name,department_id) VALUES(?,?,?,?)",prefix+"OTHER","unused-test-hash",prefix+"OTHER",department):existing;}
    private String token(String role) {
        current("ALL",department);String secret=UUID.randomUUID().toString();jdbc.update("UPDATE sys_user SET password_hash=? WHERE id=?",encoder.encode(secret),operator);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",operator);jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",operator,role);
        LoginRequest r=new LoginRequest();r.setUsername(prefix);r.setPassword(secret);return auth.login(r).getAccessToken();
    }
    private void current(String scope,Long departmentId) {
        CurrentUserView u=new CurrentUserView();u.setUserId(operator);u.setDataScope(scope);u.setPermissions(jdbc.queryForList("SELECT permission_code FROM sys_permission",String.class));
        if(departmentId!=null){DepartmentBriefView d=new DepartmentBriefView();d.setId(departmentId);u.setDepartment(d);}CurrentUserContext.set(u);
    }
    private Long product(String suffix,String type){return id("INSERT INTO md_products(product_code,product_name,category_id,brand_id,unit_id,product_type) VALUES(?,?,?,?,?,?)",prefix+suffix,prefix+suffix,category,brand,unit,type);}
    private int race(Callable<?> a,Callable<?> b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try {List<Future<Boolean>> results=new ArrayList<>();for(Callable<?> task:List.of(a,b))results.add(pool.submit(()->{current("ALL",department);ready.countDown();start.await();try{task.call();return true;}catch(BusinessException e){return false;}finally{CurrentUserContext.clear();}}));
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();int successes=0;for(Future<Boolean> r:results)if(r.get(30,TimeUnit.SECONDS))successes++;return successes;
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    private Long id(String sql,Object... args){GeneratedKeyHolder key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
}
