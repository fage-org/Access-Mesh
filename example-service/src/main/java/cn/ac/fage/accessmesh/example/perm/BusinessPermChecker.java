package cn.ac.fage.accessmesh.example.perm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 业务最终检查门面（T-ACCESS-061，设计 §8.6 / 契约 §25.7）。
 * <p>
 * §8.6 逐路由最终检查的唯一调用面：主体/租户只取自可信认证链（Gateway 注入且已经
 * {@code GatewaySignatureFilter} 验签的请求头值，经 Controller 显式传入），实际目标
 * 由业务代码从业务请求解析——请求 DTO 不携带任何主体/租户字段，客户端无法自报。
 * 网关操作准入（MAY_ENTER）只是第一层，本门禁是最后一道实例级检查，两者跨 HTTP
 * 两次执行、不共享运行状态。
 * </p>
 * <p>
 * 环境上下文（clientIp）由 Controller 从网关清洗重建后的 {@code X-Forwarded-For}
 * （单一可信来源=T-GW-008 下游统一消费口径）取出显式传入；缺失时不传——IP 类条件
 * 由引擎按缺上下文拒绝（fail-closed）。异步作业在提交时捕获该值、执行时点重放。
 * </p>
 * <p>
 * 失败语义 fail-closed：Feign 传输异常、信封 code≠200、data=null 一律视为不可判定，
 * 抛 30005 拒绝业务——不因鉴权服务故障放行任何数据（§8.6「不能检查 A 却按 B 取数」
 * 的对偶面：不能判定时也不取数）。
 * </p>
 */
@Component
@EnableConfigurationProperties(ExamplePermissionProperties.class)
public class BusinessPermChecker {

    private static final Logger log = LoggerFactory.getLogger(BusinessPermChecker.class);
    private static final String SUBJECT_TYPE_LOCAL_USER = "LOCAL_USER";

    /** SDK 契约条件评估上下文的受信 IP 键（CallerContext.KEY_CLIENT_IP 线格式，与网关 PermissionClient 同款）。 */
    private static final String CONTEXT_KEY_CLIENT_IP = "clientIp";

    private final PermissionFeignClient permissionClient;
    private final ExamplePermissionProperties credentials;

    public BusinessPermChecker(PermissionFeignClient permissionClient, ExamplePermissionProperties credentials) {
        this.permissionClient = permissionClient;
        this.credentials = credentials;
    }

    /** 单目标 DECISION（§8.6 查看/创建/导出/子权限行）。 */
    public Decision check(String tenantId, String userId, String clientIp, Target target) {
        AuthCheckResp resp = call(tenantId, credential -> permissionClient.checkAuth(new AuthCheckReq(
            SUBJECT_TYPE_LOCAL_USER, userId,
            target.resourceTypeCode(), target.resourceCode(), target.operationCode(),
            null, null, null,
            target.parentResourceTypeCode(), target.parentResourceCode(), target.parentCodeType(),
            target.parentOperationCodes(), contextOf(clientIp)), credential.credentialId(), credential.credentialSecret()));
        return new Decision(resp.allowed(), resp.reason());
    }

    /**
     * 独立批量（§8.6）：每个目标一个 DECISION 项、一次 batch-check 调用，结果按
     * resourceCode 对齐返回——任一允许不放行整批，全拒/允许子集由调用方业务决定。
     */
    public Map<String, Decision> batchCheck(String tenantId, String userId, String clientIp,
                                            String resourceTypeCode,
                                            List<String> resourceCodes, String operationCode) {
        List<BatchAuthCheckReq.AuthCheckItem> items = new ArrayList<>(resourceCodes.size());
        for (String code : resourceCodes) {
            items.add(new BatchAuthCheckReq.AuthCheckItem(resourceTypeCode, code, operationCode, null, null, null));
        }
        BatchAuthCheckResp resp = call(tenantId, credential -> permissionClient.batchCheckAuth(
            new BatchAuthCheckReq(SUBJECT_TYPE_LOCAL_USER, userId, items,
                null, null, null, null, contextOf(clientIp)), credential.credentialId(), credential.credentialSecret()));
        Map<String, Decision> byCode = new LinkedHashMap<>();
        for (BatchAuthCheckResp.AuthCheckItemResult item : resp.items()) {
            byCode.put(item.resourceCode(), new Decision(item.allowed(), item.reason()));
        }
        return byCode;
    }

