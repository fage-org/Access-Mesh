package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import com.mybatisflex.core.paginate.Page;
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
 * admin 分页响应扁平形态回归锁（T-ADMIN-027）。
 * <p>
 * admin 12 端点由嵌套 {@code PaginatedResult{items, pagination:{total,page,size,totalPages}}}
 * 迁扁平 {@code PageResp{items,total,pageNum,pageSize,hasNext}}。本测试锁定扁平五字段与
 * hasNext 的两类装配语义：手工 count/offset 装配（offset+已取条数&lt;total）与 MyBatis-Flex
 * {@code Page#hasNext()}（pageNumber&lt;totalPage）。边界用例取「整除末页 hasNext=false」
 * 与「非整除末页 hasNext=false」，旧嵌套实现无 hasNext 访问器，无法通过编译。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AdminPageRespShapeTest {

    private static final long TENANT_ID = 1000L;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /**
     * 手工 count/offset 装配（OrgTreeConfig为代表，XML 分页同款）。
     */
    @Nested
    class ManualCountAssembly {

        @Mock private SysOrgTreeConfigMapper orgTreeConfigMapper;
        @Mock private AdminPermissionValidator permissionValidator;

        private OrgTreeConfigServiceImpl service() {
            return new OrgTreeConfigServiceImpl(orgTreeConfigMapper, permissionValidator);
        }

        @Test
        @DisplayName("非末页：offset+已取 < total → hasNext=true")
        void pageBeforeLastHasNextTrue() {
            when(orgTreeConfigMapper.countAllByTenant(TENANT_ID)).thenReturn(4L);
            when(orgTreeConfigMapper.selectAllByTenant(eq(TENANT_ID), eq(0), eq(2)))
                .thenReturn(List.of(new SysOrgTreeConfig(), new SysOrgTreeConfig()));

            PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.OrgTreeConfigResp> resp =
                service().pageOrgTreeConfigs(PageReq.of(1, 2));

            assertThat(resp.items()).hasSize(2);
            assertThat(resp.total()).isEqualTo(4L);
            assertThat(resp.pageNum()).isEqualTo(1);
            assertThat(resp.pageSize()).isEqualTo(2);
            assertThat(resp.hasNext()).isTrue();
        }

        @Test
        @DisplayName("整除末页边界：offset+已取 == total → hasNext=false（旧 totalPages 语义等位点）")
        void exactFitLastPageHasNextFalse() {
            when(orgTreeConfigMapper.countAllByTenant(TENANT_ID)).thenReturn(4L);
            when(orgTreeConfigMapper.selectAllByTenant(eq(TENANT_ID), eq(2), eq(2)))
                .thenReturn(List.of(new SysOrgTreeConfig(), new SysOrgTreeConfig()));

            PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.OrgTreeConfigResp> resp =
                service().pageOrgTreeConfigs(PageReq.of(2, 2));

            assertThat(resp.items()).hasSize(2);
            assertThat(resp.total()).isEqualTo(4L);
            assertThat(resp.hasNext()).isFalse();
        }

        @Test
        @DisplayName("空集：total=0 短路不查列表 → items 空、hasNext=false")
        void zeroTotalShortCircuit() {
            when(orgTreeConfigMapper.countAllByTenant(TENANT_ID)).thenReturn(0L);

            PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.OrgTreeConfigResp> resp =
                service().pageOrgTreeConfigs(PageReq.of(1, 2));

            assertThat(resp.items()).isEmpty();
            assertThat(resp.total()).isZero();
            assertThat(resp.hasNext()).isFalse();
        }
    }

    /**
     * MyBatis-Flex Page 装配（Config 为代表，Flex paginate 同款 7 端点）。
     */
    @Nested
    class FlexPageAssembly {

        @Mock private SystemConfigMapper configMapper;
        @Mock private AdminPermissionValidator permissionValidator;

        private ConfigServiceImpl service() {
            return new ConfigServiceImpl(configMapper, permissionValidator);
        }

        /** 就地填充调用方传入的 Flex Page（Page 未实现 equals，不能按值匹配桩参）。 */
        private void stubFlexPage(int fetched, long totalRow) {
            when(configMapper.selectPageByTenantId(org.mockito.ArgumentMatchers.any(), eq(TENANT_ID)))
                .thenAnswer(inv -> {
                    Page<SystemConfig> page = inv.getArgument(0);
                    page.setTotalRow(totalRow);
                    page.setRecords(java.util.stream.IntStream.rangeClosed(1, fetched)
                        .mapToObj(i -> new SystemConfig())
                        .toList());
                    return page;
                });
        }

        @Test
        @DisplayName("非末页：pageNumber < totalPage → hasNext=true")
        void flexPageBeforeLastHasNextTrue() {
            stubFlexPage(2, 5L);

            PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp> resp =
                service().pageConfigs(PageReq.of(1, 2));

            assertThat(resp.items()).hasSize(2);
            assertThat(resp.total()).isEqualTo(5L);
            assertThat(resp.pageNum()).isEqualTo(1);
            assertThat(resp.pageSize()).isEqualTo(2);
            assertThat(resp.hasNext()).isTrue();
        }

        @Test
        @DisplayName("整除末页边界：pageNumber == totalPage → hasNext=false")
        void flexExactFitLastPageHasNextFalse() {
            stubFlexPage(2, 4L);

            PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp> resp =
                service().pageConfigs(PageReq.of(2, 2));

            assertThat(resp.items()).hasSize(2);
            assertThat(resp.total()).isEqualTo(4L);
            assertThat(resp.hasNext()).isFalse();
        }
    }
}
