package cn.ac.fage.accessmesh.admin.sync.orchestrator;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysSyncTaskMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.admin.sync.SyncSequenceProvider;
import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link SyncFullSyncOrchestrator} PHASE 5 USER_ROLE 阶段：
 * 必须按 binding 对应的 orgType（ORG / POSITION）分组发出独立 envelope，
 * 每个 envelope 内 item.roleTypeCode 与 scope.roleTypeCode 严格相等。
 */
@ExtendWith(MockitoExtension.class)
class SyncFullSyncOrchestratorSplitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        syncTaskBuilder = new SyncTaskBuilder(objectMapper, new SyncSequenceProvider());
        orchestrator = new SyncFullSyncOrchestrator(
            syncTaskBuilder, syncTaskDomainService, sysSyncTaskMapper,
            sysUserMapper, sysOrgMapper, sysUserOrgMapper, sysMenuMapper, sysOrgTreeConfigMapper,
            orgTreeConfigDomainService
        );
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
    @DisplayName("USER_ROLE phase: bindings 按 orgType 分组，ORG envelope 与 POSITION envelope 内 item.roleTypeCode 严格隔离")
    void userRolePhase_splitsBindingsByOrgType() throws Exception {
        // 2 个 ORG 类型 org (id=10, 11) + 2 个 POSITION 类型 org (id=20, 21)
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(anyLong(), anyString()))
            .thenReturn(null);
        when(sysUserMapper.selectListByQuery(any())).thenReturn(List.<SysUser>of());
        when(sysOrgMapper.selectListByQuery(any())).thenReturn(List.of(
            org(10L, "ORG"),
            org(11L, "ORG"),
            org(20L, "POSITION"),
            org(21L, "POSITION")
        ));
        // 4 条 binding：用户 1->10(ORG), 用户 2->20(POSITION), 用户 3->11(ORG), 用户 4->21(POSITION)
        when(sysUserOrgMapper.selectListByQuery(any())).thenReturn(List.of(
            binding(1L, 10L),
            binding(2L, 20L),
            binding(3L, 11L),
            binding(4L, 21L)
        ));
        when(sysMenuMapper.selectAllValid(anyLong())).thenReturn(List.<SysMenu>of());
        when(sysOrgTreeConfigMapper.selectDefaultConfigs(anyLong()))
            .thenReturn(List.of(treeConfig(1L)));
        // 全部 4 个 org 都解析到同一棵树 root=1
        stubResolveAll("1");

        orchestrator.startFullSyncRun(1L, "admin-service", "manual");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SyncTaskEnvelope>> captor = ArgumentCaptor.forClass(List.class);
        verify(syncTaskDomainService).enqueueAll(eq(1L), captor.capture());
        List<SyncTaskEnvelope> envelopes = captor.getValue();

        // 收集所有 PERM_USER_ROLE_SYNC envelope（PHASE 5）
        List<SyncTaskEnvelope> userRoleEnvelopes = new ArrayList<>();
        for (SyncTaskEnvelope env : envelopes) {
            if (SyncTaskBuilder.ACTION_USER_ROLE_SYNC.equals(env.syncAction())
                && "USER_ROLE".equals(env.phase())) {
                userRoleEnvelopes.add(env);
            }
        }
        // 至少 2 个 envelope（ORG / POSITION 各一）
        assertThat(userRoleEnvelopes).hasSizeGreaterThanOrEqualTo(2);

        // 按 scope.roleTypeCode 分组校验
        SyncTaskEnvelope orgEnvelope = null;
        SyncTaskEnvelope positionEnvelope = null;
        for (SyncTaskEnvelope env : userRoleEnvelopes) {
            JsonNode root = objectMapper.readTree(env.payload());
            String rt = root.get("scope").get("roleTypeCode").asText();
            if ("ORG".equals(rt)) orgEnvelope = env;
            else if ("POSITION".equals(rt)) positionEnvelope = env;
        }
        assertThat(orgEnvelope).as("ORG envelope must exist").isNotNull();
        assertThat(positionEnvelope).as("POSITION envelope must exist").isNotNull();

        // ORG envelope 仅含用户 1/3 (orgId 10/11)
        JsonNode orgItems = objectMapper.readTree(orgEnvelope.payload()).get("items");
        Set<String> orgUserIds = new HashSet<>();
        for (JsonNode item : orgItems) {
            assertThat(item.get("roleTypeCode").asText()).isEqualTo("ORG");
            orgUserIds.add(item.get("subjectExternalId").asText());
        }
        assertThat(orgUserIds).containsExactlyInAnyOrder("1", "3");

        // POSITION envelope 仅含用户 2/4 (orgId 20/21)
        JsonNode positionItems = objectMapper.readTree(positionEnvelope.payload()).get("items");
        Set<String> positionUserIds = new HashSet<>();
        for (JsonNode item : positionItems) {
            assertThat(item.get("roleTypeCode").asText()).isEqualTo("POSITION");
            positionUserIds.add(item.get("subjectExternalId").asText());
        }
        assertThat(positionUserIds).containsExactlyInAnyOrder("2", "4");
    }

    @Test
    @DisplayName("无默认组织树时 startFullSyncRun 抛 BizException(FULL_SYNC_NO_DEFAULT_TREE)，禁止 fallback \"1\"")
    void startFullSyncRun_throws_whenNoDefaultTree() {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(anyLong(), anyString()))
            .thenReturn(null);
        when(sysOrgTreeConfigMapper.selectDefaultConfigs(anyLong()))
            .thenReturn(List.<SysOrgTreeConfig>of());

        assertThatThrownBy(() -> orchestrator.startFullSyncRun(1L, "admin-service", "manual"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("FULL_SYNC_NO_DEFAULT_TREE");

        verify(syncTaskDomainService, never()).enqueueAll(eq(1L), any());
    }

    @Test
    @DisplayName("USER_ROLE phase: 2 棵树 × 2 orgType → 4 个 envelope，每桶 (treeRoot, roleTypeCode) 唯一")
    void userRolePhase_splitsByTreeAndRoleType() throws Exception {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(anyLong(), anyString()))
            .thenReturn(null);
        when(sysUserMapper.selectListByQuery(any())).thenReturn(List.<SysUser>of());
        // 4 个 SysOrg：(orgId=10 ORG@root100), (orgId=11 POSITION@root100),
        //              (orgId=20 ORG@root200), (orgId=21 POSITION@root200)
        when(sysOrgMapper.selectListByQuery(any())).thenReturn(List.of(
            org(10L, "ORG"),
            org(11L, "POSITION"),
            org(20L, "ORG"),
            org(21L, "POSITION")
        ));
        when(sysUserOrgMapper.selectListByQuery(any())).thenReturn(List.of(
            binding(1L, 10L), // root100 / ORG
            binding(2L, 11L), // root100 / POSITION
            binding(3L, 20L), // root200 / ORG
            binding(4L, 21L)  // root200 / POSITION
        ));
        when(sysMenuMapper.selectAllValid(anyLong())).thenReturn(List.<SysMenu>of());
        when(sysOrgTreeConfigMapper.selectDefaultConfigs(anyLong()))
            .thenReturn(List.of(treeConfig(100L), treeConfig(200L)));
        // resolver mock: 10/11 -> "100", 20/21 -> "200"
        Map<Long, String> resolveMap = new HashMap<>();
        resolveMap.put(10L, "100");
        resolveMap.put(11L, "100");
        resolveMap.put(20L, "200");
        resolveMap.put(21L, "200");
        when(orgTreeConfigDomainService.resolveTreeRootExternalIds(eq(1L), any()))
            .thenReturn(resolveMap);

        orchestrator.startFullSyncRun(1L, "admin-service", "manual");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SyncTaskEnvelope>> captor = ArgumentCaptor.forClass(List.class);
        verify(syncTaskDomainService).enqueueAll(eq(1L), captor.capture());
        List<SyncTaskEnvelope> envelopes = captor.getValue();

        List<SyncTaskEnvelope> userRoleEnvelopes = new ArrayList<>();
        for (SyncTaskEnvelope env : envelopes) {
            if (SyncTaskBuilder.ACTION_USER_ROLE_SYNC.equals(env.syncAction())
                && "USER_ROLE".equals(env.phase())) {
                userRoleEnvelopes.add(env);
            }
        }
        // 严格 4 个桶
        assertThat(userRoleEnvelopes).hasSize(4);

        // 收集 (treeRoot, roleTypeCode) -> userId set
        Map<String, Set<String>> bucketUsers = new LinkedHashMap<>();
        for (SyncTaskEnvelope env : userRoleEnvelopes) {
            JsonNode root = objectMapper.readTree(env.payload());
            String tree = root.get("scope").get("treeRootExternalId").asText();
            String rt = root.get("scope").get("roleTypeCode").asText();
            String key = tree + "|" + rt;
            Set<String> users = new HashSet<>();
            for (JsonNode item : root.get("items")) {
                // 校验 item.roleTypeCode 与 scope.roleTypeCode 严格一致
                assertThat(item.get("roleTypeCode").asText()).isEqualTo(rt);
                users.add(item.get("subjectExternalId").asText());
            }
            bucketUsers.put(key, users);
        }
        assertThat(bucketUsers.get("100|ORG")).containsExactly("1");
        assertThat(bucketUsers.get("100|POSITION")).containsExactly("2");
        assertThat(bucketUsers.get("200|ORG")).containsExactly("3");
        assertThat(bucketUsers.get("200|POSITION")).containsExactly("4");
    }

    @Test
    @DisplayName("ORG_ROLE phase: 2 棵树 × 2 orgType → 4 个 envelope，每桶 (treeRoot, roleTypeCode) 唯一")
    void orgRolePhase_splitsByTreeAndRoleType() throws Exception {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(anyLong(), anyString()))
            .thenReturn(null);
        when(sysUserMapper.selectListByQuery(any())).thenReturn(List.<SysUser>of());
        when(sysOrgMapper.selectListByQuery(any())).thenReturn(List.of(
            org(10L, "ORG"),
            org(11L, "POSITION"),
            org(20L, "ORG"),
            org(21L, "POSITION")
        ));
        when(sysUserOrgMapper.selectListByQuery(any())).thenReturn(List.<SysUserOrg>of());
        when(sysMenuMapper.selectAllValid(anyLong())).thenReturn(List.<SysMenu>of());
        when(sysOrgTreeConfigMapper.selectDefaultConfigs(anyLong()))
            .thenReturn(List.of(treeConfig(100L), treeConfig(200L)));
        Map<Long, String> resolveMap = new HashMap<>();
        resolveMap.put(10L, "100");
        resolveMap.put(11L, "100");
        resolveMap.put(20L, "200");
        resolveMap.put(21L, "200");
        when(orgTreeConfigDomainService.resolveTreeRootExternalIds(eq(1L), any()))
            .thenReturn(resolveMap);

        orchestrator.startFullSyncRun(1L, "admin-service", "manual");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SyncTaskEnvelope>> captor = ArgumentCaptor.forClass(List.class);
        verify(syncTaskDomainService).enqueueAll(eq(1L), captor.capture());
        List<SyncTaskEnvelope> envelopes = captor.getValue();

        List<SyncTaskEnvelope> orgRoleEnvelopes = new ArrayList<>();
        for (SyncTaskEnvelope env : envelopes) {
            if (SyncTaskBuilder.ACTION_ABSTRACT_ROLE_SYNC.equals(env.syncAction())
                && "ORG_ROLE".equals(env.phase())) {
                orgRoleEnvelopes.add(env);
            }
        }
        assertThat(orgRoleEnvelopes).hasSize(4);

        Map<String, Set<String>> bucketOrgIds = new LinkedHashMap<>();
        for (SyncTaskEnvelope env : orgRoleEnvelopes) {
            JsonNode root = objectMapper.readTree(env.payload());
            String tree = root.get("scope").get("treeRootExternalId").asText();
            String rt = root.get("scope").get("roleTypeCode").asText();
            String key = tree + "|" + rt;
            Set<String> ids = new HashSet<>();
            for (JsonNode item : root.get("items")) {
                ids.add(item.get("roleExternalId").asText());
            }
            bucketOrgIds.put(key, ids);
        }
        assertThat(bucketOrgIds.get("100|ORG")).containsExactly("10");
        assertThat(bucketOrgIds.get("100|POSITION")).containsExactly("11");
        assertThat(bucketOrgIds.get("200|ORG")).containsExactly("20");
        assertThat(bucketOrgIds.get("200|POSITION")).containsExactly("21");
    }

    @Test
    @DisplayName("binding.orgId 在 allOrgs 中找不到 → 抛 BizException(ORG_TREE_ROOT_NOT_RESOLVED) 且 enqueueAll 不被调用")
    void startFullSyncRun_throws_whenBindingPointsToInactiveOrg() {
        when(sysSyncTaskMapper.selectActiveBatchByTenantSource(anyLong(), anyString()))
            .thenReturn(null);
        when(sysUserMapper.selectListByQuery(any())).thenReturn(List.<SysUser>of());
        // allOrgs 仅含 10（ORG@root100）
        when(sysOrgMapper.selectListByQuery(any())).thenReturn(List.of(org(10L, "ORG")));
        // binding 含一个 orphan orgId=999（已被软删但 binding 残留）
        when(sysUserOrgMapper.selectListByQuery(any())).thenReturn(List.of(
            binding(1L, 10L),
            binding(2L, 999L)
        ));
        lenient().when(sysMenuMapper.selectAllValid(anyLong())).thenReturn(List.<SysMenu>of());
        when(sysOrgTreeConfigMapper.selectDefaultConfigs(anyLong()))
            .thenReturn(List.of(treeConfig(100L)));
        // resolver 给 10 -> "100", 999 -> "100"（modeling: org 999 已软删，但 binding 残留导致 resolver 仍解析祖先链）
        // 但 allOrgs 中无 999 → orgTypeByOrgId.get(999) == null → orchestrator 抛 BizException
        Map<Long, String> resolveMap = new HashMap<>();
        resolveMap.put(10L, "100");
        resolveMap.put(999L, "100");
        when(orgTreeConfigDomainService.resolveTreeRootExternalIds(eq(1L), any()))
            .thenReturn(resolveMap);

        assertThatThrownBy(() -> orchestrator.startFullSyncRun(1L, "admin-service", "manual"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("FULL_SYNC_BINDING_ORG_INACTIVE")
            .hasMessageContaining("999");

        verify(syncTaskDomainService, never()).enqueueAll(eq(1L), any());
    }

    /** 帮助方法：按 allOrgs ∪ bindings.orgId 解析全部到指定 root */
    private void stubResolveAll(String treeRoot) {
        when(orgTreeConfigDomainService.resolveTreeRootExternalIds(anyLong(), any()))
            .thenAnswer(inv -> {
                Collection<Long> ids = inv.getArgument(1);
                Map<Long, String> r = new HashMap<>();
                if (ids != null) {
                    for (Long id : ids) {
                        if (id != null) r.put(id, treeRoot);
                    }
                }
                return r;
            });
    }
}
