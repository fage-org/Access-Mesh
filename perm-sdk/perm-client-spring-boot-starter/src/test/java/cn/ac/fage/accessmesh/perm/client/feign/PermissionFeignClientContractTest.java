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
 * {@code docs/design/access-service-api-contract.md}。
 * </p>
 * <p>
 * 封闭语义（评审 P2 修复）：接口的**全部**声明方法都必须满足 POST + 单一
 * {@code @RequestBody}——不以 @PostMapping 预过滤（否则新增 GET/未标注方法会逃逸）；
 * 单 DTO 方法路径清单封闭且不得重复；仅四运行时查询允许追加显式凭证头重载。
 * 新增或删除端点必须同步修改本清单。
 * （{@code SyncTaskFeignClient} 已随内部同步链路退役删除，不在契约范围。）
 * </p>
 */
class PermissionFeignClientContractTest {

    /** SDK 对外契约的封闭路径清单（与单 DTO 方法一一对应）。 */
    private static final Set<String> CONTRACT_PATHS = Set.of(
        "/api/access/abstract-user/remove",
        "/api/access/auth/check",
        "/api/access/auth/batch-check",
        "/api/access/auth/query-resources",
        "/api/access/auth/query-scopes",
        "/api/access/abstract-role/create",
        "/api/access/abstract-role/list",
        "/api/access/abstract-role/detail",
        "/api/access/user-role/list",
        "/api/access/user-role/assign",
        "/api/access/user-role/revoke",
        "/api/access/resource-entity/create",
        "/api/access/resource-entity/batch-create",
        "/api/access/resource-entity/update",
        "/api/access/resource-entity/remove",
        "/api/access/operation-permission/list",
        "/api/access/permission-view/effective-permission-codes",
        "/api/access/auth/interface-admission",
        "/api/access/auth/interface-admission-snapshot"
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
    @DisplayName("HTTP 契约：路径封闭清单；仅运行时查询允许显式凭证重载")
    void contractPathsAreFrozen() {
        assertThat(allInstanceMethods())
            .as("全部方法必须标注 @PostMapping（未标注/GET 方法不得混入 SDK 契约）")
            .allMatch(m -> m.isAnnotationPresent(PostMapping.class));

        List<String> actualPaths = allInstanceMethods().stream()
            .filter(m -> m.getParameterCount() == 1)
            .map(m -> m.getAnnotation(PostMapping.class).value()[0])
            .toList();

        assertThat((long) actualPaths.size())
            .as("接口方法总数必须与契约清单一致，防止增删端点静默漂移")
            .isEqualTo(CONTRACT_PATHS.size());
        assertThat(actualPaths)
            .as("SDK 声明的路径集合必须与契约清单完全一致")
            .containsExactlyInAnyOrderElementsOf(CONTRACT_PATHS);
        assertThat(allInstanceMethods().stream().filter(m -> m.getParameterCount() == 3)
            .map(m -> m.getAnnotation(PostMapping.class).value()[0]).toList())
            .containsExactlyInAnyOrder("/api/access/auth/check", "/api/access/auth/batch-check",
                "/api/access/auth/query-resources", "/api/access/auth/query-scopes");
    }

    @Test
    @DisplayName("HTTP 契约：全部方法 POST + 恰好一个 @RequestBody，禁止 GET/路径参数/查询参数；凭证重载仅允许指定头")
    void contractMethodsAreAllPostWithSingleJsonBody() throws Exception {
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
                .as("%s 仅允许单一 JSON Body，或追加独立凭证头", method.getName()).isIn(1, 3);
            assertThat(Arrays.stream(method.getParameterAnnotations()[0])
                .anyMatch(RequestBody.class::isInstance))
                .as("%s 的唯一参数必须标注 @RequestBody", method.getName()).isTrue();
            if (method.getParameterCount() == 3) {
                for (int index = 1; index <= 2; index++) {
                    var parameter = method.getParameters()[index];
                    assertThat(parameter.getType()).isEqualTo(String.class);
                    assertThat(parameter.getAnnotations()).hasSize(1);
                    assertThat(parameter.getAnnotation(RequestHeader.class).value())
                        .isEqualTo(index == 1 ? "X-Credential-Id" : "X-Credential-Secret");
                }
                var base = PermissionFeignClient.class.getDeclaredMethod(method.getName(), method.getParameterTypes()[0]);
                assertThat(base.getAnnotation(PostMapping.class).value()).containsExactly(post.value());
                assertThat(base.getGenericReturnType()).isEqualTo(method.getGenericReturnType());
            }
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
                "matchedRoleIds", "matchedPermissionIds", "conditionEvaluated");
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

    @Test
    void explicitCredentialHeadersReachFeignRequests_withoutGlobalFallback() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var requests = new java.util.ArrayList<feign.Request>();
        var fixed = new cn.ac.fage.accessmesh.perm.common.feign.FeignCredentialInterceptor();
        org.springframework.test.util.ReflectionTestUtils.setField(fixed, "credentialId", "sc-default");
        org.springframework.test.util.ReflectionTestUtils.setField(fixed, "credentialSecret", "sk-default");
        PermissionFeignClient client = feign.Feign.builder()
            .contract(new org.springframework.cloud.openfeign.support.SpringMvcContract())
            .encoder((body, type, template) -> {
                try { template.body(json.writeValueAsString(body)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
            })
            .decoder((response, type) -> null)
            .requestInterceptor(fixed)
            .client((request, options) -> {
                requests.add(request);
                return feign.Response.builder().request(request).status(200).reason("OK")
                    .headers(java.util.Map.of()).body("{}", java.nio.charset.StandardCharsets.UTF_8).build();
            }).target(PermissionFeignClient.class, "http://localhost");
        for (Method method : allInstanceMethods()) {
            if (method.getParameterCount() != 3) continue;
            Class<?> dto = method.getParameterTypes()[0];
            var constructor = dto.getDeclaredConstructors()[0];
            Object body = constructor.newInstance(new Object[constructor.getParameterCount()]);
            method.invoke(client, body, "sc-selected", "sk-selected");
        }
        assertThat(requests).hasSize(4);
        for (var request : requests) {
            assertThat(request.httpMethod()).isEqualTo(feign.Request.HttpMethod.POST);
            assertThat(request.headers().get("X-Credential-Id")).containsExactly("sc-selected");
            assertThat(request.headers().get("X-Credential-Secret")).containsExactly("sk-selected");
            assertThat(request.headers()).doesNotContainKeys("X-Tenant-Id", "X-Internal-Secret");
            assertThat(json.readTree(request.body()).has("subjectExternalId")).isTrue();
        }
    }

    private static List<String> recordComponents(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    }
}
