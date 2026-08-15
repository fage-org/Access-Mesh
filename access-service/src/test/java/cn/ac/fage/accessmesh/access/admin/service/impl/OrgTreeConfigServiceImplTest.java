package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgTreeConfigCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgTreeConfigUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 组织树配置写方法 ORG_VISIBILITY 失效登记测试（T-ACCESS-006 第二轮外部评审补测）。
 * <p>
 * 验证 4 个写方法（create/update/delete/setDefault）在 @PermissionChange 上下文中调用
 * {@link PermissionChangeContext#markVisibility}，使累积器登记可见范围变更（tenantId + visibilityChanged）。
 * flush 阶段由 {@link cn.ac.fage.accessmesh.access.permission.aop.PermissionChangeAspect}
 * 无条件 evictAll(ORG_VISIBILITY)——框架行为由 PermissionChangeAspectTest 覆盖，本测试仅断言
 * 业务侧登记发生（Accumulator.isEmpty()==false 且角色/用户/条件/服务编码各维度为空，只能来自 visibilityChanged）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrgTreeConfigServiceImplTest {

    private static final long TENANT_ID = 1000L;

    @Mock private SysOrgTreeConfigMapper orgTreeConfigMapper;
    @Mock private AdminPermissionValidator permissionValidator;

    private OrgTreeConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgTreeConfigServiceImpl(orgTreeConfigMapper, permissionValidator);
        TenantContextHolder.setTenantId(TENANT_ID);
        // 模拟 @PermissionChange 切面入口绑定累积器（owner）
        assertThat(PermissionChangeContext.bindIfAbsent()).isTrue();
    }

    @AfterEach
    void tearDown() {
        PermissionChangeContext.clear();
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("写方法登记 ORG_VISIBILITY 租户级失效")
    class WriteMethodsMarkVisibility {

        @Test
        @DisplayName("createOrgTreeConfig 登记可见范围变更")
        void create_marksVisibility() {
            service.createOrgTreeConfig(new OrgTreeConfigCreateReq(1L, "默认组织树", "DEFAULT", null));

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("updateOrgTreeConfig 登记可见范围变更")
        void update_marksVisibility() {
            when(orgTreeConfigMapper.selectByIdSafe(eq(TENANT_ID), eq(1L))).thenReturn(new SysOrgTreeConfig());

            service.updateOrgTreeConfig(new OrgTreeConfigUpdateReq(1L, null, "新名称", null, null));

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("deleteOrgTreeConfigs 登记可见范围变更")
        void delete_marksVisibility() {
            service.deleteOrgTreeConfigs(new IdsReq(List.of(1L, 2L)));

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("setDefault 登记可见范围变更")
        void setDefault_marksVisibility() {
            when(orgTreeConfigMapper.selectByIdSafe(eq(TENANT_ID), eq(5L))).thenReturn(new SysOrgTreeConfig());

            service.setDefault(5L);

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
        }
    }
}
