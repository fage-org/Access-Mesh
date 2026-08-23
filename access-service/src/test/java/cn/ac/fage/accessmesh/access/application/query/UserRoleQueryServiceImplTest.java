package cn.ac.fage.accessmesh.access.application.query;

import cn.ac.fage.accessmesh.access.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.application.query.impl.UserRoleQueryServiceImpl;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserRoleQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.projection.FunctionalRoleProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.OrgBriefProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserRoleProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户角色组合查询服务（跨域只读）行为测试。
 */
@ExtendWith(MockitoExtension.class)
class UserRoleQueryServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private UserRoleQueryMapper userRoleQueryMapper;

    private UserRoleQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleQueryServiceImpl(permissionValidator, typeResolutionService, userRoleQueryMapper);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("listRoles")
    class ListRoles {

        @Test
        @DisplayName("默认功能角色：类型码解析后一次批量查询并映射展示字段")
        void defaultFunctionalRoles_mapped() {
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("role_type"), anySet()))
                .thenReturn(Map.of("BASIC_ROLE", 6, "GROUP_ROLE", 5, "PERSONAL", 3));
            when(userRoleQueryMapper.selectFunctionalRoles(eq(TENANT), anyList(), eq(null), eq(0), eq(200)))
                .thenReturn(List.of(
                    new FunctionalRoleProjection(6, "r-1", "基础管理员"),
                    new FunctionalRoleProjection(5, "r-2", "分组管理员")));

            List<RoleListItemResp> result = service.listRoles(null);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).roleTypeCode()).isEqualTo("BASIC_ROLE");
            assertThat(result.get(0).roleExternalId()).isEqualTo("r-1");
            assertThat(result.get(0).roleName()).isEqualTo("基础管理员");
            assertThat(result.get(0).roleTypeLabel()).isEqualTo("基础角色");
            assertThat(result.get(1).roleTypeLabel()).isEqualTo("分组角色");
            // 单次批量查询，无循环单查
            verify(userRoleQueryMapper).selectFunctionalRoles(eq(TENANT), anyList(), eq(null), eq(0), eq(200));
        }

        @Test
        @DisplayName("显式传入 ORG/POSITION 拒绝（防暴露本地投影角色）")
        void reservedType_rejected() {
            assertThatThrownBy(() -> service.listRoles(List.of("ORG")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(AdminErrorCode.INVALID_PARAM.getCode());
        }

        @Test
        @DisplayName("门禁 ROLE:VIEW 在查询前执行")
        void gateCheckedBeforeQuery() {
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("role_type"), anySet()))
                .thenReturn(Map.of("BASIC_ROLE", 6, "GROUP_ROLE", 5, "PERSONAL", 3));
            when(userRoleQueryMapper.selectFunctionalRoles(eq(TENANT), anyList(), eq(null), eq(0), eq(200)))
                .thenReturn(List.of());

            service.listRoles(null);

            verify(permissionValidator).checkTypeLevel(eq("ROLE"), eq("VIEW"));
        }

        @Test
        @DisplayName("门禁拒绝时安全异常传播，不执行查询")
        void gateDenied_propagates() {
            org.mockito.Mockito.doThrow(new SecurityException("denied"))
                .when(permissionValidator).checkTypeLevel(any(), any());

            assertThatThrownBy(() -> service.listRoles(null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("denied");
            verify(userRoleQueryMapper, never()).selectFunctionalRoles(any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("功能角色类型未注册（租户缺 role_type 种子）返回空列表（matchNone 语义）")
        void unresolvedType_returnsEmpty() {
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("role_type"), anySet()))
                .thenReturn(Map.of());

            List<RoleListItemResp> result = service.listRoles(List.of("BASIC_ROLE"));

            assertThat(result).isEmpty();
            verify(userRoleQueryMapper, never()).selectFunctionalRoles(any(), any(), any(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("listUserRoles")
    class ListUserRoles {

        @Test
        @DisplayName("POSITION 角色补充所属组织名，其他类型保持业务键")
        void position_relationOrgName_filled() {
            when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "100"))
                .thenReturn(9000L);
            // 写路径语义：user_role.target_type 恒为 ROLE（岗位成员绑定同写 ROLE），
            // 岗位语义由 target 角色类型（role_type=2）承载
            when(userRoleQueryMapper.selectUserRoleProjections(eq(TENANT), eq(9000L), any()))
                .thenReturn(List.of(
                    new UserRoleProjection(null, "ROLE", 6, "r-1", "基础管理员", null, null, null),
                    new UserRoleProjection(8001L, "ROLE", 2, "pos-9", "测试岗位", "50", null, null)));
            when(userRoleQueryMapper.selectOrgBriefsByIds(eq(TENANT), eq(Set.of(50L))))
                .thenReturn(List.of(new OrgBriefProjection(50L, "研发一部")));
            when(typeResolutionService.batchResolveTypeCodes(eq(TENANT), eq("role_type"), anySet()))
                .thenReturn(Map.of(6, "BASIC_ROLE", 2, "POSITION"));

            List<UserRoleItemResp> result = service.listUserRoles(100L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).roleTypeCode()).isEqualTo("BASIC_ROLE");
            assertThat(result.get(0).relationOrgName()).isNull();
            assertThat(result.get(1).roleTypeCode()).isEqualTo("POSITION");
            assertThat(result.get(1).relationOrgName()).isEqualTo("研发一部");
            assertThat(result.get(1).relationId()).isEqualTo(8001L);
            // 组织名批量补查一次，无循环单查
            verify(userRoleQueryMapper).selectOrgBriefsByIds(eq(TENANT), eq(Set.of(50L)));
        }

        @Test
        @DisplayName("用户投影缺失返回空列表（不查询角色关系）")
        void userProjectionMissing_returnsEmpty() {
            when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "100"))
                .thenReturn(null);

            List<UserRoleItemResp> result = service.listUserRoles(100L);

            assertThat(result).isEmpty();
            verify(userRoleQueryMapper, never()).selectUserRoleProjections(any(), any(), any());
        }

        @Test
        @DisplayName("门禁 USER:VIEW@userId 在查询前执行")
        void gateCheckedBeforeUserRoleQuery() {
            when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "100"))
                .thenReturn(9000L);
            when(userRoleQueryMapper.selectUserRoleProjections(eq(TENANT), eq(9000L), any()))
                .thenReturn(List.of());

            service.listUserRoles(100L);

            verify(permissionValidator).checkInstanceLevel(eq("USER"), eq("100"), eq("VIEW"));
        }

        @Test
        @DisplayName("target 角色投影缺失保留关系行，角色字段为 null")
        void missingTargetRole_keepsRowWithNullFields() {
            when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "100"))
                .thenReturn(9000L);
            when(userRoleQueryMapper.selectUserRoleProjections(eq(TENANT), eq(9000L), any()))
                .thenReturn(List.of(
                    new UserRoleProjection(null, "BASIC_ROLE", null, null, null, null, null, null)));

            List<UserRoleItemResp> result = service.listUserRoles(100L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).roleTypeCode()).isNull();
            assertThat(result.get(0).roleExternalId()).isNull();
        }

        @Test
        @DisplayName("有效期窗口由 Mapper SQL 承载（now 透传），无 Java 层过滤")
        void validityWindow_passedToMapper() {
            when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "100"))
                .thenReturn(9000L);
            when(userRoleQueryMapper.selectUserRoleProjections(eq(TENANT), eq(9000L), any()))
                .thenReturn(List.of());

            service.listUserRoles(100L);

            org.mockito.ArgumentCaptor<java.time.LocalDateTime> nowCaptor =
                org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
            verify(userRoleQueryMapper).selectUserRoleProjections(eq(TENANT), eq(9000L), nowCaptor.capture());
            assertThat(nowCaptor.getValue()).isNotNull();
            assertThat(nowCaptor.getValue()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(5));
        }
    }
}
