package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 祖先链内存上溯防环回归（T-PERM-044）。
 * <p>
 * OrgDomainServiceImpl / MenuDomainServiceImpl 的 {@code batchGetAncestorIds} 构建祖先链的
 * {@code while} 上溯在 parent 环脏数据（move 并发窗口残留）下原本无限循环（JVM 死循环）。
 * 修复后重访节点即截断，返回已收集的部分链。本用例在旧实现下不返回（挂死），
 * 锁定「不挂死且行为确定」。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AncestorChainCycleGuardTest {

    private static final Long TENANT = 1L;
    private static final Long A = 10L;
    private static final Long B = 11L;
    private static final Long NORMAL = 12L;

    @Mock
    private SysOrgMapper orgMapper;

    @Mock
    private SysMenuMapper menuMapper;

    private SysOrg org(Long id, Long parentId) {
        SysOrg o = new SysOrg();
        o.setId(id);
        o.setTenantId(TENANT);
        o.setParentId(parentId);
        return o;
    }

    private SysMenu menu(Long id, Long parentId) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setTenantId(TENANT);
        m.setParentId(parentId);
        return m;
    }

    @Test
    @DisplayName("组织祖先链遇 2-环：重访截断返回 [B, A]，不无限上溯")
    void orgAncestorChainTerminatesOnParentCycle() {
        // A.parent=B、B.parent=A（2-环）；NORMAL 正常挂在 A 下形成链入环的第三节点
        SysOrg a = org(A, B);
        SysOrg b = org(B, A);
        SysOrg normal = org(NORMAL, A);
        when(orgMapper.selectByIdsForAncestors(anyLong(), any(Set.class)))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Set<Long> ids = (Set<Long>) inv.getArgument(1);
                return List.of(a, b, normal).stream()
                    .filter(o -> ids.contains(o.getId()))
                    .toList();
            });

        OrgDomainServiceImpl service = new OrgDomainServiceImpl(orgMapper);

        // 环成员起点：A → B → A（重访截断，A 不重复入链）
        assertThat(service.getAncestorIds(TENANT, A)).containsExactly(B, A);
        // 链入环起点：NORMAL → A → B → A（重访截断）
        assertThat(service.getAncestorIds(TENANT, NORMAL)).containsExactly(A, B);
    }

    @Test
    @DisplayName("菜单祖先链遇 2-环：重访截断返回 [B, A]，calculateDepth 确定返回")
    void menuAncestorChainTerminatesOnParentCycle() {
        SysMenu a = menu(A, B);
        SysMenu b = menu(B, A);
        when(menuMapper.selectByIdsForAncestors(anyLong(), any(Set.class)))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Set<Long> ids = (Set<Long>) inv.getArgument(1);
                return List.of(a, b).stream()
                    .filter(m -> ids.contains(m.getId()))
                    .toList();
            });

        MenuDomainServiceImpl service = new MenuDomainServiceImpl(menuMapper);

        assertThat(service.getAncestorIds(TENANT, A)).containsExactly(B, A);
        // calculateDepth = 祖先数 + 1（截断链 2 个祖先 → 3），环脏数据下确定返回不挂死
        assertThat(service.calculateDepth(TENANT, A)).isEqualTo(3);
    }

    @Test
    @DisplayName("正常树祖先链不受防环影响：完整返回到根")
    void normalChainUnaffected() {
        SysOrg root = org(B, 0L);
        SysOrg child = org(A, B);
        when(orgMapper.selectByIdsForAncestors(anyLong(), any(Set.class)))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Set<Long> ids = (Set<Long>) inv.getArgument(1);
                return List.of(root, child).stream()
                    .filter(o -> ids.contains(o.getId()))
                    .toList();
            });

        OrgDomainServiceImpl service = new OrgDomainServiceImpl(orgMapper);

        assertThat(service.getAncestorIds(TENANT, A)).containsExactly(B);
    }
}
