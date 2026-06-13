package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link OrgTreeConfigDomainServiceImpl#resolveTreeRootExternalId(Long, Long)}：
 * <ul>
 *   <li>org 自身 ∈ rootOrgId 集合 → 返回自身</li>
 *   <li>org 祖先 ∈ rootOrgId 集合 → 返回祖先 rootOrgId</li>
 *   <li>多树场景按祖先链命中</li>
 *   <li>游离 org → 抛 BizException(ORG_TREE_ROOT_NOT_RESOLVED)</li>
 *   <li>org 不存在 → 抛 BizException(ORG_NOT_FOUND)</li>
 *   <li>禁止 fallback "1"</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrgTreeConfigDomainServiceResolveTest {

    private static final Long TENANT_ID = 1L;

    @Mock private SysOrgTreeConfigMapper orgTreeConfigMapper;
    @Mock private OrgDomainService orgDomainService;

    @InjectMocks
    private OrgTreeConfigDomainServiceImpl service;

    private SysOrg org(long id) {
        SysOrg o = new SysOrg();
        o.setId(id);
        o.setTenantId(TENANT_ID);
        o.setName("org-" + id);
        return o;
    }

    private SysOrgTreeConfig treeConfig(long rootOrgId, boolean isDefault) {
        SysOrgTreeConfig c = new SysOrgTreeConfig();
        c.setTenantId(TENANT_ID);
        c.setRootOrgId(rootOrgId);
        c.setIsDefault(isDefault);
        return c;
    }

    @BeforeEach
    void setUp() {
        // no-op; per-test stubbing
    }

    @Test
    @DisplayName("org.path 包含 rootOrgId=999 时返回 \"999\"（root 不是 1）")
    void shouldReturnRoot999_whenAncestorChainContainsRoot() {
        // orgId=500 祖先链含 999
        when(orgDomainService.selectValidById(eq(TENANT_ID), eq(500L))).thenReturn(org(500L));
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(999L, true)));
        when(orgDomainService.getAncestorIds(eq(TENANT_ID), eq(500L)))
            .thenReturn(List.of(250L, 999L));

        String result = service.resolveTreeRootExternalId(TENANT_ID, 500L);

        assertThat(result).isEqualTo("999");
    }

    @Test
    @DisplayName("游离 org（祖先链与 rootOrgId 集合无交集）抛 BizException(ORG_TREE_ROOT_NOT_RESOLVED)")
    void shouldThrow_whenOrgIsOrphan() {
        when(orgDomainService.selectValidById(eq(TENANT_ID), eq(500L))).thenReturn(org(500L));
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(999L, true)));
        when(orgDomainService.getAncestorIds(eq(TENANT_ID), eq(500L)))
            .thenReturn(List.of(250L, 100L)); // 不含 999

        assertThatThrownBy(() -> service.resolveTreeRootExternalId(TENANT_ID, 500L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("ORG_TREE_ROOT_NOT_RESOLVED");
    }

    @Test
    @DisplayName("orgId 自己即 rootOrgId 时直接返回自身（无需祖先链）")
    void shouldReturnSelf_whenOrgIsRoot() {
        when(orgDomainService.selectValidById(eq(TENANT_ID), eq(999L))).thenReturn(org(999L));
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(999L, true)));

        String result = service.resolveTreeRootExternalId(TENANT_ID, 999L);

        assertThat(result).isEqualTo("999");
        // 命中 orgId 自身后无需祖先链调用，但即便调用也允许（lenient mock）
    }

    @Test
    @DisplayName("多树：orgId=150 祖先链含 100，应解析到 tree A 而非 tree B")
    void shouldResolveCorrectTree_whenMultipleTrees() {
        when(orgDomainService.selectValidById(eq(TENANT_ID), eq(150L))).thenReturn(org(150L));
        // tree A rootOrgId=100，tree B rootOrgId=200
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(100L, true), treeConfig(200L, false)));
        when(orgDomainService.getAncestorIds(eq(TENANT_ID), eq(150L)))
            .thenReturn(List.of(100L)); // 在 tree A 子树

        String result = service.resolveTreeRootExternalId(TENANT_ID, 150L);

        assertThat(result).isEqualTo("100");
    }

    @Test
    @DisplayName("org 不存在时抛 BizException(ORG_NOT_FOUND)")
    void shouldThrow_whenOrgNotFound() {
        when(orgDomainService.selectValidById(eq(TENANT_ID), eq(404L))).thenReturn(null);
        // 不应触达 mapper
        lenient().when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(1L, true)));

        assertThatThrownBy(() -> service.resolveTreeRootExternalId(TENANT_ID, 404L))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ORG_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("无任何 SysOrgTreeConfig 时抛 BizException(ORG_TREE_ROOT_NOT_RESOLVED)（不 fallback \"1\"）")
    void shouldThrow_whenNoTreeConfig() {
        when(orgDomainService.selectValidById(eq(TENANT_ID), eq(500L))).thenReturn(org(500L));
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveTreeRootExternalId(TENANT_ID, 500L))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode())
            .hasMessageNotContaining("\"1\"");
    }

    // ===== 批量 resolver 用例 =====

    @Test
    @DisplayName("批量：3 个 orgId 分属 2 棵树（root=100、root=200），返回完整 Map size=3，mapper 仅被调用 1 次（非 N+1）")
    void shouldResolveAllOrgIdsBatch_whenAllResolvable() {
        // orgIds: 150 -> tree A(100), 151 -> tree A(100), 250 -> tree B(200)
        when(orgDomainService.batchSelectValidByIdsMap(eq(TENANT_ID), any()))
            .thenReturn(Map.of(
                150L, org(150L),
                151L, org(151L),
                250L, org(250L)
            ));
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(100L, true), treeConfig(200L, false)));
        when(orgDomainService.batchGetAncestorIds(eq(TENANT_ID), any()))
            .thenReturn(Map.of(
                150L, List.of(120L, 100L),
                151L, List.of(100L),
                250L, List.of(200L)
            ));

        Map<Long, String> result = service.resolveTreeRootExternalIds(
            TENANT_ID, List.of(150L, 151L, 250L));

        assertThat(result).hasSize(3);
        assertThat(result).containsEntry(150L, "100");
        assertThat(result).containsEntry(151L, "100");
        assertThat(result).containsEntry(250L, "200");
        // 验证非 N+1：mapper.selectAllValid 仅 1 次（< 3）
        verify(orgTreeConfigMapper, times(1)).selectAllValid(eq(TENANT_ID));
        verify(orgDomainService, times(1)).batchSelectValidByIdsMap(eq(TENANT_ID), any());
        verify(orgDomainService, times(1)).batchGetAncestorIds(eq(TENANT_ID), any());
    }

    @Test
    @DisplayName("批量：3 个 orgId 中 2 个游离 → 抛 BizException，message 同时含两个游离 orgId")
    void shouldThrowWithAllMissingOrgIds_whenSomeOrphan() {
        // 150 ok（祖先含 100），300/400 游离（祖先链不含任何 root）
        when(orgDomainService.batchSelectValidByIdsMap(eq(TENANT_ID), any()))
            .thenReturn(Map.of(
                150L, org(150L),
                300L, org(300L),
                400L, org(400L)
            ));
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(100L, true)));
        when(orgDomainService.batchGetAncestorIds(eq(TENANT_ID), any()))
            .thenReturn(Map.of(
                150L, List.of(100L),
                300L, List.of(),
                400L, List.of(999L)
            ));

        assertThatThrownBy(() -> service.resolveTreeRootExternalIds(
                TENANT_ID, List.of(150L, 300L, 400L)))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode())
            .hasMessageContaining("300")
            .hasMessageContaining("400");
    }

    @Test
    @DisplayName("批量：空入参 → 不调 mapper，返回 emptyMap")
    void shouldReturnEmptyMap_whenInputEmpty() {
        // 不 stub 任何 mapper

        Map<Long, String> r1 = service.resolveTreeRootExternalIds(TENANT_ID, List.of());
        Map<Long, String> r2 = service.resolveTreeRootExternalIds(TENANT_ID, null);
        Map<Long, String> r3 = service.resolveTreeRootExternalIds(TENANT_ID, Set.of());

        assertThat(r1).isEmpty();
        assertThat(r2).isEmpty();
        assertThat(r3).isEmpty();
        verify(orgTreeConfigMapper, never()).selectAllValid(anyLong());
        verify(orgDomainService, never()).batchSelectValidByIdsMap(anyLong(), any());
    }

    @Test
    @DisplayName("批量：org 不存在（不在 batchSelectValidByIdsMap 返回里）→ 抛 BizException 且 missing 含该 orgId")
    void shouldThrow_whenOrgEntityMissing() {
        when(orgDomainService.batchSelectValidByIdsMap(eq(TENANT_ID), any()))
            .thenReturn(Map.of(150L, org(150L))); // 999 缺失
        lenient().when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(100L, true)));
        lenient().when(orgDomainService.batchGetAncestorIds(eq(TENANT_ID), any()))
            .thenReturn(Map.of(150L, List.of(100L)));

        assertThatThrownBy(() -> service.resolveTreeRootExternalIds(
                TENANT_ID, List.of(150L, 999L)))
            .isInstanceOf(BizException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode())
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("批量：orgId 自身即 rootOrgId → 直接命中无需祖先链")
    void shouldResolveSelfWhenOrgIsRoot_batch() {
        when(orgDomainService.batchSelectValidByIdsMap(eq(TENANT_ID), any()))
            .thenReturn(Map.of(100L, org(100L)));
        when(orgTreeConfigMapper.selectAllValid(eq(TENANT_ID)))
            .thenReturn(List.of(treeConfig(100L, true)));
        // ancestorMap 可空也可有：lenient
        lenient().when(orgDomainService.batchGetAncestorIds(eq(TENANT_ID), any()))
            .thenReturn(Collections.emptyMap());

        Map<Long, String> result = service.resolveTreeRootExternalIds(TENANT_ID, List.of(100L));

        assertThat(result).containsExactly(Map.entry(100L, "100"));
    }
}
