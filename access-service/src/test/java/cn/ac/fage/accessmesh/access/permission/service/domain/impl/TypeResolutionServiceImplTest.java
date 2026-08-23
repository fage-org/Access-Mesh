package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类型解析服务单元测试（T-ACCESS-017 特征测试·链路 2：sys_user.id → abstract_user.id 映射）。
 * <p>
 * 固化 {@code TypeResolutionServiceImpl.resolveUserId} 的当前正确行为：
 * ADMIN_USER 类型经 type_definition 解析 user_type 值后，
 * 按 {@code abstract_user.external_id = sys_user.id.toString()} 定位投影主体；
 * 类型未定义或投影缺失时返回 null（调用方 fail-closed），且 null 不回填缓存。
 * 真实 SQL 语义（uk_abstract_user 部分唯一索引、软删过滤）由
 * {@code cn.ac.fage.accessmesh.access.characterization.PermissionCharacterizationPgIT} 验证。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TypeResolutionServiceImplTest {

    /** type_definition 种子：user_type / ADMIN_USER = 3（docs/design/schema/access-service.sql） */
    private static final Integer ADMIN_USER_TYPE_VALUE = 3;

    @Mock private TypeDefinitionMapper typeDefinitionMapper;
    @Mock private AbstractUserMapper abstractUserMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private BizDomainMapper bizDomainMapper;
    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private CacheService cacheService;

    private TypeResolutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TypeResolutionServiceImpl(
            typeDefinitionMapper,
            abstractUserMapper,
            resourceEntityMapper,
            bizDomainMapper,
            abstractRoleMapper,
            operationPermissionMapper,
            cacheService
        );
        // beginRead 委托真实实现——mock 默认返回 null 令牌会导致 put(token) 断言失真
        org.mockito.Mockito.lenient().when(cacheService.beginRead(any(
                cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry.class)))
            .thenAnswer(inv -> new cn.ac.fage.accessmesh.common.cache.DefaultCacheService(
                null, null, null, new cn.ac.fage.accessmesh.common.cache.CacheProperties(), null)
                .beginRead(inv.getArgument(0)));
    }

    @Test
    @DisplayName("resolveUserId：ADMIN_USER 外部ID（sys_user.id 字符串）解析为 abstract_user.id")
    void resolveUserIdShouldMapAdminUserExternalIdToSubjectId() {
        TypeDefinition td = new TypeDefinition();
        td.setTypeKey("user_type");
        td.setTypeCode("ADMIN_USER");
        td.setTypeValue(ADMIN_USER_TYPE_VALUE);

        AbstractUser projection = new AbstractUser();
        projection.setId(501L);

        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "user_type", "ADMIN_USER")).thenReturn(td);
        when(abstractUserMapper.selectByTypeAndExternalId(1L, ADMIN_USER_TYPE_VALUE, "9")).thenReturn(projection);

        Long subjectId = service.resolveUserId(1L, "ADMIN_USER", "9");

        assertEquals(501L, subjectId);
    }

    @Test
    @DisplayName("resolveUserId：主体类型未注册时返回 null 且不回填缓存（fail-closed 前置）")
    void resolveUserIdShouldReturnNullWhenSubjectTypeUndefined() {
        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "user_type", "UNKNOWN_TYPE")).thenReturn(null);

        assertNull(service.resolveUserId(1L, "UNKNOWN_TYPE", "9"));

        verify(abstractUserMapper, never()).selectByTypeAndExternalId(anyLong(), any(), any());
        // null 解析结果不缓存：下一次同键解析仍需回源（负缓存不存在）
        verify(cacheService, never()).put(any(cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry.class), anyLong(), any(), any());
    }

    @Test
    @DisplayName("resolveUserId：投影中不存在该外部ID时返回 null")
    void resolveUserIdShouldReturnNullWhenProjectionMissing() {
        TypeDefinition td = new TypeDefinition();
        td.setTypeValue(ADMIN_USER_TYPE_VALUE);

        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "user_type", "ADMIN_USER")).thenReturn(td);
        when(abstractUserMapper.selectByTypeAndExternalId(1L, ADMIN_USER_TYPE_VALUE, "404")).thenReturn(null);

        assertNull(service.resolveUserId(1L, "ADMIN_USER", "404"));
    }

    @Test
    @DisplayName("resolveUserId：user_type 解析值经类型缓存目录回填（TYPE_VALUE）")
    void resolveUserIdShouldBackfillTypeValueCacheOnMiss() {
        TypeDefinition td = new TypeDefinition();
        td.setTypeValue(ADMIN_USER_TYPE_VALUE);

        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "user_type", "ADMIN_USER")).thenReturn(td);
        when(abstractUserMapper.selectByTypeAndExternalId(eq(1L), eq(ADMIN_USER_TYPE_VALUE), any()))
            .thenReturn(null);

        service.resolveUserId(1L, "ADMIN_USER", "9");

        var tokenCaptor = org.mockito.ArgumentCaptor.forClass(
            cn.ac.fage.accessmesh.common.cache.CacheReadToken.class);
        verify(cacheService).put(tokenCaptor.capture(), eq(1L), eq("user_type:ADMIN_USER"), any());
        assertEquals(cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.TYPE_VALUE,
            tokenCaptor.getValue().catalog());
    }
}
