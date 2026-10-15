package cn.ac.fage.accessmesh.perm.common.feign;

import feign.MethodMetadata;
import feign.RequestInterceptor;
import cn.ac.fage.accessmesh.common.security.M2mCredentialEndpoints;
import feign.RequestTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

/**
 * 服务凭证注入：仅向 M2M 精确端点发送凭证，管理面不注入。
 * 端点清单直接调 common.security.M2mCredentialEndpoints（单源，2026-10-06 逐任务评审清扫「SDK 镜像」退役措辞）。
 * 配置 credential-id/credential-secret 成对必填，allow-insecure 必须显式声明信任域。
 * 仅配置 credential-id 时装配；调用方已声明的凭证头保持原值。
 * <p>
 * 路径匹配（T-ACCESS-098）：真实路径按<b>完整值</b>与 M2M 清单匹配（不子串、不 indexOf），
 * 且仅接受相对路径形态（发现制权限客户端调用；feign RequestTemplate.uri() 结构性拒绝
 * 绝对 URL）——URL 其他位置（其他路径前缀、query 参数值）出现 /api/access/ 子串不注入。
 * 装配边界（T-ACCESS-098）：本类不得以 @Component/@Import 注册进宿主应用上下文
 * （会经 beansOfTypeIncludingAncestors 扩大为宿主全部 Feign 客户端的全局拦截器）；
 * 唯一挂载点为 {@link PermFeignClientsConfiguration}（权限客户端 Feign 子上下文）。
 * </p>
 */
public class FeignCredentialInterceptor implements RequestInterceptor {

    public static final String HEADER_CREDENTIAL_ID = "X-Credential-Id";
    public static final String HEADER_CREDENTIAL_SECRET = "X-Credential-Secret";

    /** 凭证注入唯一合法目标接口（全限定名；本模块类引用不到两 SDK 接口，见 apply 注释）。 */
    private static final String CLIENT_SDK_INTERFACE =
        "cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient";
    private static final String MANIFEST_SDK_INTERFACE =
        "cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient";

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
        // 外评 P2-1 处置（T-ACCESS-098）＋codex sol r2 补强：Feign 子上下文按 contextId
        // 共享——宿主 FeignClientBuilder/同 contextId 客户端可进入权限客户端子上下文取得
        // 本拦截器，故注入以<b>目标接口</b>为唯一判据。判据取 MethodMetadata.targetType()
        // 的全限定类名（本模块无法类引用两 SDK 接口：starter 依赖本模块，反向引用成环；
        // targetType 由各 Contract 解析时绑定，字节码/源码实证）——feign configKey 只含
        // 接口简名，异包同名接口（acme.PermissionFeignClient）可伪造简名门禁（r2 P2-1
        // 红跑实证），简名前缀比对不构成身份。无接口元信息/targetType 的手工模板不注入。
        MethodMetadata metadata = template.methodMetadata();
        if (metadata == null) {
            return;
        }
        Class<?> targetType = metadata.targetType();
        if (targetType == null) {
            return;
        }
        String targetName = targetType.getName();
        if (!CLIENT_SDK_INTERFACE.equals(targetName) && !MANIFEST_SDK_INTERFACE.equals(targetName)) {
            return;
        }
        String url = template.url();
        // T-ACCESS-098：仅相对路径形态参与匹配——绝对 URL 形态被 feign 结构性拒绝，
        // 此处不依赖该前提重复校验 scheme；不以 '/' 开头（如空路径）一律不注入
        if (url == null || url.isEmpty() || url.charAt(0) != '/') {
            return;
        }
        String path = url;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        if (!M2mCredentialEndpoints.matches(template.method(), path)) {
            // 仅 M2M 通道端点按完整路径值注入（common 单源精确清单）——其余调用
            // （含 /api/access/ 子串出现在其他前缀或 query 值中的形态）不携带凭证头
            // （白名单外携带=服务端 403，见类注释）
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
