package cn.ac.fage.accessmesh.access.org.service.impl;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.org.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.org.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.access.org.service.OrgVisibilityQueryAppService;
import cn.ac.fage.accessmesh.access.org.service.OrgWriteAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Q-066：组织读面 orgType 线格式组装对脏数据（null 列 / 历史标签）的降级锁。
 * <p>
 * 旧实现 toResp 内 {@code Integer.parseInt(org.getOrgType())} 无防御——org_type 为 NULL
 * 或历史标签（"ORG"/"POSITION"）时 NumberFormatException，组织 getOrg/分页/树整读挂。
 * 修复后经 {@code OrgOperationCodeMapper.parseWireOrgType} 单源解析（null/垃圾→null，
 * 标签归一 1/2）；两用例在旧实现下均抛 NumberFormatException 失败。
 */
@ExtendWith(MockitoExtension.class)
class OrgAppServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long ORG_ID = 10L;

    @Mock private SysOrgMapper orgMapper;
    @Mock private SysOrgTreeConfigMapper treeConfigMapper;
    @Mock private OrgTreeConfigDomainService treeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgWriteAppService orgWriteAppService;
    @Mock private SysUserOrgMapper userOrgMapper;
    @Mock private UserDomainService userDomainService;
    @Mock private OrgVisibilityQueryAppService orgVisibilityQueryService;

    private OrgAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgAppServiceImpl(orgMapper, treeConfigMapper, treeConfigDomainService,
            orgDomainService, permissionValidator, orgWriteAppService, userOrgMapper,
            userDomainService, orgVisibilityQueryService);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private SysOrg org(String orgType) {
        SysOrg org = new SysOrg();
        org.setId(ORG_ID);
        org.setOrgType(orgType);
        org.setName("默认组织");
        org.setParentId(null);
        return org;
    }

    @Test
    @DisplayName("org_type 为 NULL（如岗位行被直改库置空）→ orgType 组装为 null，读取不中断")
    void getOrg_nullOrgType_degradesToNull() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org(null));

        OrgResp resp = service.getOrg(ORG_ID);

        assertThat(resp.orgType()).isNull();
    }

    @Test
    @DisplayName("org_type 历史标签 \"POSITION\" → 线格式归一为 2（与 normalize 同源）")
    void getOrg_positionLabel_normalizesToTwo() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("POSITION"));

        OrgResp resp = service.getOrg(ORG_ID);

        assertThat(resp.orgType()).isEqualTo(2);
    }
}
