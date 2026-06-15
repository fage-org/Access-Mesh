package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.admin.sync.SyncSequenceProvider;
import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link UserOrgServiceImpl} 在 user-org 增量同步链路上：
 * - assignUserToOrgs / removeUserFromOrg 的 envelope.payload.treeRootExternalId
 *   必须来自 {@link OrgTreeConfigDomainService#resolveTreeRootExternalId}，
 *   严格禁止 fallback "1"。
 */
@ExtendWith(MockitoExtension.class)
class UserOrgServiceImplSyncEnvelopeTest {

    private static final Long TENANT_ID = 1L;

    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private SyncTaskDomainService syncTaskDomainService;

    private SyncTaskBuilder syncTaskBuilder;
    private UserOrgServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        syncTaskBuilder = new SyncTaskBuilder(objectMapper, new SyncSequenceProvider());
        service = new UserOrgServiceImpl(
            userOrgDomainService, orgTreeConfigDomainService, orgDomainService,
            permissionValidator, syncTaskDomainService, syncTaskBuilder
        );
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("assignUserToOrgs 入队的 envelope payload.treeRootExternalId 来自 resolver（=\"999\"），不再硬编码 \"1\"")
    void assignUserToOrgs_usesResolvedTreeRootExternalId() throws Exception {
        // 不存在已有关联，且 resolver 返回 "999"
        when(userOrgDomainService.findByUserId(eq(TENANT_ID), anyLong())).thenReturn(List.of());
        when(orgTreeConfigDomainService.findDefaultConfigs(eq(TENANT_ID)))
            .thenReturn(List.<SysOrgTreeConfig>of());
        // 增量必须按 orgId → orgType 解析 roleTypeCode
        SysOrg org500 = new SysOrg();
        org500.setId(500L);
        org500.setOrgType("ORG");
        when(orgDomainService.batchSelectValidByIdsMap(eq(TENANT_ID), eq(Set.of(500L))))
            .thenReturn(Map.of(500L, org500));
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(eq(TENANT_ID), eq(500L)))
            .thenReturn("999");

        UserOrgAssignReq req = new UserOrgAssignReq(42L, List.of(500L), 500L);

        service.assignUserToOrgs(req);

        ArgumentCaptor<SyncTaskEnvelope> captor = ArgumentCaptor.forClass(SyncTaskEnvelope.class);
        verify(syncTaskDomainService).enqueue(eq(TENANT_ID), captor.capture());
        SyncTaskEnvelope env = captor.getValue();

        JsonNode payload = objectMapper.readTree(env.payload());
        assertThat(payload.get("treeRootExternalId").asText()).isEqualTo("999");
        assertThat(payload.get("roleTypeCode").asText()).isEqualTo("ORG");
    }

    @Test
    @DisplayName("removeUserFromOrg 入队的 envelope payload.treeRootExternalId 来自 resolver（=\"777\"），不再硬编码 \"1\"")
    void removeUserFromOrg_usesResolvedTreeRootExternalId() throws Exception {
        SysOrg org700 = new SysOrg();
        org700.setId(700L);
        org700.setOrgType("ORG");
        when(orgDomainService.selectValidById(eq(TENANT_ID), eq(700L))).thenReturn(org700);
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(eq(TENANT_ID), eq(700L)))
            .thenReturn("777");

        service.removeUserFromOrg(42L, 700L);

        verify(userOrgDomainService).deleteByUserIdAndOrgId(eq(TENANT_ID), eq(42L), eq(700L));

        ArgumentCaptor<SyncTaskEnvelope> captor = ArgumentCaptor.forClass(SyncTaskEnvelope.class);
        verify(syncTaskDomainService).enqueue(eq(TENANT_ID), captor.capture());
        SyncTaskEnvelope env = captor.getValue();

        JsonNode payload = objectMapper.readTree(env.payload());
        assertThat(payload.get("treeRootExternalId").asText()).isEqualTo("777");
        assertThat(payload.get("roleTypeCode").asText()).isEqualTo("ORG");
    }

    @Test
    @DisplayName("POSITION 类型 org 的 user-org BIND，payload.roleTypeCode 必须等于 POSITION，business_key 也使用 POSITION")
    void assignUserToOrgs_positionOrg_yieldsPositionRoleType() throws Exception {
        when(userOrgDomainService.findByUserId(eq(TENANT_ID), anyLong())).thenReturn(List.of());
        when(orgTreeConfigDomainService.findDefaultConfigs(eq(TENANT_ID)))
            .thenReturn(List.<SysOrgTreeConfig>of());
        SysOrg posOrg = new SysOrg();
        posOrg.setId(800L);
        posOrg.setOrgType("POSITION");
        when(orgDomainService.batchSelectValidByIdsMap(eq(TENANT_ID), eq(Set.of(800L))))
            .thenReturn(Map.of(800L, posOrg));
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(eq(TENANT_ID), eq(800L)))
            .thenReturn("100");

        service.assignUserToOrgs(new UserOrgAssignReq(42L, List.of(800L), 800L));

        ArgumentCaptor<SyncTaskEnvelope> captor = ArgumentCaptor.forClass(SyncTaskEnvelope.class);
        verify(syncTaskDomainService).enqueue(eq(TENANT_ID), captor.capture());
        SyncTaskEnvelope env = captor.getValue();
        JsonNode payload = objectMapper.readTree(env.payload());
        assertThat(payload.get("roleTypeCode").asText()).isEqualTo("POSITION");
        assertThat(payload.get("relationKey").asText()).isEqualTo("ORG:800");
        // business_key 必须使用 POSITION，否则与 abstract_role(POSITION,...) 创建路径错位
        assertThat(env.businessKey()).contains("POSITION");
    }
}
