package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysJobLog;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * 任务执行日志独立短事务测试（T-ACCESS-007 §8.2）。
 * <p>
 * 验证 {@link JobLogDomainServiceImpl#recordJobLog} 使用 {@code REQUIRES_NEW}
 * 独立短事务（任务调度主流程无外部事务，日志写入独立提交），且方法体不吞异常——
 * REQUIRES_NEW 异常（含代理层 commit 阶段异常）自然传播，由调用方
 * （JobServiceImpl.executeJob finally）统一 try-catch 兜底隔离（评审 P1-3 修复）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class JobLogDomainServiceImplTest {

    @Mock
    private SysJobLogMapper jobLogMapper;

    private JobLogDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobLogDomainServiceImpl(jobLogMapper);
    }

    @Test
    void shouldUseRequiresNewPropagation() throws Exception {
        Method method = JobLogDomainServiceImpl.class.getMethod(
            "recordJobLog", Long.class, Long.class, String.class, String.class, Integer.class, String.class, Integer.class);

        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation());
    }

    @Test
    void shouldInsertJobLogOnSuccess() {
        service.recordJobLog(1L, 42L, "cleanup", "task#cleanup", 1, "Executed successfully", 120);

        verify(jobLogMapper).insert(any(SysJobLog.class));
    }

    @Test
    void shouldPropagateExceptionWhenInsertFails() {
        doThrow(new RuntimeException("db unavailable")).when(jobLogMapper).insert(any(SysJobLog.class));

        // 评审 P1-3 修复：方法体不吞异常，REQUIRES_NEW 写入失败自然传播，
        // 由调用方（JobServiceImpl.executeJob finally）try-catch 兜底隔离。
        assertThrows(RuntimeException.class,
            () -> service.recordJobLog(1L, 42L, "cleanup", "task#cleanup", 0, "boom", 99));
    }
}
