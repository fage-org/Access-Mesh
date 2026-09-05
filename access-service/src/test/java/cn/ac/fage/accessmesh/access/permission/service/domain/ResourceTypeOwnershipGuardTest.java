package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ResourceTypeOwnershipGuard} 单元测试（T-PERM-052 类型级所有权）。
 */
@ExtendWith(MockitoExtension.class)
class ResourceTypeOwnershipGuardTest {

    private static final Long TENANT = 1L;

    @Mock
    private TypeDefinitionMapper typeDefinitionMapper;
    @Mock
    private ServiceConfigMapper serviceConfigMapper;
    @Mock
    private ResourceEntityDomainService resourceEntityDomainService;

    private ResourceTypeOwnershipGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ResourceTypeOwnershipGuard(typeDefinitionMapper, serviceConfigMapper,
                resourceEntityDomainService, new ObjectMapper());
    }

    private static TypeDefinition resourceType(Long id, String code, Integer value, String extra) {
        TypeDefinition td = new TypeDefinition();
        td.setId(id);
        td.setTenantId(TENANT);
        td.setTypeKey("resource_type");
        td.setTypeCode(code);
        td.setTypeValue(value);
        td.setExtra(extra);
        return td;
    }

    // ---- parseOwnership ----

    @Test
    @DisplayName("extra 空/缺键/损坏 JSON → 缺省 MANAGED（fail-closed：外部同步拒绝、管理面可写）")
    void parseOwnership_shouldDefaultToManaged() {
        assertThat(guard.parseOwnership(null).managedMode()).isEqualTo(ResourceTypeOwnershipGuard.MODE_MANAGED);
        assertThat(guard.parseOwnership("  ").managedMode()).isEqualTo(ResourceTypeOwnershipGuard.MODE_MANAGED);
        assertThat(guard.parseOwnership("{\"k\":1}").managedMode()).isEqualTo(ResourceTypeOwnershipGuard.MODE_MANAGED);
        assertThat(guard.parseOwnership("not-json").managedMode()).isEqualTo(ResourceTypeOwnershipGuard.MODE_MANAGED);
        // mode 非字符串：损坏声明同样回落 MANAGED
        assertThat(guard.parseOwnership("{\"managedMode\":1}").managedMode())
                .isEqualTo(ResourceTypeOwnershipGuard.MODE_MANAGED);
    }

    @Test
    @DisplayName("SYNC 声明解析出 mode 与来源；syncOwnedBy 按来源精确匹配")
    void parseOwnership_shouldParseSyncDeclaration() {
        ResourceTypeOwnershipGuard.Ownership ownership = guard.parseOwnership(
                "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\"}");
        assertThat(ownership.managedMode()).isEqualTo(ResourceTypeOwnershipGuard.MODE_SYNC);
        assertThat(ownership.syncOwnedBy("hr-service")).isTrue();
        assertThat(ownership.syncOwnedBy("other-service")).isFalse();
        assertThat(guard.parseOwnership("{\"k\":1}").syncOwnedBy("hr-service")).isFalse();
    }

    // ---- resolveTypeOwnership / 管理面门禁 ----

    @Test
    @DisplayName("rejectIfSyncManagedType：SYNC 类型 → 20055；MANAGED/类型不存在 → 放行")
    void rejectIfSyncManagedType_shouldThrowOnlyForSyncOwned() {
        when(typeDefinitionMapper.selectByTypeKeyAndCode(TENANT, "resource_type", "HR_ORG"))
                .thenReturn(resourceType(1L, "HR_ORG", 5,
                        "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\"}"));
        when(typeDefinitionMapper.selectByTypeKeyAndCode(TENANT, "resource_type", "MENU"))
                .thenReturn(resourceType(2L, "MENU", 1, null));
        when(typeDefinitionMapper.selectByTypeKeyAndCode(TENANT, "resource_type", "GHOST"))
                .thenReturn(null);

        assertThatThrownBy(() -> guard.rejectIfSyncManagedType(TENANT, "HR_ORG"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(20055);
        assertThatCode(() -> guard.rejectIfSyncManagedType(TENANT, "MENU")).doesNotThrowAnyException();
        // 类型不存在不在此拦截（存在性由既有类型解析负责，门禁只按声明判定）
        assertThatCode(() -> guard.rejectIfSyncManagedType(TENANT, "GHOST")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejectIfAnySyncManagedByValues：类型值批量判定，任一 SYNC → 20055（级联删除守卫）")
    void rejectIfAnySyncManagedByValues_shouldThrowWhenAnySyncOwned() {
        when(typeDefinitionMapper.selectByTypeKeyAndValues(TENANT, "resource_type", Set.of(1, 7)))
                .thenReturn(List.of(
                        resourceType(2L, "MENU", 1, null),
                        resourceType(8L, "BI_MENU", 7,
                                "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"bi-service\"}")));

        assertThatThrownBy(() -> guard.rejectIfAnySyncManagedByValues(TENANT, Set.of(1, 7)))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(20055)
                .isNotNull();
        // message 列明类型码，便于排障
        assertThatThrownBy(() -> guard.rejectIfAnySyncManagedByValues(TENANT, Set.of(1, 7)))
                .hasMessageContaining("BI_MENU");
    }

    @Test
    @DisplayName("rejectIfAnySyncManagedByCodes：空集合不查库")
    void rejectIfAnySyncManagedByCodes_shouldSkipWhenEmpty() {
        guard.rejectIfAnySyncManagedByCodes(TENANT, java.util.Set.of());
        verify(typeDefinitionMapper, never()).selectByTypeKeyAndCodes(anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    // ---- validateExtraDeclaration（保存边界） ----

    @Test
    @DisplayName("声明校验：SYNC+已注册来源 → 通过；MANAGED 显式 → 通过；无声明 → 通过")
    void validateExtraDeclaration_shouldAcceptValidDeclarations() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, "hr-service"))
                .thenReturn(new cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig());

        assertThatCode(() -> guard.validateExtraDeclaration(TENANT, "resource_type",
                "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\"}", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.validateExtraDeclaration(TENANT, "resource_type",
                "{\"managedMode\":\"MANAGED\"}", false)).doesNotThrowAnyException();
        assertThatCode(() -> guard.validateExtraDeclaration(TENANT, "resource_type", "{\"k\":1}", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.validateExtraDeclaration(TENANT, "resource_type", null, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("声明校验：非 resource_type 携带声明键 / 非法 mode / SYNC 缺来源 / 来源未注册 / MANAGED 携带来源 / 非法 JSON → 拒绝")
    void validateExtraDeclaration_shouldRejectInvalidDeclarations() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, "hr-service")).thenReturn(null);

        assertThatThrownBy(() -> guard.validateExtraDeclaration(TENANT, "group_type",
                "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\"}", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.validateExtraDeclaration(TENANT, "resource_type",
                "{\"managedMode\":\"AUTO\"}", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.validateExtraDeclaration(TENANT, "resource_type",
                "{\"managedMode\":\"SYNC\"}", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.validateExtraDeclaration(TENANT, "resource_type",
                "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\"}", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("未注册");
        assertThatThrownBy(() -> guard.validateExtraDeclaration(TENANT, "resource_type",
                "{\"managedMode\":\"MANAGED\",\"syncSourceService\":\"hr-service\"}", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.validateExtraDeclaration(TENANT, "resource_type", "not-json", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- rejectIfDeclarationChangeBlocked（无有效行才可改） ----

    @Test
    @DisplayName("声明变更 + 类型下有有效行 → 20056；无行 → 放行；声明未变 → 不查行数直接放行")
    void rejectIfDeclarationChangeBlocked_shouldGuardRowExistence() {
        TypeDefinition hrOrg = resourceType(1L, "HR_ORG", 5,
                "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\"}");

        // 声明未变（含其余键差异）→ 不查行数放行（先于任何变更路径调用，never 断言才成立）
        assertThatCode(() -> guard.rejectIfDeclarationChangeBlocked(TENANT, hrOrg,
                "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\",\"k\":1}"))
                .doesNotThrowAnyException();
        verify(resourceEntityDomainService, never()).hasValidRowsOfType(anyLong(),
                org.mockito.ArgumentMatchers.any());

        when(resourceEntityDomainService.hasValidRowsOfType(TENANT, 5)).thenReturn(true);
        // SYNC → MANAGED（有行）拒绝
        assertThatThrownBy(() -> guard.rejectIfDeclarationChangeBlocked(TENANT, hrOrg,
                "{\"managedMode\":\"MANAGED\"}"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(20056);
        // 删键隐式切回 MANAGED 同样拒绝
        assertThatThrownBy(() -> guard.rejectIfDeclarationChangeBlocked(TENANT, hrOrg, "{\"k\":1}"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(20056);

        // 非 resource_type 不受守卫
        TypeDefinition groupType = new TypeDefinition();
        groupType.setId(2L);
        groupType.setTenantId(TENANT);
        groupType.setTypeKey("group_type");
        groupType.setTypeCode("G1");
        groupType.setTypeValue(1);
        assertThatCode(() -> guard.rejectIfDeclarationChangeBlocked(TENANT, groupType, "{\"k\":2}"))
                .doesNotThrowAnyException();

        // 无有效行（hasValidRowsOfType=false）→ 放行（无行才可改的正向路径）
        when(resourceEntityDomainService.hasValidRowsOfType(TENANT, 5)).thenReturn(false);
        assertThatCode(() -> guard.rejectIfDeclarationChangeBlocked(TENANT, hrOrg,
                "{\"managedMode\":\"MANAGED\"}")).doesNotThrowAnyException();
    }

    // ---- 内部来源声明（2026-09-05 补充定案：事实链路四类型收编） ----

    @Test
    @DisplayName("内部来源豁免：is_system 类型可声明 SYNC+access-service（无需服务注册行）；非 is_system 拒绝")
    void validateExtraDeclaration_shouldCarveOutInternalSource() {
        String internal = "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"access-service\"}";

        // is_system 预置类型：豁免通过（access-service 不是 service_config 注册行）
        assertThatCode(() -> guard.validateExtraDeclaration(TENANT, "resource_type", internal, true))
                .doesNotThrowAnyException();
        // 非 is_system（API create 恒 false / 租户自定义类型）：拒绝——防自定义类型锁死成无人写入的孤岛
        assertThatThrownBy(() -> guard.validateExtraDeclaration(TENANT, "resource_type", internal, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅系统预置类型");
    }

    @Test
    @DisplayName("内部来源类型的 20055 message 指向事实链路管理入口（区别于外部来源「到来源系统操作」）")
    void rejectIfSyncManagedType_shouldDistinguishInternalSourceMessage() {
        when(typeDefinitionMapper.selectByTypeKeyAndCode(TENANT, "resource_type", "USER"))
                .thenReturn(resourceType(1L, "USER", 6,
                        "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"access-service\"}"));

        assertThatThrownBy(() -> guard.rejectIfSyncManagedType(TENANT, "USER"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(20055)
                .isNotNull();
        assertThatThrownBy(() -> guard.rejectIfSyncManagedType(TENANT, "USER"))
                .hasMessageContaining("系统事实链路维护");
    }
}
