package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.access.admin.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService.LoginLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 登录日志独立短事务测试（T-ACCESS-007 §8.2）。
 * <p>
 * 验证 {@link LoginLogDomainServiceImpl#recordLoginLog} 使用 {@code REQUIRES_NEW}
 * 独立短事务（登录主流程回滚不影响日志落库），且方法体不吞异常——
 * REQUIRES_NEW 异常（含代理层 commit 阶段异常）自然传播，由调用方
 * （AuthServiceImpl.safeRecordLoginLog）统一 try-catch 兜底隔离。
 * 登录日志完整回填 userId/loginType/IP/User-Agent（T-ACCESS-007 评审修复）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class LoginLogDomainServiceImplTest {

    @Mock
    private SysLoginLogMapper loginLogMapper;

    private LoginLogDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoginLogDomainServiceImpl(loginLogMapper);
    }

    @Test
    void shouldUseRequiresNewPropagation() throws Exception {
        Method method = LoginLogDomainServiceImpl.class.getMethod(
            "recordLoginLog", LoginLogEntry.class);

        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation());
    }

    @Test
    void shouldInsertLoginLogWithFullFieldsOnSuccess() {
        service.recordLoginLog(new LoginLogEntry(
            1L, 100L, "alice", "PASSWORD", "admin-web",
            "127.0.0.1", "Mozilla/5.0", 1, null));

        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        SysLoginLog log = captor.getValue();
        assertEquals(1L, log.getTenantId());
        assertEquals(100L, log.getUserId());
        assertEquals("alice", log.getUsername());
        assertEquals("PASSWORD", log.getLoginType());
        assertEquals("admin-web", log.getClientId());
        assertEquals("127.0.0.1", log.getIpAddress());
        assertEquals("Mozilla/5.0", log.getUserAgent());
        assertEquals(1, log.getStatus());
        assertEquals(null, log.getFailReason());
        assertNotNull(log.getLoginAt());
    }

    @Test
    void shouldInsertLoginLogOnFailureWithoutUser() {
        service.recordLoginLog(new LoginLogEntry(
            1L, null, "unknown-user", "PASSWORD", "admin-web",
            null, null, 0, "用户不存在"));

        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        SysLoginLog log = captor.getValue();
        assertEquals(null, log.getUserId());
        assertEquals("unknown-user", log.getUsername());
        assertEquals(0, log.getStatus());
        assertEquals("用户不存在", log.getFailReason());
    }

    @Test
    void shouldPropagateExceptionWhenInsertFails() {
        doThrow(new RuntimeException("db unavailable")).when(loginLogMapper).insert(any(SysLoginLog.class));

        // 方法体不吞异常，REQUIRES_NEW 写入失败自然传播，
        // 由调用方（AuthServiceImpl.safeRecordLoginLog）try-catch 兜底隔离。
        assertThrows(RuntimeException.class,
            () -> service.recordLoginLog(new LoginLogEntry(
                1L, 100L, "alice", "PASSWORD", "admin-web",
                "127.0.0.1", "Mozilla/5.0", 0, "bad credentials")));
    }
}