    /**
     * 范围查询（§8.6 列表/搜索行）：取该类型+操作下主体可访问的业务码集合，业务侧
     * 以同口径过滤数据与统计 total——分页 total 与返回数据必须来自同一权限范围。
     * {@code scopeMode=ALL}（类型级全量授权）以 {@link Scope#all} 表达——此时
     * 条目 resourceCode 为 null，按码过滤会漏掉全部数据，须以 {@link Scope#contains} 消费。
     */
    public Scope accessibleScope(String tenantId, String userId, String clientIp,
                                 String resourceTypeCode, String operationCode) {
        QueryResourcesResp resp = call(tenantId, credential -> permissionClient.queryResources(
            new QueryResourcesReq(SUBJECT_TYPE_LOCAL_USER, userId,
                List.of(resourceTypeCode), List.of(operationCode),
                null, null, null, null, contextOf(clientIp)), credential.credentialId(), credential.credentialSecret()));
        boolean all = false;
        Set<String> codes = new LinkedHashSet<>();
        for (QueryResourcesResp.ResourceEntry entry : resp.items()) {
            if (entry.scopeMode() == ScopeMode.ALL) {
                all = true;
            } else if (entry.resourceCode() != null) {
                codes.add(entry.resourceCode());
            }
        }
        return new Scope(all, codes);
    }

    /** 每次调用按可信租户选凭证；不依赖线程或可变拦截器状态。 */
    private <T> T call(String tenantId, Function<ExamplePermissionProperties.Credential, R<T>> call) {
        var credential = credentials.require(tenantId);
        try {
            R<T> envelope = call.apply(credential);
            if (envelope == null || envelope.getCode() != 200 || envelope.getData() == null) {
                log.warn("Business final check unavailable: envelope={}, tenantId={}",
                    envelope == null ? "null" : envelope.getCode() + "/" + envelope.getMessage(), tenantId);
                throw unavailable();
            }
            return envelope.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Feign 传输异常/解码异常等：不可判定即拒绝（fail-closed，不 stale-allow）
            log.warn("Business final check transport failure (tenantId={})", tenantId, e);
            throw unavailable();
        }
    }

    /** 条件评估上下文：仅承载受信 clientIp（缺省不传，IP 类条件由引擎 fail-closed）。 */
    private static Map<String, Object> contextOf(String clientIp) {
        return clientIp == null ? null : Map.of(CONTEXT_KEY_CLIENT_IP, clientIp);
    }

    private static BizException unavailable() {
        return new BizException(ExampleErrorCode.PERM_CHECK_UNAVAILABLE.getCode(),
            ExampleErrorCode.PERM_CHECK_UNAVAILABLE.getMessage());
    }

    /** 最终检查目标（值对象）：类型/实例码/操作 + 可选 depend_on 父上下文（T-PERM-058 字段族）。 */
    public record Target(String resourceTypeCode, String resourceCode, String operationCode,
                         String parentResourceTypeCode, String parentResourceCode,
                         String parentCodeType, List<String> parentOperationCodes) {

        public static Target of(String type, String code, String op) {
            return new Target(type, code, op, null, null, null, null);
        }

        /** depend_on 子权限目标：携带真实父资源与父操作（§8.6 上下文子权限行——无父/错父由引擎拒绝）。 */
        public static Target childOf(String type, String code, String op,
                                     String parentType, String parentCode, List<String> parentOps) {
            return new Target(type, code, op, parentType, parentCode, null, parentOps);
        }
    }

    /** 最终检查判定结果（引擎 DECISION 投影：allowed + 拒绝原因）。 */
    public record Decision(boolean allowed, String reason) {
    }

    /** 可访问范围（引擎 GRANT_LIST 投影）：all=类型级全量授权（不按码过滤）；codes=实例级可访问业务码。 */
    public record Scope(boolean all, Set<String> codes) {

        public boolean contains(String code) {
            return all || codes.contains(code);
        }
    }
}
