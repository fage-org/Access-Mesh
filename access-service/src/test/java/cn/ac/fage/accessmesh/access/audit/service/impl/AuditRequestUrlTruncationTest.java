package cn.ac.fage.accessmesh.access.audit.service.impl;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService.OperationLogEntry;
import cn.ac.fage.accessmesh.access.audit.service.domain.impl.AuditDomainServiceImpl;
import cn.ac.fage.accessmesh.access.audit.entity.OperationLog;
import cn.ac.fage.accessmesh.access.audit.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.audit.mapper.PermissionChangeLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 审计 request_url 列宽截断回归锁（2026-10-06 逐任务评审 P2）。
 * <p>
 * request_url 列 VARCHAR(256)，而网关拒绝审计链路允许 512 字符入参（网关侧
 * limited(...,512) + GatewayDenialAuditReq @Size 512）——统一落点原只截断
 * targetId/summary/operatorName，request_url 是该表唯一漏防护列：超长路径的
 * 拒绝留痕在 @Async 写入时整条静默丢失（AsyncConfig 仅 log.error），且可被
 * 被审计方用长路径主动规避留痕。旧实现（直写不截断）下本用例必红。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AuditRequestUrlTruncationTest {

    @Mock
    private PermissionChangeLogMapper changeLogMapper;
    @Mock
    private OperationLogMapper operationLogMapper;

    @Test
    @DisplayName("512 字符 request_url 截断至列宽 256 落库（旧实现原样落库必红）")
    void requestUrlTruncatedToColumnWidth() {
        AuditDomainService service = new AuditDomainServiceImpl(changeLogMapper, operationLogMapper);
        String longUrl = "https://gateway.example/api/access/auth/login/" + "a".repeat(400);

        service.asyncRecordLog(new OperationLogEntry(1L, "ACCESS", "LOGIN", "sys_user",
            "1", "login", 1L, "admin", "127.0.0.1", "req-1", longUrl, 200, 10));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getRequestUrl())
            .as("request_url 必须截断至列宽 256，不得原样落库超长值")
            .hasSize(256);
    }
}
