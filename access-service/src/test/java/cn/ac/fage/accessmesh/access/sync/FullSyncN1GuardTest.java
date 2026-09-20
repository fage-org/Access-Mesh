package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.access.sync.dto.AbstractUserFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.AbstractUserSyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.AbstractUserSyncScope;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.user.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncScope;
import cn.ac.fage.accessmesh.access.user.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.sync.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.role.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.role.service.impl.UserRoleSyncAppServiceImpl;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.sync.SyncAuthVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import cn.ac.fage.accessmesh.access.user.service.impl.AbstractUserSyncAppServiceImpl;
import cn.ac.fage.accessmesh.access.resource.service.impl.ResourceEntitySyncAppServiceImpl;

/**
 * P7 防回归测试：full-sync 必须批量解析 + 批量写，禁止 N+1。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@link AbstractUserSyncAppServiceImpl#fullSync}：100 个 items 下，
 *       {@code abstractUserMapper.selectByTypeAndExternalId/selectByTypeAndExternalIds} 等查询调用次数 &lt; 10，
 *       {@code insert/update} 等写入与单条 select 调用次数 &lt; 5（即批量化）；</li>
 *   <li>{@link ResourceEntitySyncAppServiceImpl#fullSync}：同上，
 *       {@code resourceEntityMapper.selectByTypeCodeAndCodeType/selectByTypeAndCodesAndCodeTypes} 必须批量；</li>
 * </ul>
 *
 * <p>断言指标说明：
 * <ul>
 *   <li>{@code <10 select}：批量解析阶段 B 一次性命中所有键，禁止循环单条 select；</li>
 *   <li>{@code <5 insert/update batch-write}：N=100 items 下若每条都触发单条
 *       {@link AbstractUserMapper#insert} 单调用本身可接受（MyBatis-Flex 主键回填要求），
 *       但额外的 {@code selectByTypeAndExternalId/softDeleteBatch} 单条调用必须 &lt; 5。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FullSyncN1GuardTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final int ITEM_COUNT = 100;

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private SyncMetadataMapper syncMetadataMapper;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private AbstractUserMapper abstractUserMapper;
    @Mock
    private ResourceEntityMapper resourceEntityMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private cn.ac.fage.accessmesh.access.sync.guard.SyncTypeGuard syncTypeGuard;
    @Mock
    private cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    @BeforeEach
    void setUp() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
        // 公共 stub：listScopeForFullSync 返回空（防止 deactivate 路径影响计数）
        lenient().when(syncMetadataDomainService.listScopeForFullSync(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        // 公共 stub：类型白名单放行（本测试聚焦 N+1 批量化，不测白名单）
        lenient().when(syncTypeGuard.validate(anyLong(), anyString(), any()))
                .thenReturn(true);
        // T-PERM-052：resource-entity 通道类型门禁默认放行（SYNC+来源匹配+服务注册启用）
        lenient().when(resourceTypeOwnershipGuard.isSyncEntranceAllowed(anyLong(), anyString(), anyString()))
                .thenReturn(true);
    }

    @Test
    void abstractUserFullSync_shouldUseBatchedSelectsAndWrites_for100Items() {
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "user_type", "USER")).thenReturn(0);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        // 阶段 B：一次性返回空（强制走 insert）
        when(abstractUserMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(0), any()))
                .thenReturn(Collections.emptyList());
        when(abstractUserMapper.insert(any(AbstractUser.class))).thenAnswer(inv -> {
            AbstractUser u = inv.getArgument(0);
            u.setId((long) (Math.random() * 1_000_000));
            return 1;
        });

        AbstractUserSyncAppServiceImpl service = new AbstractUserSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, abstractUserMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard(), syncTypeGuard);

        List<AbstractUserSyncItem> items = new ArrayList<>(ITEM_COUNT);
        for (int i = 0; i < ITEM_COUNT; i++) {
            items.add(new AbstractUserSyncItem("u" + i, "User " + i, true, null,
                    null, null, new SyncVersionRef(OCCURRED_AT, (long) i + 1)));
        }
        AbstractUserFullSyncReq req = new AbstractUserFullSyncReq(
                new AbstractUserSyncScope(SOURCE_SERVICE, "USER"), items);

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.detail().appliedCount()).isEqualTo(ITEM_COUNT);

        // ---- N+1 防护断言 ----
        long totalSelects = countInvocations(abstractUserMapper, "selectByTypeAndExternalIds")
                + countInvocations(abstractUserMapper, "selectByTypeAndExternalId")
                + countInvocations(abstractUserMapper, "selectValidById")
                + countInvocations(abstractUserMapper, "selectValidByIds");
        assertThat(totalSelects)
                .as("阶段 B 必须批量解析现有实体，select 调用次数应 < 10（实际 1 次 selectByTypeAndExternalIds）")
                .isLessThan(10);

        long singleRowSelects = countInvocations(abstractUserMapper, "selectByTypeAndExternalId")
                + countInvocations(abstractUserMapper, "selectValidById");
        assertThat(singleRowSelects)
                .as("单行 select 必须 < 5（应为 0）")
                .isLessThan(5);

        long softDeleteBatchCalls = countInvocations(abstractUserMapper, "softDeleteBatch");
        assertThat(softDeleteBatchCalls)
                .as("差异校准必须批量 softDelete（无差异时为 0；有差异最多 1 次）")
                .isLessThan(5);

        long typeResolutionCalls = countInvocations(typeResolutionService, "resolveTypeValue")
                + countInvocations(typeResolutionService, "resolveUserId")
                + countInvocations(typeResolutionService, "resolveRoleId");
        assertThat(typeResolutionCalls)
                .as("类型解析必须批量（resolveTypeValue 仅顶层 1 次；禁止循环 resolveRoleId/resolveUserId）")
                .isLessThan(5);
    }

    @Test
    void resourceEntityFullSync_shouldUseBatchedSelectsAndWrites_for100Items() {
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(eq(TENANT_ID), eq(0), any(), any()))
                .thenReturn(Collections.emptyList());
        // batchResolveResourceIds 用于 parent 解析；items 不带 parent，故 batch 不会被调用
        // 此处保持默认 mock 行为（返回空 Map）

        ResourceEntitySyncAppServiceImpl service = new ResourceEntitySyncAppServiceImpl(
                syncMetadataDomainService, syncMetadataMapper, typeResolutionService,
                resourceEntityMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard(), resourceTypeOwnershipGuard,
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService.class),
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.class), org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.sync.metadata.ResourcePublicationDomainService.class));

        List<ResourceEntitySyncItem> items = new ArrayList<>(ITEM_COUNT);
        for (int i = 0; i < ITEM_COUNT; i++) {
            items.add(new ResourceEntitySyncItem("menu-" + i, "default", "Menu " + i,
                    null, null, null, "/menu/" + i, 1, null, null, null,
                    new SyncVersionRef(OCCURRED_AT, (long) i + 1)));
        }
        ResourceEntityFullSyncReq req = new ResourceEntityFullSyncReq(
                new ResourceEntitySyncScope(SOURCE_SERVICE, "MENU"), items, "1");

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.detail().appliedCount()).isEqualTo(ITEM_COUNT);

        // ---- N+1 防护断言 ----
        long totalSelects = countInvocations(resourceEntityMapper, "selectByTypeCodeAndCodeType")
                + countInvocations(resourceEntityMapper, "selectByTypeAndCodes")
                + countInvocations(resourceEntityMapper, "selectByTypeAndCodesAndCodeTypes")
                + countInvocations(resourceEntityMapper, "selectValidById")
                + countInvocations(resourceEntityMapper, "selectValidByIds");
        assertThat(totalSelects)
                .as("阶段 B 必须批量解析现有资源，select 调用次数应 < 10（实际 1 次 selectByTypeAndCodesAndCodeTypes）")
                .isLessThan(10);

        long singleRowSelects = countInvocations(resourceEntityMapper, "selectByTypeCodeAndCodeType")
                + countInvocations(resourceEntityMapper, "selectValidById");
        assertThat(singleRowSelects)
                .as("单行 select 必须 < 5（应为 0；新建分支会通过 in-memory cache 避免重新查）")
                .isLessThan(5);

        long softDeleteBatchCalls = countInvocations(resourceEntityMapper, "softDeleteBatch");
        assertThat(softDeleteBatchCalls)
                .as("差异校准必须批量 softDelete")
                .isLessThan(5);

        long typeResolutionCalls = countInvocations(typeResolutionService, "resolveTypeValue")
                + countInvocations(typeResolutionService, "resolveResourceId")
                + countInvocations(typeResolutionService, "resolveRoleId")
                + countInvocations(typeResolutionService, "resolveUserId");
        assertThat(typeResolutionCalls)
                .as("类型解析必须批量（仅顶层 resolveTypeValue 1 次）")
                .isLessThan(5);
    }

    /** grok 复评 T-PERM-064 P1 回归锁：full-sync 同批同用户 BIND 互斥两端——
     *  第二条必须 ROLE_MUTEX_CONFLICT 拒绝且零写库（无批内累积的旧实现两条都 applied，必红）。 */
    @Test
    void userRoleFullSync_shouldRejectSecondBindWhenSameUserMutexPairInBatch() {
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        // 同用户 e-0：BIND 角色对 team-a(200) / team-b(201)，relation rel-0(300)；全为新建行
        when(typeResolutionService.batchResolveUserIds(TENANT_ID, "EMP", java.util.Set.of("e-0")))
                .thenReturn(java.util.Map.of("e-0", 100L));
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", java.util.Set.of("team-a", "team-b"), null))
                .thenReturn(java.util.Map.of("team-a", 200L, "team-b", 201L));
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", java.util.Set.of("rel-0"), null))
                .thenReturn(java.util.Map.of("rel-0", 300L));
        when(userRoleMapper.selectValidByUserTargetRelation(anyLong(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRoleMapper.insert(any(cn.ac.fage.accessmesh.access.role.entity.UserRole.class))).thenReturn(1);

        cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService subjectDomainService =
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.class);
        cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService conflictDomainService =
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.class);
        cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper abstractRoleMapper =
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper.class);
        // 目标角色启用；用户现有效角色为空（冷缓存形态——最不利：批内第一条写入对第二条不可见）
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(TENANT_ID), any()))
                .thenAnswer(inv -> new ArrayList<>((java.util.Set<Long>) inv.getArgument(1)));
        when(subjectDomainService.resolveEffectiveRoles(TENANT_ID, 100L))
                .thenReturn(java.util.Set.of());
        // 冲突判定镜像真实语义：postState 同时含 200 与 201 才命中规则 (200,201)
        when(conflictDomainService.findAssignMutexConflicts(eq(TENANT_ID), any()))
                .thenAnswer(inv -> {
                    java.util.Map<Long, java.util.Set<Long>> postState = inv.getArgument(1);
                    java.util.Set<Long> ps = postState.getOrDefault(100L, java.util.Set.of());
                    return ps.contains(200L) && ps.contains(201L
                    ) ? java.util.List.of(new cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.RoleMutexAssignConflict(
                            100L, 9L, 200L, 201L))
                      : java.util.List.of();
                });

        UserRoleSyncAppServiceImpl service = new UserRoleSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, userRoleMapper,
                new cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard(), syncTypeGuard,
                subjectDomainService, conflictDomainService, abstractRoleMapper, org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.class));

        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncScope(SOURCE_SERVICE, "HR_MEMBER", "TEAM_ROLE", "ROOT"),
                List.of(
                        new cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncItem("EMP", "e-0", "TEAM_ROLE", "team-a",
                                "TEAM_ROLE:rel-0", null, null, null, null,
                                new SyncVersionRef(OCCURRED_AT, 1L)),
                        new cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncItem("EMP", "e-0", "TEAM_ROLE", "team-b",
                                "TEAM_ROLE:rel-0", null, null, null, null,
                                new SyncVersionRef(OCCURRED_AT, 2L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        // 第一条 applied、第二条 ROLE_MUTEX_CONFLICT；仅一条 insert（第二条零写库）
        assertThat(resp.detail().itemResults().get(0).applied()).isTrue();
        assertThat(resp.detail().itemResults().get(1).applied()).isFalse();
        assertThat(resp.detail().itemResults().get(1).reason()).isEqualTo("ROLE_MUTEX_CONFLICT");
        assertThat(resp.detail().itemResults().get(1).retryClass())
                .isEqualTo(cn.ac.fage.accessmesh.access.sync.SyncResultBuilder.RETRY_NON_RETRYABLE);
        verify(userRoleMapper, times(1)).insert(any(cn.ac.fage.accessmesh.access.role.entity.UserRole.class));
        org.mockito.ArgumentCaptor<cn.ac.fage.accessmesh.access.role.entity.UserRole> cap =
                org.mockito.ArgumentCaptor.forClass(cn.ac.fage.accessmesh.access.role.entity.UserRole.class);
        verify(userRoleMapper).insert(cap.capture());
        assertThat(cap.getValue().getTargetId()).isEqualTo(200L);
    }

    @Test
    void userRoleFullSync_shouldNotResolveOwnershipPerItem_whenExistingRows() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        // 批量解析：100 个用户/角色/关联角色
        java.util.Map<String, Long> userResolved = new java.util.HashMap<>();
        java.util.Map<String, Long> roleResolved = new java.util.HashMap<>();
        java.util.Map<String, Long> relationResolved = new java.util.HashMap<>();
        java.util.Set<String> userExt = new java.util.HashSet<>();
        java.util.Set<String> roleExt = new java.util.HashSet<>();
        java.util.Set<String> relExt = new java.util.HashSet<>();
        List<cn.ac.fage.accessmesh.access.role.entity.UserRole> existingRows = new ArrayList<>();
        List<cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadata> scopeMetadata = new ArrayList<>();
        List<cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncItem> items = new ArrayList<>();
        for (int i = 0; i < ITEM_COUNT; i++) {
            String ue = "e-" + i;
            String re = "team-" + i;
            String rke = "rel-" + i;
            userExt.add(ue);
            roleExt.add(re);
            relExt.add(rke);
            userResolved.put(ue, 100L + i);
            roleResolved.put(re, 200L + i);
            relationResolved.put(rke, 300L + i);
            // 存量 user_role 行（owner=NULL：外部来源之前写入）
            cn.ac.fage.accessmesh.access.role.entity.UserRole row =
                    new cn.ac.fage.accessmesh.access.role.entity.UserRole();
            row.setId(1000L + i);
            row.setAbstractUserId(100L + i);
            row.setTargetId(200L + i);
            row.setRelationId(300L + i);
            existingRows.add(row);
            // 当前 scope metadata：businessKeyHash -> targetId 与行一致（归属预加载命中）
            String bk = cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil.userRoleBusinessKey(
                    "EMP", ue, "TEAM_ROLE", re, "TEAM_ROLE:" + rke);
            cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadata md =
                    new cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadata();
            md.setBusinessKeyHash(cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil.sha256Hex(bk));
            md.setTargetId(1000L + i);
            md.setTargetStatus("ACTIVE");
            scopeMetadata.add(md);
            items.add(new cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncItem(
                    "EMP", ue, "TEAM_ROLE", re, "TEAM_ROLE:" + rke,
                    null, null, null, null, new SyncVersionRef(OCCURRED_AT, (long) i + 1)));
        }
        when(typeResolutionService.batchResolveUserIds(TENANT_ID, "EMP", userExt)).thenReturn(userResolved);
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", roleExt, null))
                .thenReturn(roleResolved);
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", relExt, null))
                .thenReturn(relationResolved);
        when(userRoleMapper.selectValidByUserTargetRelation(anyLong(), any(), any(), any(), any()))
                .thenReturn(existingRows);
        when(syncMetadataDomainService.listScopeForFullSync(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(scopeMetadata);
        when(syncMetadataDomainService.isNewerVersion(any(), any(), anyLong())).thenReturn(true);

        UserRoleSyncAppServiceImpl service = new UserRoleSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, userRoleMapper,
                new cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard(), syncTypeGuard,
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.class),
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.class),
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper.class),
                org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.class));
        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncScope(SOURCE_SERVICE, "HR_MEMBER", "TEAM_ROLE", "1"), items);

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.detail().appliedCount()).isEqualTo(ITEM_COUNT);
        // N+1 防护：归属校验走预加载 Map，100 存量行不得产生 per-item resolveTargetId 查询
        verify(syncMetadataDomainService, never())
                .resolveTargetId(anyLong(), anyString(), anyString(), anyString(), anyString());
        // scope metadata 只加载一次（归属 Map + 差异校准复用）
        verify(syncMetadataDomainService, times(1))
                .listScopeForFullSync(anyLong(), anyString(), anyString(), anyString());
    }

    /**
     * 统计 mock 上指定方法名的调用次数（不区分参数）。
     */
    private static long countInvocations(Object mock, String methodName) {
        return mockingDetails(mock).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals(methodName))
                .count();
    }
}
