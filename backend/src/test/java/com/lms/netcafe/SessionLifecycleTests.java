package com.lms.netcafe;

import com.lms.netcafe.common.security.AuthenticatedUser;
import com.lms.netcafe.module.billing.service.MachineSessionService;
import com.lms.netcafe.module.billing.service.MachineSessionService.StartSessionRequest;
import com.lms.netcafe.module.billing.service.SessionBilling;
import com.lms.netcafe.module.face.service.FaceRecognitionService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.lms.netcafe.module.device.controller.MaintenanceController;
import com.lms.netcafe.module.device.controller.DeviceController;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
class SessionLifecycleTests {
    @Autowired MachineSessionService sessions;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired MaintenanceController maintenance;
    @Autowired DeviceController devices;
    @MockBean FaceRecognitionService faces;
    AuthenticatedUser operator = new AuthenticatedUser(99L, 1L, "cashier", "前台", "ENABLED", List.of(), List.of());

    @BeforeEach void seed() {
        for (String table : List.of("repair_record", "member_account_flow", "consume_record", "session_billing_snapshot", "machine_session", "device_fault", "billing_rule", "device_info", "member_account", "member_info")) db.update("DELETE FROM " + table);
        db.update("INSERT INTO member_info (id,member_no,name,status,deleted) VALUES (1,'M1','会员1','ACTIVE',0),(2,'M2','会员2','ACTIVE',0)");
        db.update("INSERT INTO member_account (member_id,balance) VALUES (1,100),(2,100)");
        db.update("INSERT INTO device_info VALUES (1,'PC1','A1','IDLE',0,10,'大厅'),(2,'PC2','A2','IDLE',0,10,'大厅')");
        db.update("INSERT INTO billing_rule (id,price_per_hour,min_minutes,billing_unit_minutes,low_balance_threshold,status) VALUES (1,10,15,15,10,'ENABLED')");
    }
    long start(long member, long device) {
        return ((Number) sessions.start(operator, new StartSessionRequest(member, device, 1L, false)).data().get("sessionId")).longValue();
    }
    int count(String table) { return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void completeLifecycleUsesPriceSnapshotAndDebitsExactlyOnce() {
        long id = start(1, 1);
        db.update("UPDATE billing_rule SET price_per_hour = 100, status='DISABLED' WHERE id=1");
        assertThat(sessions.preview(id).data().get("amount")).isEqualTo(new BigDecimal("2.50"));
        assertThat(sessions.running().data().get(0).get("estimatedAmount")).isEqualTo(new BigDecimal("2.50"));
        assertThat(sessions.settle(id, operator).data().get("balance")).isEqualTo(new BigDecimal("97.50"));
        assertThatThrownBy(() -> sessions.settle(id, operator)).isInstanceOf(ResponseStatusException.class);
        assertThat(count("consume_record")).isEqualTo(1);
        assertThat(count("member_account_flow")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM device_info WHERE id=1", String.class)).isEqualTo("IDLE");
    }
    @Test void rejectsInsufficientBalanceDisabledRulesMissingMembersAndForgedFaceFlag() {
        db.update("UPDATE member_account SET balance=0 WHERE member_id=1");
        assertThatThrownBy(() -> start(1,1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> start(999,1)).isInstanceOf(ResponseStatusException.class);
        db.update("UPDATE billing_rule SET status='DISABLED'");
        assertThatThrownBy(() -> start(2,1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sessions.start(operator, new StartSessionRequest(2L,1L,1L,true))).isInstanceOf(ResponseStatusException.class);
        assertThat(count("machine_session")).isZero();
    }
    @Test void rejectsDuplicateMemberAndDuplicateDevice() {
        start(1,1);
        assertThatThrownBy(() -> start(1,2)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> start(2,1)).isInstanceOf(ResponseStatusException.class);
        assertThat(count("machine_session")).isEqualTo(1);
    }
    @Test void rejectsUnderpricedRoomAndInconsistentIdleDevice() {
        db.update("UPDATE device_info SET hourly_rate_hint=78 WHERE id=1");
        assertThatThrownBy(() -> start(1,1)).isInstanceOf(ResponseStatusException.class);
        db.update("UPDATE device_info SET hourly_rate_hint=10 WHERE id=1");
        start(1,1);
        db.update("UPDATE device_info SET status='IDLE' WHERE id=1");
        assertThatThrownBy(() -> start(2,1)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void shortageRollsBackSettlementAndPreservesFault() {
        long id=start(1,1);
        db.update("UPDATE member_account SET balance=0 WHERE member_id=1");
        assertThatThrownBy(() -> sessions.settle(id,operator)).isInstanceOf(ResponseStatusException.class);
        assertThat(count("consume_record")).isZero();
        assertThat(db.queryForObject("SELECT status FROM machine_session WHERE id=?",String.class,id)).isEqualTo("RUNNING");
        db.update("UPDATE member_account SET balance=100 WHERE member_id=1");
        db.update("INSERT INTO device_fault (device_id,status) VALUES (1,'OPEN')");
        sessions.settle(id,operator);
        assertThat(db.queryForObject("SELECT status FROM device_info WHERE id=1",String.class)).isEqualTo("FAULT");
        assertThatThrownBy(() -> start(2,1)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void faceFailureCreatesNoSessionAndSuccessfulFaceStartIsAudited() {
        MockMultipartFile photo=new MockMultipartFile("image","capture.jpg","image/jpeg",new byte[]{1});
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"不匹配"))
                .when(faces).verifyMember(1L,1L,photo);
        assertThatThrownBy(() -> sessions.startWithFace(operator,1L,1L,1L,photo)).isInstanceOf(ResponseStatusException.class);
        assertThat(count("machine_session")).isZero();
        reset(faces);
        sessions.startWithFace(operator,1L,1L,1L,photo);
        verify(faces).verifyMember(1L,1L,photo);
        assertThat(db.queryForObject("SELECT auth_method FROM session_billing_snapshot",String.class)).isEqualTo("FACE");
    }
    @Test void concurrentRequestsCannotOccupyTheSameSeat() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch gate=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=List.of(pool.submit(() -> attempt(1,gate)),pool.submit(() -> attempt(2,gate)));
            gate.countDown();
            int success=0;
            for(Future<Boolean> result:results) if(result.get(10,TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
            assertThat(count("machine_session")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    boolean attempt(long member, CountDownLatch gate) throws InterruptedException {
        gate.await();
        try { start(member,1); return true; } catch(ResponseStatusException ex) { return false; }
    }
    @Test void timestampsAndMinuteBoundariesAreCompatible() {
        LocalDateTime start=LocalDateTime.of(2026,10,27,10,0);
        assertThat(SessionBilling.dateTime(start)).isEqualTo(start);
        assertThat(SessionBilling.dateTime(Timestamp.valueOf(start))).isEqualTo(start);
        assertThat(SessionBilling.minutes(start,start.plusMinutes(15).plusSeconds(1))).isEqualTo(16);
        assertThat(SessionBilling.amount(new BigDecimal("10"),15,15,16)).isEqualTo(new BigDecimal("5.00"));
    }

    @Test void portalUsesAuthenticatedMemberAndRejectsOtherMembersSessions() throws Exception {
        var account = authentication(new UsernamePasswordAuthenticationToken(operator, null,
                List.of(new SimpleGrantedAuthority("portal:devices"))));
        mvc.perform(post("/api/v1/portal/sessions/start").with(account).contentType("application/json")
                .content("{\"memberId\":2,\"deviceId\":1,\"billingRuleId\":1}"))
                .andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT member_id FROM machine_session",Long.class)).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT auth_method FROM session_billing_snapshot",String.class)).isEqualTo("ACCOUNT");
        long other = start(2,2);
        mvc.perform(post("/api/v1/portal/sessions/"+other+"/settle").with(account)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/portal/sessions/"+other+"/preview").with(account)).andExpect(status().isNotFound());
        long own=db.queryForObject("SELECT id FROM machine_session WHERE member_id=1",Long.class);
        mvc.perform(post("/api/v1/portal/sessions/"+own+"/settle").with(account)).andExpect(status().isOk());
    }
    @Test void viewPermissionCannotStartOrSettleSessions() throws Exception {
        var viewer = authentication(new UsernamePasswordAuthenticationToken(operator,null,
                List.of(new SimpleGrantedAuthority("session:view"))));
        mvc.perform(post("/api/v1/sessions/start").with(viewer).contentType("application/json")
                .content("{\"memberId\":1,\"deviceId\":1,\"billingRuleId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sessions/1/settle").with(viewer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sessions/start").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void repairRestoresRunningStateAndRejectsDuplicateRepair() {
        start(1,1);
        devices.createFault(1L,operator,new DeviceController.FaultRequest("HEADSET","耳机无声"));
        long fault=db.queryForObject("SELECT MAX(id) FROM device_fault",Long.class);
        maintenance.resolve(fault,operator,new MaintenanceController.ResolveRequest("已更换耳机"));
        assertThat(db.queryForObject("SELECT status FROM device_info WHERE id=1",String.class)).isEqualTo("IN_USE");
        assertThatThrownBy(() -> maintenance.resolve(fault,operator,new MaintenanceController.ResolveRequest("重复")))
                .isInstanceOf(ResponseStatusException.class);
    }
    @Test void diagnosticsFindsStaleSessionsAndOrphanedOccupancy() {
        start(1,1);
        db.update("UPDATE machine_session SET start_at=?",Timestamp.valueOf(LocalDateTime.now().minusDays(2)));
        db.update("UPDATE device_info SET status='IN_USE' WHERE id=2");
        assertThat((List<?>)sessions.diagnostics().data().get("staleSessions")).hasSize(1);
        assertThat((List<?>)sessions.diagnostics().data().get("inconsistentDevices")).hasSize(1);
    }
}
