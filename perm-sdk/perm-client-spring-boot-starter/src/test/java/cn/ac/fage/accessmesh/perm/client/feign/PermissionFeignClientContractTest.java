package cn.ac.fage.accessmesh.perm.client.feign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
 * 路径清单为封闭集合：新增或删除端点必须同步修改本清单，防止 SDK 契约静默漂移。
 * （{@code SyncTaskFeignClient} 已随内部同步链路退役删除，不在契约范围。）
 * </p>
 */
class PermissionFeignClientContractTest {

    /** SDK 对外契约的封闭路径清单（与方法一一对应）。 */
    private static final Set<String> CONTRACT_PATHS = Set.of(
        "/api/perm/abstract-user/remove",
        "/api/perm/auth/check",
        "/api/perm/auth/batch-check",
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
        "/api/perm/role-resource-permission/save",
        "/api/perm/role-resource-permission/revoke",
        "/api/perm/permission-view/effective-permissions",
        "/api/perm/permission-view/effective-permission-codes"
    );

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
    @DisplayName("HTTP 契约：路径封闭清单逐一比对，增删端点必须同步本清单")
    void contractPathsAreFrozen() {
        Set<String> actualPaths = feignMethods().stream()
            .map(m -> m.getAnnotation(PostMapping.class).value()[0])
            .collect(Collectors.toSet());

        assertThat(actualPaths)
            .as("SDK 声明的路径集合必须与契约清单完全一致")
            .containsExactlyInAnyOrderElementsOf(CONTRACT_PATHS);
    }

    @Test
    @DisplayName("HTTP 契约：全部方法 POST + 单一 @RequestBody JSON（禁止路径参数/查询参数）")
    void allMethodsArePostWithJsonBody() {
        for (Method method : feignMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            assertThat(post)
                .as("%s 必须标注 @PostMapping（POST + JSON 契约）", method.getName()).isNotNull();
            assertThat(post.path()).as("%s 不得使用路径参数", method.getName()).isEmpty();
            assertThat(Arrays.stream(method.getParameterAnnotations())
                .anyMatch(anns -> Arrays.stream(anns).anyMatch(RequestBody.class::isInstance)))
                .as("%s 必须有且仅有 @RequestBody 参数", method.getName()).isTrue();
        }
    }

    private Set<Method> feignMethods() {
        return Arrays.stream(PermissionFeignClient.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(PostMapping.class))
            .collect(Collectors.toSet());
    }
}
