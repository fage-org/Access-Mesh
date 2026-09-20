package cn.ac.fage.accessmesh.perm.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 服务凭证头注入拦截器（T-PERM-070，service-authentication.md §3.3「SDK 直连」形态）。
 * <p>
 * 落 perm-common 供 perm-client（既有）与 perm-registration（T-PERM-071 新增）两 starter
 * 共用——starter 的 AutoConfiguration 须显式 {@code @Import} 装配（业务服务组件扫描
 * 覆盖不到 SDK 包，同 FeignInternalSyncInterceptor 先例）。仅当配置了
 * {@code perm.credential-id} 时启用；上线序=服务端先行向后兼容（旧密钥注入
 * FeignInternalSyncInterceptor 不受影响，两拦截器并存期间「凭证头+密钥头」由服务端
 * 仲裁器凭证优先规则消解）。
 * </p>
 * <p>
 * <b>注入面=M2M 通道端点精确清单</b>（2026-09-20 拍板「精确镜像」）：仅对
 * {@link #M2M_PATHS} 中的端点注入凭证头——auth/check、batch-check 等运行时鉴权端点
 * 不注入（服务端仲裁器凭证优先+白名单外 403，宽注入面会击穿接入方自身鉴权链）。
 * 本清单是 {@code common.security.M2mCredentialEndpoints}（服务端+Gateway 单源）的
 * <b>SDK 侧镜像副本</b>——SDK 对外发制品不能依赖 common 模块；阶段二扩展白名单
 * （071 及后续）时<b>必须同批同步两处</b>，两侧测试各自锁清单防漂移。
 * </p>
 * <p>
 * 配置键：
 * <ul>
 *   <li>{@code perm.credential-id} + {@code perm.credential-secret}：成对必填——只配一半
 *       启动失败（fail-fast，对齐 perm.service-code 先例防半配误用）。</li>
 *   <li>{@code perm.allow-insecure}：<b>启动声明式 TLS 信任域护栏</b>（2026-09-20 拍板）——
 *       配置了凭证就必须显式声明（true=本部署整体一个信任域、明文 hop 可接受；
 *       false=跨边界部署、期望 TLS；本护栏纯声明不校验实际地址——服务发现形态下
 *       静态校验无落点）。缺省（未声明）配置凭证即拒绝启动。</li>
 * </ul>
 * 已显式声明凭证头的请求模板不覆盖（多租户差异化场景由调用方自行指定）。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "perm.credential-id")
public class FeignCredentialInterceptor implements RequestInterceptor {

    public static final String HEADER_CREDENTIAL_ID = "X-Credential-Id";
    public static final String HEADER_CREDENTIAL_SECRET = "X-Credential-Secret";

    /** 内部调用统一路径前缀；仅匹配该前缀的请求才考虑注入，避免污染其他 Feign 调用。 */
    private static final String ACCESS_PATH_MARKER = "/api/access/";

    /**
     * M2M 通道端点精确清单（common.security.M2mCredentialEndpoints 的 SDK 侧镜像副本，
     * 同步义务见类注释；method 恒 POST）。
     */
    private static final java.util.Set<String> M2M_PATHS = java.util.Set.of(
        "/api/access/resource-entity/sync",
        "/api/access/resource-entity/full-sync",
        "/api/access/integration/permission-manifest/full-sync");

    @Value("${perm.credential-id}")
    private String credentialId;

    @Value("${perm.credential-secret:}")
    private String credentialSecret;

    /** TLS 信任域声明（三态：null=未声明→配置凭证即拒启；true/false=有效声明）。 */
    @Value("${perm.allow-insecure:}")
    private String allowInsecure;

    @PostConstruct
    void validateConfiguration() {
        if (credentialId == null || credentialId.isBlank()) {
            throw new IllegalStateException(
                "perm.credential-id 配置为空白——服务凭证标识不可为空（sc- 前缀签发值）");
        }
        if (credentialSecret == null || credentialSecret.isBlank()) {
            throw new IllegalStateException(
                "perm.credential-id 已配置但 perm.credential-secret 缺失——服务凭证必须成对配置"
                + "（X-Credential-Id/X-Credential-Secret 头二进制缺一，半配置请求会被服务端 403 拒绝）");
        }
        if (allowInsecure == null || allowInsecure.isBlank()) {
            throw new IllegalStateException(
                "配置了 perm.credential-id/secret 但未显式声明 perm.allow-insecure——"
                + "凭证 secret 是可重放 bearer（无签名/nonce），必须显式声明信任域模型后才能发送："
                + "纯内网/单信任域部署设 perm.allow-insecure=true（明文 hop 可接受）；"
                + "跨信任边界部署设 false（期望 TLS）——本护栏为启动声明式（不校验实际地址），"
                + "见 docs/design/service-authentication.md §3.2 TLS 信任域模型");
        }
        if (!"true".equalsIgnoreCase(allowInsecure.trim()) && !"false".equalsIgnoreCase(allowInsecure.trim())) {
            // 二值白名单（2026-09-20 claude 外评 P3-2 拍板收紧）：拼写错（flase 等）当场
            // fail-fast——纯声明护栏 true/false 零运行时行为差异，此处仅防操作者心智模型
            // 漂移（以为已声明跨边界实际是错值）
            throw new IllegalStateException(
                "perm.allow-insecure 值不合法：" + allowInsecure + "（仅允许 true/false——"
                + "true=单信任域明文 hop 可接受；false=跨边界期望 TLS）");
        }
    }

    @Override
    public void apply(RequestTemplate template) {
        String url = template.url();
        int markerIndex = url == null ? -1 : url.indexOf(ACCESS_PATH_MARKER);
        if (markerIndex < 0) {
            return;
        }
        // url 可能是相对路径或完整 URL（http://host/api/access/...）——统一截取
        // /api/access/ 起的路径段（去 query）后精确匹配
        String path = url.substring(markerIndex);
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        if (!M2M_PATHS.contains(path)) {
            // 仅 M2M 通道端点注入（精确镜像清单）——auth/check 等其他 /api/access/ 调用
            // 不携带凭证头（白名单外携带=服务端 403，见类注释）
            return;
        }
        if (!hasHeader(template, HEADER_CREDENTIAL_ID)) {
            template.header(HEADER_CREDENTIAL_ID, credentialId);
        }
        if (!hasHeader(template, HEADER_CREDENTIAL_SECRET)) {
            template.header(HEADER_CREDENTIAL_SECRET, credentialSecret);
        }
    }

    private static boolean hasHeader(RequestTemplate template, String name) {
        java.util.Collection<String> existing = template.headers().get(name);
        return existing != null && !existing.isEmpty();
    }
}
