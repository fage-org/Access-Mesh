package cn.ac.fage.accessmesh.access.role.service.impl;

import cn.ac.fage.accessmesh.access.role.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.role.service.impl.UserRoleQueryAppServiceImpl;
import cn.ac.fage.accessmesh.access.role.mapper.UserRoleQueryMapper;
import cn.ac.fage.accessmesh.access.role.dto.projection.OrgBriefProjection;
import cn.ac.fage.accessmesh.access.role.dto.projection.UserRoleProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户角色组合查询服务（跨域只读）行为测试。
 * <p>
 * 功能角色候选查询（listRoles）已随 /role/list 端点退役（T-FE-058，2026-09-23）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserRoleQueryAppServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private UserRoleQueryMapper userRoleQueryMapper;

    private UserRoleQueryAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleQueryAppServiceImpl(permissionValidator, typeResolutionService, userRoleQueryMapper);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
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
