package cn.ac.fage.accessmesh.example.perm;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
 * 失败语义 fail-closed：Feign 传输异常、信封 code≠200、data=null 一律视为不可判定，
 * 抛 30005 拒绝业务——不因鉴权服务故障放行任何数据（§8.6「不能检查 A 却按 B 取数」
 * 的对偶面：不能判定时也不取数）。
 * </p>
 */
@Component
public class BusinessPermChecker {

    private static final Logger log = LoggerFactory.getLogger(BusinessPermChecker.class);
    private static final String SUBJECT_TYPE_LOCAL_USER = "LOCAL_USER";

    private final PermissionFeignClient permissionClient;

    public BusinessPermChecker(PermissionFeignClient permissionClient) {
        this.permissionClient = permissionClient;
    }

    /** 单目标 DECISION（§8.6 查看/创建/导出/子权限行）。 */
    public Decision check(String tenantId, String userId, Target target) {
        AuthCheckResp resp = call(tenantId, () -> permissionClient.checkAuth(new AuthCheckReq(
            SUBJECT_TYPE_LOCAL_USER, userId,
            target.resourceTypeCode(), target.resourceCode(), target.operationCode(),
            null, null, null,
            target.parentResourceTypeCode(), target.parentResourceCode(), target.parentCodeType(),
            target.parentOperationCodes(), null)));
        return new Decision(resp.allowed(), resp.reason());
    }

    /**
     * 独立批量（§8.6）：每个目标一个 DECISION 项、一次 batch-check 调用，结果按
     * resourceCode 对齐返回——任一允许不放行整批，全拒/允许子集由调用方业务决定。
     */
    public Map<String, Decision> batchCheck(String tenantId, String userId, String resourceTypeCode,
                                            List<String> resourceCodes, String operationCode) {
        List<BatchAuthCheckReq.AuthCheckItem> items = new ArrayList<>(resourceCodes.size());
        for (String code : resourceCodes) {
            items.add(new BatchAuthCheckReq.AuthCheckItem(resourceTypeCode, code, operationCode, null, null, null));
        }
        BatchAuthCheckResp resp = call(tenantId, () -> permissionClient.batchCheckAuth(
            new BatchAuthCheckReq(SUBJECT_TYPE_LOCAL_USER, userId, items, null, null, null, null, null)));
        Map<String, Decision> byCode = new LinkedHashMap<>();
        for (BatchAuthCheckResp.AuthCheckItemResult item : resp.items()) {
            byCode.put(item.resourceCode(), new Decision(item.allowed(), item.reason()));
        }
        return byCode;
    }

    /**
     * 范围查询（§8.6 列表/搜索行）：取该类型+操作下主体可访问的业务码集合，业务侧
     * 以同口径过滤数据与统计 total——分页 total 与返回数据必须来自同一权限范围。
     */
    public Set<String> accessibleCodes(String tenantId, String userId, String resourceTypeCode,
                                       String operationCode) {
        QueryResourcesResp resp = call(tenantId, () -> permissionClient.queryResources(
            new QueryResourcesReq(SUBJECT_TYPE_LOCAL_USER, userId,
                List.of(resourceTypeCode), List.of(operationCode), null, null, null, null, null)));
        Set<String> codes = new LinkedHashSet<>();
        for (QueryResourcesResp.ResourceEntry entry : resp.items()) {
            codes.add(entry.resourceCode());
        }
        return codes;
    }

    /** 统一调用包装：绑定租户上下文（拦截器注入 X-Tenant-Id）+ fail-closed 信封解析。 */
    private <T> T call(String tenantId, Supplier<R<T>> call) {
        PermCallContext.setTenantId(tenantId);
        try {
            R<T> envelope = call.get();
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
        } finally {
            PermCallContext.clear();
        }
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
}
