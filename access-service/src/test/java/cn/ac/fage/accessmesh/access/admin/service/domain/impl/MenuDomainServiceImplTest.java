package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * calculateDepth 层级语义单测（T-ACCESS-015 二轮复评 P1-2，不依赖 Docker）。
 * <p>
 * 语义约定：返回给定节点自身深度，顶级（parent 为 null/0）= 1；
 * 挂到某父节点下的新菜单深度 = 父深度 + 1，由调用方（MenuWriteAppServiceImpl）相加判断。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MenuDomainServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock
    private SysMenuMapper menuMapper;

    private MenuDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MenuDomainServiceImpl(menuMapper);
    }

    private SysMenu node(Long id, Long parentId) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        return menu;
    }

    @Test
    @DisplayName("顶级节点（parent 为 null/0）深度为 1")
    void topLevelDepthIsOne() {
        assertThat(service.calculateDepth(TENANT, null)).isEqualTo(1);
        assertThat(service.calculateDepth(TENANT, 0L)).isEqualTo(1);
    }

    @Test
    @DisplayName("三层链 root→mid→leaf：leaf 自身深度为 3（祖先链不含自身）")
    void thirdLevelNodeDepthIsThree() {
        // batchGetAncestorIds 逐层加载：{leaf} → {mid} → {root}
        when(menuMapper.selectByIdsForAncestors(eq(TENANT), anySet()))
            .thenReturn(List.of(node(3L, 2L)))   // leaf
            .thenReturn(List.of(node(2L, 1L)))   // mid
            .thenReturn(List.of(node(1L, 0L)));  // root

        assertThat(service.calculateDepth(TENANT, 3L)).isEqualTo(3);
    }
}
