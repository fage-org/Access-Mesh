package cn.ac.fage.accessmesh.access.admin.sync.orchestrator;

import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysSyncTaskMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.sync.SyncSequenceProvider;
import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskEnvelope;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link SyncFullSyncOrchestrator}（S6）：
 * <ul>
 *   <li>已有活跃批次时抛 {@link BizException}（reason 含 FULL_SYNC_BATCH_ACTIVE）</li>
 *   <li>正常启动生成 6 个 phase 至少各 1 条 envelope（OTHER_RESOURCE 暂无生产事实）</li>
 *   <li>ORG_ROLE / USER_ROLE phase 按 roleTypeCode（ORG / POSITION）各生成一条</li>
 *   <li>batchKey 格式 {@code sourceService=admin-service&runId=<uuid>}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SyncFullSyncOrchestratorTest {

    @Mock private SyncTaskDomainService syncTaskDomainService;
    @Mock private SysSyncTaskMapper sysSyncTaskMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysOrgMapper sysOrgMapper;
    @Mock private SysUserOrgMapper sysUserOrgMapper;
    @Mock private SysMenuMapper sysMenuMapper;
    @Mock private SysOrgTreeConfigMapper sysOrgTreeConfigMapper;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;

    private SyncTaskBuilder syncTaskBuilder;
    private SyncFullSyncOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        syncTaskBuilder = new SyncTaskBuilder(new ObjectMapper(), new SyncSequenceProvider());
        orchestrator = new SyncFullSyncOrchestrator(
            syncTaskBuilder, syncTaskDomainService, sysSyncTaskMapper,
            sysUserMapper, sysOrgMapper, sysUserOrgMapper, sysMenuMapper, sysOrgTreeConfigMapper,
            orgTreeConfigDomainService
        );
    }

    /** 帮助方法：让 resolver 把所有 orgIds 都解析到 root="1" */
    private void stubResolveAllToRoot1() {
        when(orgTreeConfigDomainService.resolveTreeRootExternalIds(anyLong(), any()))
            .thenAnswer(inv -> {
                Collection<Long> ids = inv.getArgument(1);
                Map<Long, String> r = new HashMap<>();
                if (ids != null) {
                    for (Long id : ids) {
                        if (id != null) r.put(id, "1");
                    }
                }
                return r;
            });
    }

    private SysUser user(long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setStatus(1);
        u.setName("user-" + id);
        u.setUsername("u" + id);
        return u;
    }

    private SysOrg org(long id, String orgType) {
        SysOrg o = new SysOrg();
        o.setId(id);
        o.setOrgType(orgType);
        o.setName("org-" + id);
        o.setStatus(1);
        return o;
    }

    private SysUserOrg binding(long userId, long orgId) {
        SysUserOrg b = new SysUserOrg();
        b.setUserId(userId);
        b.setOrgId(orgId);
        return b;
    }

    private SysOrgTreeConfig treeConfig(long rootOrgId) {
        SysOrgTreeConfig c = new SysOrgTreeConfig();
        c.setRootOrgId(rootOrgId);
        c.setIsDefault(true);
        return c;
    }

    @Test
    @DisplayName("已有活跃批次时抛 BizException(FULL_SYNC_BATCH_ACTIVE)")
    void shouldThrowBizException_whenActiveBatchExists() {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(eq(1L), anyString()))
            .thenReturn("sourceService=admin-service&runId=existing-id");

        assertThatThrownBy(() -> orchestrator.startFullSyncRun(1L, "admin-service", "manual"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("FULL_SYNC_BATCH_ACTIVE");

        verify(syncTaskDomainService, never()).enqueueAll(anyLong(), any());
    }

    @Test
    @DisplayName("startFullSyncRun 生成 6 个 phase 各至少 1 条 envelope；batchKey 格式正确（OTHER_RESOURCE 暂无生产事实，待 SyncTaskBuilder.otherResourceFullSync 落地后再入队）")
    void shouldGenerateAllPhases_whenStartFullSyncRun() {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(anyLong(), anyString()))
            .thenReturn(null);
        when(sysUserMapper.selectListByQuery(any())).thenReturn(List.of(user(10001L)));
        when(sysOrgMapper.selectListByQuery(any()))
            .thenReturn(List.of(org(2001L, "ORG"), org(3001L, "POSITION")));
        when(sysUserOrgMapper.selectListByQuery(any())).thenReturn(List.of(binding(10001L, 2001L)));
        when(sysMenuMapper.selectAllValid(eq(1L))).thenReturn(List.<SysMenu>of());
        when(sysOrgTreeConfigMapper.selectDefaultConfigs(eq(1L))).thenReturn(List.of(treeConfig(1L)));
        stubResolveAllToRoot1();

        String batchKey = orchestrator.startFullSyncRun(1L, "admin-service", "manual");

        // batchKey 格式
        assertThat(batchKey).startsWith("sourceService=admin-service&runId=");
        assertThat(batchKey.length()).isGreaterThan("sourceService=admin-service&runId=".length());

        // 捕获 enqueueAll 入参
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SyncTaskEnvelope>> captor = ArgumentCaptor.forClass(List.class);
        verify(syncTaskDomainService).enqueueAll(eq(1L), captor.capture());
        List<SyncTaskEnvelope> envelopes = captor.getValue();

        // 当前实施 6 个 phase（OTHER_RESOURCE 暂未生成任务）
        Set<String> phases = new HashSet<>();
        for (SyncTaskEnvelope env : envelopes) {
            phases.add(env.phase());
            // batchKey 透传
            assertThat(env.batchKey()).isEqualTo(batchKey);
        }
        assertThat(phases).contains(
            "USER_SUBJECT", "USER_RESOURCE", "ORG_RESOURCE", "ORG_ROLE",
            "USER_ROLE", "MENU_RESOURCE"
        );
        assertThat(phases).doesNotContain("OTHER_RESOURCE");
    }

    @Test
    @DisplayName("ORG_ROLE phase 同时生成 ORG 类型与 POSITION 类型两条任务")
    void shouldGenerateOrgRoleAndPositionRolePhases() {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(anyLong(), anyString()))
            .thenReturn(null);
        when(sysUserMapper.selectListByQuery(any())).thenReturn(List.<SysUser>of());
        when(sysOrgMapper.selectListByQuery(any()))
            .thenReturn(List.of(org(1L, "ORG"), org(2L, "POSITION")));
        when(sysUserOrgMapper.selectListByQuery(any())).thenReturn(List.<SysUserOrg>of());
        when(sysMenuMapper.selectAllValid(anyLong())).thenReturn(List.<SysMenu>of());
        when(sysOrgTreeConfigMapper.selectDefaultConfigs(anyLong()))
            .thenReturn(List.of(treeConfig(1L)));
        stubResolveAllToRoot1();

        orchestrator.startFullSyncRun(1L, "admin-service", "cron");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SyncTaskEnvelope>> captor = ArgumentCaptor.forClass(List.class);
        verify(syncTaskDomainService).enqueueAll(eq(1L), captor.capture());
        List<SyncTaskEnvelope> envelopes = captor.getValue();

        // ORG_ROLE 应有两条任务（ORG 和 POSITION）
        long orgRoleCount = envelopes.stream()
            .filter(e -> "ORG_ROLE".equals(e.phase()))
            .count();
        assertThat(orgRoleCount).isEqualTo(2);

        // 确认 businessKey 中包含两种 roleTypeCode
        boolean hasOrg = envelopes.stream()
            .filter(e -> "ORG_ROLE".equals(e.phase()))
            .anyMatch(e -> e.businessKey().contains("roleTypeCode=ORG"));
        boolean hasPosition = envelopes.stream()
            .filter(e -> "ORG_ROLE".equals(e.phase()))
            .anyMatch(e -> e.businessKey().contains("roleTypeCode=POSITION"));
        assertThat(hasOrg).isTrue();
        assertThat(hasPosition).isTrue();
    }

    @Test
    @DisplayName("findActiveBatch 返回 mapper 查得的 batchKey")
    void findActiveBatch_returnsMapperResult() {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(eq(1L), anyString()))
            .thenReturn("sourceService=admin-service&runId=abc");

        assertThat(orchestrator.findActiveBatch(1L, "admin-service"))
            .isPresent()
            .contains("sourceService=admin-service&runId=abc");
    }
}
