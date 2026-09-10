package cn.ac.fage.accessmesh.perm.client.feign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * perm-sdk HTTP 契约固化测试（T-ACCESS-010）。
 * <p>
 * 证明 Feign 目标切换为 {@code access-service} 后，SDK 声明的对外 HTTP 契约
 * （路径、POST + JSON Body 形态、方法清单）与切换前完全一致——外部接入方
 * 仅受服务发现名变化影响，不受契约漂移影响。契约权威来源为
 * {@code docs/design/permission-center/api-contract.md}。
 * </p>
 * <p>
 * 封闭语义（评审 P2 修复）：接口的**全部**声明方法都必须满足 POST + 单一
 * {@code @RequestBody}——不以 @PostMapping 预过滤（否则新增 GET/未标注方法会逃逸）；
 * 路径清单为封闭集合且不得重复（Set 比对会掩盖重复映射，用 List 计数）。
 * 新增或删除端点必须同步修改本清单。
 * （{@code SyncTaskFeignClient} 已随内部同步链路退役删除，不在契约范围。）
 * </p>
 */
class PermissionFeignClientContractTest {

    /** SDK 对外契约的封闭路径清单（与方法一一对应）。 */
    private static final Set<String> CONTRACT_PATHS = Set.of(
        "/api/perm/abstract-user/remove",
        "/api/perm/auth/check",
        "/api/perm/auth/batch-check",
        "/api/perm/auth/query-resources",
        "/api/perm/auth/query-scopes",
        "/api/perm/abstract-role/create",
        "/api/perm/abstract-role/list",
        "/api/perm/abstract-role/detail",
        "/api/perm/user-role/list",
        "/api/perm/user-role/assign",
        "/api/perm/user-role/revoke",
        "/api/perm/resource-entity/create",
        "/api/perm/resource-entity/batch-create",
        "/api/perm/resource-entity/update",
        "/api/perm/resource-entity/remove",
        "/api/perm/operation-permission/list",
        "/api/perm/permission-view/effective-permissions",
        "/api/perm/permission-view/effective-permission-codes"
    );

    /** 接口的全部实例方法（不做注解预过滤——封闭检查必须覆盖每一个方法）。 */
    private static List<Method> allInstanceMethods() {
        return Arrays.stream(PermissionFeignClient.class.getDeclaredMethods())
            .filter(m -> !Modifier.isStatic(m.getModifiers()) && !m.isSynthetic())
            .toList();
    }

    @Test
    @DisplayName("Feign 目标：@FeignClient name 统一为 access-service（T-ACCESS-010）")
    void feignTargetIsAccessService() {
        FeignClient feignClient = PermissionFeignClient.class.getAnnotation(FeignClient.class);
        assertThat(feignClient).as("PermissionFeignClient 必须声明 @FeignClient").isNotNull();
        assertThat(feignClient.name())
            .as("Feign 目标必须为 access-service，不得残留旧服务发现名")
            .isEqualTo("access-service");
    }

    @Test
    @DisplayName("HTTP 契约：路径封闭清单逐一比对且无重复映射，增删端点必须同步本清单")
    void contractPathsAreFrozen() {
        assertThat(allInstanceMethods())
            .as("全部方法必须标注 @PostMapping（未标注/GET 方法不得混入 SDK 契约）")
            .allMatch(m -> m.isAnnotationPresent(PostMapping.class));

        List<String> actualPaths = allInstanceMethods().stream()
            .map(m -> m.getAnnotation(PostMapping.class).value()[0])
            .toList();

        assertThat((long) actualPaths.size())
            .as("接口方法总数必须与契约清单一致（18，2026-09-06 T-API-002 补齐 query-resources/query-scopes 后），防止增删端点静默漂移")
            .isEqualTo(CONTRACT_PATHS.size());
        assertThat(actualPaths)
            .as("SDK 声明的路径集合必须与契约清单完全一致")
            .containsExactlyInAnyOrderElementsOf(CONTRACT_PATHS);
        assertThat(Set.copyOf(actualPaths))
            .as("路径不得重复映射（两个方法声明同一路径会被 Set 比对掩盖）")
            .hasSize(actualPaths.size());
    }

