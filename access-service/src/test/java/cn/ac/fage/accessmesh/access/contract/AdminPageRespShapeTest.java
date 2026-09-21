package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
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
import cn.ac.fage.accessmesh.access.org.service.impl.OrgTreeConfigAppServiceImpl;

/**
 * admin 分页响应扁平形态回归锁（T-ADMIN-027）。
 * <p>
 * admin 12 端点由嵌套 {@code PaginatedResult{items, pagination:{total,page,size,totalPages}}}
 * 迁扁平 {@code PageResp{items,total,pageNum,pageSize,hasNext}}。本测试锁定扁平五字段与
 * hasNext 的手工 count/offset 装配语义（offset+已取条数&lt;total）。边界用例取「整除末页
 * hasNext=false」与「非整除末页 hasNext=false」。原 Flex Page 装配轨（MyBatis-Flex
 * {@code Page#hasNext()}）已随 T-ADMIN-026 XML 分页改造消亡。原 Config 双轨用例随
 * admin /config 端点退役删除（T-ACCESS-037），OrgTreeConfig 为该装配语义唯一锁定载体。
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
        @Mock private cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService orgTreeConfigDomainService;
        @Mock private cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService orgDomainService;
        @Mock private cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport treeWriteLockSupport;

        private OrgTreeConfigAppServiceImpl service() {
            return new OrgTreeConfigAppServiceImpl(orgTreeConfigMapper, permissionValidator,
                orgTreeConfigDomainService, orgDomainService, treeWriteLockSupport);
        }

        @Test
        @DisplayName("非末页：offset+已取 < total → hasNext=true")
        void pageBeforeLastHasNextTrue() {
            when(orgTreeConfigMapper.countAllByTenant(TENANT_ID)).thenReturn(4L);
            when(orgTreeConfigMapper.selectAllByTenant(eq(TENANT_ID), eq(0), eq(2)))
                .thenReturn(List.of(new SysOrgTreeConfig(), new SysOrgTreeConfig()));

            PageResp<cn.ac.fage.accessmesh.access.org.dto.resp.OrgTreeConfigResp> resp =
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

            PageResp<cn.ac.fage.accessmesh.access.org.dto.resp.OrgTreeConfigResp> resp =
                service().pageOrgTreeConfigs(PageReq.of(2, 2));

            assertThat(resp.items()).hasSize(2);
            assertThat(resp.total()).isEqualTo(4L);
            assertThat(resp.hasNext()).isFalse();
        }

        @Test
        @DisplayName("空集：total=0 短路不查列表 → items 空、hasNext=false")
        void zeroTotalShortCircuit() {
            when(orgTreeConfigMapper.countAllByTenant(TENANT_ID)).thenReturn(0L);

            PageResp<cn.ac.fage.accessmesh.access.org.dto.resp.OrgTreeConfigResp> resp =
                service().pageOrgTreeConfigs(PageReq.of(1, 2));

            assertThat(resp.items()).isEmpty();
            assertThat(resp.total()).isZero();
            assertThat(resp.hasNext()).isFalse();
        }
    }
}
