package cn.ac.fage.accessmesh.access.user.service;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.constant.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.service.OrgVisibilityQueryAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.user.dto.req.MemberCandidatesReq;
import cn.ac.fage.accessmesh.access.user.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.user.service.impl.UserAppServiceImpl;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /user/member-candidates 门禁单测（T-ORG-003，F005）：
 * 候选查询与 {@code user-org/assign} 共用既有成员动作码解析——普通组织
 * MANAGE_MEMBER、岗位 ASSIGN_POSITION_USER（经 OrgOperationCodeMapper 按目标
 * orgType 解析）；先验证目标组织存在再判权（与 setPrimaryOrg 同序）。
 * 旧实现固定 ORG:UPDATE 且门禁先于存在性校验——仅持成员动作权的有限管理员
 * 选不出人（首管理员全码掩盖），本组用例在旧实现下必红。
 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceMemberCandidatesGateTest {

    private static final Long OPERATOR = 900L;
    private static final long POSITION_ORG = 3001L;
    private static final long REGULAR_ORG = 3002L;
    /** orgType=null 缺省形态的目标（与岗位/普通常量区分，评审 P3-2：常量语义不复用） */
    private static final long NULL_TYPE_ORG = 3003L;

    @Mock private SysUserMapper userMapper;
    @Mock private UserDomainService userDomainService;
    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private UserWriteAppService userWriteAppService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgVisibilityQueryAppService orgVisibilityQueryService;

    private UserAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAppServiceImpl(
            userMapper, userDomainService, userOrgDomainService,
            orgTreeConfigDomainService, orgDomainService, userWriteAppService,
            permissionValidator, orgVisibilityQueryService);
    }

    private SysOrg org(long id, String orgType) {
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setOrgType(orgType);
        return org;
    }

    /** 门禁通过后走「操作者无可见默认树范围」早退路径（可见范围裁剪/排除逻辑不在本用例射程）。 */
    private void runCandidates(long targetOrgId) {
        when(orgVisibilityQueryService.getOperatorVisibleDefaultTreeOrgIds(any(), eq(OPERATOR)))
            .thenReturn(java.util.Set.of());
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR);
            var resp = service.memberCandidates(new MemberCandidatesReq(targetOrgId, null, null, null));
            assertThat(resp.items()).isEmpty();
        }
    }

    @Test
    @DisplayName("岗位目标门禁收 ASSIGN_POSITION_USER（旧实现固定 UPDATE 必红，T-ORG-003）")
    void positionTargetGatesByAssignPositionUser() {
        when(orgDomainService.selectValidById(any(), eq(POSITION_ORG))).thenReturn(org(POSITION_ORG, "2"));

        runCandidates(POSITION_ORG);

        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.ORG), eq(String.valueOf(POSITION_ORG)),
            eq(OrgOperationCodeMapper.resolveForUserOrg("2", OperationCode.UPDATE)));
        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.ORG), eq(String.valueOf(POSITION_ORG)), eq(OperationCode.ASSIGN_POSITION_USER));
    }

    @Test
    @DisplayName("普通组织目标（orgType=1 与 null 缺省）门禁收 MANAGE_MEMBER（旧实现固定 UPDATE 必红）")
    void regularOrgTargetGatesByManageMember() {
        when(orgDomainService.selectValidById(any(), eq(REGULAR_ORG))).thenReturn(org(REGULAR_ORG, "1"));
        runCandidates(REGULAR_ORG);
        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.ORG), eq(String.valueOf(REGULAR_ORG)), eq(OperationCode.MANAGE_MEMBER));

        when(orgDomainService.selectValidById(any(), eq(NULL_TYPE_ORG))).thenReturn(org(NULL_TYPE_ORG, null));
        runCandidates(NULL_TYPE_ORG);
        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.ORG), eq(String.valueOf(NULL_TYPE_ORG)), eq(OperationCode.MANAGE_MEMBER));
    }

    @Test
    @DisplayName("门禁不再收 ORG:UPDATE（候选与提交同权后 UPDATE 持有者单码不放行，旧实现必红）")
    void gateNeverResolvesToRawUpdate() {
        when(orgDomainService.selectValidById(any(), eq(REGULAR_ORG))).thenReturn(org(REGULAR_ORG, "1"));

        runCandidates(REGULAR_ORG);

        verify(permissionValidator, never()).checkInstanceLevel(
            any(), any(), eq(OperationCode.UPDATE));
    }

    @Test
    @DisplayName("目标组织不存在拒绝 ORG_NOT_FOUND 且不触门禁（存在性先于判权，旧实现门禁在前必红）")
    void missingTargetOrgRejectsBeforeGate() {
        when(orgDomainService.selectValidById(any(), eq(REGULAR_ORG))).thenReturn(null);

        assertThatThrownBy(() -> service.memberCandidates(new MemberCandidatesReq(REGULAR_ORG, null, null, null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining(AccessErrorCode.ORG_NOT_FOUND.getMessage());

        verify(permissionValidator, never()).checkInstanceLevel(any(), any(), any());
    }
}