    @Test
    @DisplayName("HTTP 契约：全部方法 POST + 恰好一个 @RequestBody，禁止 GET/路径参数/查询参数/请求头参数")
    void contractMethodsAreAllPostWithSingleJsonBody() {
        for (Method method : allInstanceMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            assertThat(post)
                .as("%s 必须标注 @PostMapping（禁止 GET/PUT/DELETE 或未标注方法混入 SDK 契约）",
                    method.getName()).isNotNull();
            assertThat(post.path())
                .as("%s 不得使用 @PostMapping path 属性（路径必须经 value 声明）", method.getName()).isEmpty();
            assertThat(post.value())
                .as("%s 的 @PostMapping 必须恰好声明一个路径", method.getName()).hasSize(1);
            assertThat(post.params()).as("%s 不得声明请求参数", method.getName()).isEmpty();
            assertThat(post.headers()).as("%s 不得声明请求头", method.getName()).isEmpty();

            assertThat(method.getParameterCount())
                .as("%s 必须有且仅有一个参数（单一 JSON Body）", method.getName()).isEqualTo(1);
            assertThat(Arrays.stream(method.getParameterAnnotations()[0])
                .anyMatch(RequestBody.class::isInstance))
                .as("%s 的唯一参数必须标注 @RequestBody", method.getName()).isTrue();
            // 禁用注解按注解实例判定（参数类型检查无效：DTO 参数可同时带 @RequestBody @RequestParam）
            assertThat(Arrays.stream(method.getParameterAnnotations()[0])
                .noneMatch(a -> a instanceof RequestParam || a instanceof PathVariable || a instanceof RequestHeader))
                .as("%s 不得携带 @RequestParam/@PathVariable/@RequestHeader（违反 POST+单一 JSON Body 契约）",
                    method.getName()).isTrue();
        }
    }

    @Test
    @DisplayName("SDK 四件套 DTO 字段快照：query-resources/query-scopes 线格式防漂移（T-API-002 裁剪后终态）")
    void queryEndpointDtoFieldsAreFrozen() {
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq.class))
            .containsExactly("subjectTypeCode", "subjectExternalId", "resourceTypeCodes",
                "operationCodes", "domainCode", "codeType", "includeInherited",
                "includeChildren", "context");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp.class))
            .containsExactly("items", "cacheTtlSeconds");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp.ResourceEntry.class))
            .containsExactly("resourceTypeCode", "resourceCode", "codeType", "resourceName",
                "canGrant", "scopeMode", "operations", "grantSources");

        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq.class))
            .containsExactly("subjectTypeCode", "subjectExternalId", "parentResourceTypeCode",
                "parentResourceCode", "parentCodeType", "parentOperationCodes",
                "scopeResourceTypeCodes", "scopeOperationCodes", "scopeCodeType",
                "domainCode", "context");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.class))
            .containsExactly("reason", "matchedParentOperations", "scopeGroups", "cacheTtlSeconds");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeGroup.class))
            .containsExactly("resourceTypeCode", "operationCode", "scopeMode", "items");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeItem.class))
            .containsExactly("resourceCode", "codeType", "resourceName");
    }

    @Test
    @DisplayName("SDK 四件套 DTO 字段快照：check 族响应恢复结果记录全量回传（T-API-003 推翻裁剪后终态）")
    void checkEndpointDtoFieldsAreFrozen() {
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp.class))
            .containsExactly("allowed", "reason", "matchedRoleIds", "matchedPermissionIds",
                "conditionEvaluated");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp.AuthCheckItemResult.class))
            .containsExactly("resourceTypeCode", "resourceCode", "operationCode", "allowed", "reason",
                "matchedRoleIds", "matchedPermissionIds");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp.MatchedResource.class))
            .containsExactly("resourceId", "resourceTypeCode", "resourceCode", "operationCode", "allowed",
                "matchedRoleIds", "matchedPermissionIds");
    }

    @Test
    @DisplayName("SDK check 族请求入参快照：T-PERM-058 主资源上下文四字段后终态")
    void checkRequestDtoFieldsAreFrozen() {
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq.class))
            .containsExactly("subjectTypeCode", "subjectExternalId", "resourceTypeCode",
                "resourceCode", "operationCode", "domainCode", "codeType", "inheritMode",
                "parentResourceTypeCode", "parentResourceCode", "parentCodeType", "parentOperationCodes",
                "context");
        assertThat(recordComponents(cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq.class))
            .containsExactly("subjectTypeCode", "subjectExternalId", "items",
                "parentResourceTypeCode", "parentResourceCode", "parentCodeType", "parentOperationCodes",
                "context");
    }

    private static List<String> recordComponents(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    }
}
