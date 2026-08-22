package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheInvalidator;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 可切换 fail-mode 已删除的测试级证明（T-ACCESS-011 验收 13）。
 * <p>
 * 设计依据 access-service-architecture §7.2（2026-08-11 取消 gateway.md 可切换
 * fail-mode、open 与 stale-allow；T-ACCESS-008 落地删除）。证据三件套：
 * 配置（application.yml 无活跃键）、代码（GatewayProperties 全嵌套字段无 failMode/
 * open/stale 系列字段）、指标（PermissionFilter 注册表无 open/stale 计数器，
 * gateway.perm.fallback 恒 mode=closed）。
 * 删除历史注记（注释中提及"已删除"）不构成残留，不在断言范围。
 * </p>
 */
class GatewayFailModeRemovalTest {

    /** 已删除的可切换语义字段名（含历史 stale-grace-seconds 配置键的驼峰形式）。 */
    private static final List<String> REMOVED_FIELD_NAMES =
        List.of("failMode", "open", "staleAllow", "staleGraceSeconds");

    @Test
    @DisplayName("代码：GatewayProperties 全嵌套结构不含 failMode/open/stale 系列字段（按叶子名判定）")
    void gatewayPropertiesHasNoFailModeFields() {
        List<String> leafNames = new ArrayList<>();
        collectFieldNames(GatewayProperties.class, "", new ArrayList<>(), leafNames);
        assertThat(leafNames)
            .as("GatewayProperties 全部字段叶子名（含任意嵌套层级）：%s", leafNames)
            .doesNotContain(REMOVED_FIELD_NAMES.toArray(String[]::new));
    }

    /**
     * 递归收集属性类全部嵌套字段（含父类）。
     * 评审修复：断言按「叶子名」判定——前缀限定名（如 permission.failMode）与裸名
     * （failMode）必须同时拒绝，否则嵌套类内复活该字段将逃逸检测。
     */
    private void collectFieldNames(Class<?> type, String prefix, List<String> prefixed, List<String> leaves) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                prefixed.add(prefix + f.getName());
                leaves.add(f.getName());
                Class<?> ft = f.getType();
                if (ft.getName().startsWith("cn.ac.fage.accessmesh.gateway")) {
                    collectFieldNames(ft, prefix + f.getName() + ".", prefixed, leaves);
                }
            }
        }
    }

    @Test
    @DisplayName("配置：application.yml 无 fail-mode/stale-allow/stale-grace-seconds 活跃键")
    void applicationYmlHasNoActiveFailModeKeys() throws Exception {
        Resource resource = new ClassPathResource("application.yml");
        String yml;
        try (InputStream in = resource.getInputStream()) {
            yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // 活跃键判定：行首缩进 + 键名 + 冒号（注释行以 # 开头被排除在 ^\s* 之外需显式过滤）
        Pattern activeKey = Pattern.compile(
            "^\\s*(fail-mode|stale-allow|stale-grace-seconds)\\s*:", Pattern.MULTILINE);
        List<String> hits = new ArrayList<>();
        for (String line : yml.split("\n")) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) {
                continue; // 删除历史注记允许存在
            }
            if (activeKey.matcher(line).find()) {
                hits.add(line.strip());
            }
        }
        assertThat(hits).as("application.yml 不得存在已删除配置的活跃键").isEmpty();
    }

    @Test
    @DisplayName("指标：PermissionFilter 注册表无 open/stale 计数器，fallback 恒 mode=closed")
    void permissionFilterMetricsContainNoOpenOrStaleCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new PermissionFilter(
            mock(PermissionClient.class),
            mock(CacheService.class),
            mock(InvalidationMarker.class),
            mock(InterfaceSnapshotLoadRegistry.class),
            mock(InterfaceSnapshotCacheInvalidator.class),
            new GatewayProperties(),
            new ObjectMapper(),
            registry);

        List<String> violatingMeters = new ArrayList<>();
        for (Meter meter : registry.getMeters()) {
            String name = meter.getId().getName();
            if (name.contains(".open.") || name.contains(".stale")) {
                violatingMeters.add(meter.getId().toString());
                continue;
            }
            if ("gateway.perm.fallback".equals(name)) {
                String mode = meter.getId().getTag("mode");
                if (!"closed".equals(mode)) {
                    violatingMeters.add(meter.getId().toString());
                }
            }
        }
        assertThat(violatingMeters)
            .as("固定 fail-closed：不得注册 open/stale 系列计数器，fallback 仅 mode=closed")
            .isEmpty();
    }

    @Test
    @DisplayName("权限回源不可达路径固定 503：PermissionFilter 不可达分支语义由 PermissionFilterTest 覆盖，此处钉住不可达计数器存在")
    void unreachableCountersExistForFailClosedPath() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new PermissionFilter(
            mock(PermissionClient.class),
            mock(CacheService.class),
            mock(InvalidationMarker.class),
            mock(InterfaceSnapshotLoadRegistry.class),
            mock(InterfaceSnapshotCacheInvalidator.class),
            new GatewayProperties(),
            new ObjectMapper(),
            registry);

        assertThat(registry.getMeters().stream()
            .filter(m -> "gateway.perm.unreachable".equals(m.getId().getName()))
            .count())
            .as("不可达计数器（source=snapshot/check_interface）必须注册")
            .isGreaterThanOrEqualTo(2);
        // 评审修复：fail-closed 拒绝路径的计数器必须存在（denied + deadline_exceeded），
        // 否则「fallback 仅 mode=closed」检查在计数器整体缺失时为假阴性
        assertThat(registry.getMeters().stream()
            .filter(m -> "gateway.perm.fallback".equals(m.getId().getName()))
            .count())
            .as("fail-closed fallback 计数器（mode=closed）必须注册")
            .isGreaterThanOrEqualTo(2);
    }
}
