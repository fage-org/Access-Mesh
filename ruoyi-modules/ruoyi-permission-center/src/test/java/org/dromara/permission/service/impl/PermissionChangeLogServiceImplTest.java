package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.permission.domain.PcPermissionChangeLog;
import org.dromara.permission.domain.bo.ChangeLogQueryBo;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.domain.vo.ChangeLogVo;
import org.dromara.permission.mapper.PcPermissionChangeLogMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionChangeLogServiceImplTest {

    @Mock
    private PcPermissionChangeLogMapper changeLogMapper;

    @InjectMocks
    private PermissionChangeLogServiceImpl service;

    private static final Long TENANT = 1L;

    // ======================== writeChangeLog ========================

    @Test
    void writeChangeLog_normal_insertsLogWithCorrectFields() {
        Long entityId = 100L;
        Long bizDomainId = 10L;
        Object oldSnap = new Object() {
            @Override
            public String toString() {
                return "old";
            }
        };
        Object newSnap = new Object() {
            @Override
            public String toString() {
                return "new";
            }
        };

        when(changeLogMapper.insert(any(PcPermissionChangeLog.class))).thenReturn(1);

        service.writeChangeLog(TENANT, bizDomainId, "user_role", entityId,
            "INSERT", oldSnap, newSnap, "req-001", "API");

        ArgumentCaptor<PcPermissionChangeLog> captor = ArgumentCaptor.forClass(PcPermissionChangeLog.class);
        verify(changeLogMapper).insert(captor.capture());
        PcPermissionChangeLog log = captor.getValue();
        assertEquals(TENANT, log.getTenantId());
        assertEquals(bizDomainId, log.getBizDomainId());
        assertEquals("user_role", log.getEntityType());
        assertEquals(entityId, log.getEntityId());
        assertEquals("INSERT", log.getOperation());
        assertNotNull(log.getOldSnapshot());
        assertNotNull(log.getNewSnapshot());
        assertEquals("req-001", log.getRequestId());
        assertEquals("API", log.getChangeSource());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void writeChangeLog_exceptionSwallowed_noRethrow() {
        doThrow(new RuntimeException("DB error")).when(changeLogMapper).insert(any(PcPermissionChangeLog.class));

        assertDoesNotThrow(() -> service.writeChangeLog(
            TENANT, null, "user_role", 1L, "INSERT", null, null, null, "API"
        ));
    }

    @Test
    void writeChangeLogParam_delegatesToMultiParam() {
        ChangeLogParam param = new ChangeLogParam()
            .setTenantId(TENANT)
            .setBizDomainId(10L)
            .setEntityType("user_role")
            .setEntityId(100L)
            .setOperation("UPDATE")
            .setOldSnapshot("old")
            .setNewSnapshot("new")
            .setRequestId("req-002")
            .setChangeSource("ADMIN");

        when(changeLogMapper.insert(any(PcPermissionChangeLog.class))).thenReturn(1);

        service.writeChangeLog(param);

        ArgumentCaptor<PcPermissionChangeLog> captor = ArgumentCaptor.forClass(PcPermissionChangeLog.class);
        verify(changeLogMapper).insert(captor.capture());
        PcPermissionChangeLog log = captor.getValue();
        assertEquals(TENANT, log.getTenantId());
        assertEquals("user_role", log.getEntityType());
        assertEquals(100L, log.getEntityId());
        assertEquals("UPDATE", log.getOperation());
        assertEquals("ADMIN", log.getChangeSource());
    }

    @Test
    void writeChangeLog_nullSnapshots_storedAsNull() {
        when(changeLogMapper.insert(any(PcPermissionChangeLog.class))).thenReturn(1);

        service.writeChangeLog(TENANT, null, "user_role", 1L, "DELETE", null, null, null, "API");

        ArgumentCaptor<PcPermissionChangeLog> captor = ArgumentCaptor.forClass(PcPermissionChangeLog.class);
        verify(changeLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getOldSnapshot());
        assertNull(captor.getValue().getNewSnapshot());
    }

    // ======================== queryPage ========================

    @Test
    void queryPage_normal_returnsTableDataInfo() {
        ChangeLogQueryBo bo = new ChangeLogQueryBo();
        bo.setTenantId(TENANT);
        PageQuery pageQuery = new PageQuery(10, 1);

        PcPermissionChangeLog record = new PcPermissionChangeLog();
        record.setId(1L);
        record.setTenantId(TENANT);
        record.setEntityType("user_role");
        record.setOperation("INSERT");
        record.setCreatedAt(LocalDateTime.now());

        Page<PcPermissionChangeLog> resultPage = new Page<>(1, 10, 1);
        resultPage.setRecords(List.of(record));

        when(changeLogMapper.selectChangeLogPage(any(Page.class), any(ChangeLogQueryBo.class))).thenReturn(resultPage);

        TableDataInfo<ChangeLogVo> result = service.queryPage(bo, pageQuery);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRows().size());
        assertEquals("user_role", result.getRows().get(0).getEntityType());
    }

    @Test
    void queryPage_nullTenantId_returnsEmptyResult() {
        ChangeLogQueryBo bo = new ChangeLogQueryBo();
        bo.setTenantId(null);
        PageQuery pageQuery = new PageQuery(10, 1);

        TableDataInfo<ChangeLogVo> result = service.queryPage(bo, pageQuery);

        assertNotNull(result);
        assertNull(result.getRows());
        verifyNoInteractions(changeLogMapper);
    }

    @Test
    void queryPage_nullBo_returnsEmptyResult() {
        PageQuery pageQuery = new PageQuery(10, 1);

        TableDataInfo<ChangeLogVo> result = service.queryPage(null, pageQuery);

        assertNotNull(result);
        assertNull(result.getRows());
        verifyNoInteractions(changeLogMapper);
    }
}
