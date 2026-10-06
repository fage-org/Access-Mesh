package cn.ac.fage.accessmesh.access.engine.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.access.engine.query.ByCode;
import cn.ac.fage.accessmesh.access.engine.query.CallerContext;
import cn.ac.fage.accessmesh.access.engine.query.DecisionResult;
import cn.ac.fage.accessmesh.access.engine.query.FactDetail;
import cn.ac.fage.accessmesh.access.engine.query.Inheritance;
import cn.ac.fage.accessmesh.access.engine.query.OutputSpec;
import cn.ac.fage.accessmesh.access.engine.query.ParentRequirement;
import cn.ac.fage.accessmesh.access.engine.query.PresentationExpansion;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryItem;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.QueryResult;
import cn.ac.fage.accessmesh.access.engine.query.ReadOptions;
import cn.ac.fage.accessmesh.access.engine.query.Selection;
import cn.ac.fage.accessmesh.access.engine.query.TargetClause;
import cn.ac.fage.accessmesh.access.engine.query.TargetSet;
import cn.ac.fage.accessmesh.access.engine.query.TypeFallback;
import cn.ac.fage.accessmesh.access.engine.query.TypeLevel;
import cn.ac.fage.accessmesh.access.engine.query.TypeOperation;
import cn.ac.fage.accessmesh.access.engine.query.User;
import cn.ac.fage.accessmesh.access.engine.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.PermResultUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 权限检查应用服务实现
 * <p>
 * 提供纯校验功能：单次校验、批量校验。
 * check/batchCheck 全部直构 QueryRequest
 * 经新 execute；外层职责保留——主体业务键解析、USER_NOT_FOUND 缺省、原序/重复项
 * （按下标 item key）、请求级父上下文与请求级单一评估时刻〔RunState 单时钟〕。
 * </p>
 */
@Service
public class PermissionCheckAppServiceImpl implements PermissionCheckAppService {


    private final TypeResolutionService typeResolutionService;
    private final QueryExecutionEngine queryEngine;

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService 类型解析服务
     * @param queryEngine            新查询执行器（check/batchCheck）
     */
    public PermissionCheckAppServiceImpl(TypeResolutionService typeResolutionService,
                                         QueryExecutionEngine queryEngine) {
        this.typeResolutionService = typeResolutionService;
        this.queryEngine = queryEngine;
    }

    /**
     * 单次权限校验
     * <p>
     * 校验指定用户对某资源的某操作是否有权限。
     * 无编码目标（含空白串归一，2026-09-27 用户拍板）= TYPE_LEVEL；有编码目标 =
     * 单 clause TARGET_SET，判定面继承按 inheritMode 显式开（PARENT/BOTH）。
     * context.clientIp 提取为受信 IP（SDK 契约）；顶层 evaluatedAt/timestamp 键
     * 不再静默处理——CallerContext 结构拒绝（400 VALIDATION_FAILED，2026-09-27 外评处置修订）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      校验请求，包含用户标识、资源类型、资源编码、操作码等
     * @return 校验响应，包含是否允许、拒绝原因、命中结果记录与条件评估状态（T-API-003：matched id 字段族恢复回传）
     */
    @Override
    @Transactional(readOnly = true)
    public AuthCheckResp check(Long tenantId, AuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return AuthCheckResp.deny("USER_NOT_FOUND", false);

        QueryItem item = QueryItem.decision("check", selection(req.resourceTypeCode(), req.resourceCode(),
            req.operationCode(), req.codeType(), req.domainCode(), inheritClosureOf(req.inheritMode()),
            parentRequirement(req.parentResourceTypeCode(), req.parentResourceCode(),
                req.parentCodeType(), req.parentOperationCodes())), checkOutput());
        QueryResult result = queryEngine.execute(new QueryRequest(tenantId, new User(userId),
            callerContext(req.context()), ReadOptions.defaults(), List.of(item)));
        return PermResultUtils.toAuthCheckResp((DecisionResult) result.orderedResults().get(0));
    }

    /**
     * 批量权限校验
     * <p>
     * 多个独立 DECISION item 一次 execute 批量表达（禁循环 N 次公开 execute）：
     * item key=输入下标（原序/重复项天然对齐）；请求级单一评估时刻由新引擎
     * RunState 单时钟承担（旧 a2 定案的钉住语义等价）；请求级父上下文＝同值
     * ParentRequirement 挂全批 item（首版混批约束下单父共享，惰性判定一次）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量校验请求，包含用户标识和校验项列表
     * @return 批量校验响应，包含每项的校验结果列表
     */
    @Override
    @Transactional(readOnly = true)
    public BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new BatchAuthCheckResp(req.items().stream()
                .map(item -> new AuthCheckItemResult(
                    item.resourceTypeCode(), item.resourceCode(), item.operationCode(), false, "USER_NOT_FOUND",
                    List.of(), List.of(), false))
                .toList());
        }
        CallerContext caller = callerContext(req.context());
        ParentRequirement parent = parentRequirement(req.parentResourceTypeCode(), req.parentResourceCode(),
            req.parentCodeType(), req.parentOperationCodes());
        List<QueryItem> items = new ArrayList<>(req.items().size());
        for (int i = 0; i < req.items().size(); i++) {
            BatchAuthCheckReq.AuthCheckItem item = req.items().get(i);
            items.add(QueryItem.decision(String.valueOf(i), selection(item.resourceTypeCode(),
                item.resourceCode(), item.operationCode(), item.codeType(), item.domainCode(),
                inheritClosureOf(item.inheritMode()), parent), checkOutput()));
        }
        QueryResult result = queryEngine.execute(new QueryRequest(tenantId, new User(userId), caller,
            ReadOptions.defaults(), items));
        List<AuthCheckItemResult> results = new ArrayList<>(req.items().size());
        for (int i = 0; i < req.items().size(); i++) {
            BatchAuthCheckReq.AuthCheckItem item = req.items().get(i);
            DecisionResult outcome = (DecisionResult) result.orderedResults().get(i);
            AuthCheckResp projected = PermResultUtils.toAuthCheckResp(outcome);
            results.add(new AuthCheckItemResult(
                item.resourceTypeCode(), item.resourceCode(), item.operationCode(),
                projected.allowed(), projected.reason(), projected.matchedRoleIds(),
                projected.matchedPermissionIds(), projected.conditionEvaluated()));
        }
        return new BatchAuthCheckResp(List.copyOf(results));
    }

    /**
     * {@code inheritMode} SDK 线格式参数解析（check 族契约口径，T-PERM-092 收编）：
     * "PARENT"/"BOTH" → 判定面继承开；"CHILD"/"NONE"/其他含缺省 → 关。
     *
     * @param inheritMode 继承模式（可 null）
     * @return 判定面继承开关
     */
    private static boolean inheritClosureOf(String inheritMode) {
        return "PARENT".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode);
    }

    /** 目标选择两档：无编码目标（含空白串归一 TYPE_LEVEL，2026-09-27 拍板）或单 clause TARGET_SET。 */
    private static Selection selection(String resourceTypeCode, String resourceCode, String operationCode,
                                        String codeType, String domainCode, boolean inheritClosure,
                                        ParentRequirement parent) {
        TypeOperation operation = new TypeOperation(resourceTypeCode, operationCode);
        if (resourceCode == null || resourceCode.isBlank()) {
            return new TypeLevel(List.of(operation));
        }
        TargetClause clause = new TargetClause(operation, new ByCode(resourceCode, codeType, domainCode));
        return new TargetSet(List.of(clause),
            inheritClosure ? Inheritance.SELF_AND_ANCESTORS : Inheritance.SELF,
            TypeFallback.ALLOW, parent);
    }

    /**
     * 主资源上下文构造（null 元素防御过滤——SDK 请求经 JSON 反序列化可含 null 值）。
     * 退化输入归一（外评 P2，2026-09-27 claude+grok 双通道，与空白目标编码拍板同口径）：
     * 父类型/父编码任一为空白串时按无父处理（旧链路空白父编码解析必落空=父判定不命中、
     * 主行照常判定；depend_on 子行 fail-closed），避免新引擎 ByCode 空白码触发结构拒绝（400）；
     * 父操作集过滤后空集＝父判定必不命中，语义等价于无父，同归一。
     */
    private static ParentRequirement parentRequirement(String parentResourceTypeCode, String parentResourceCode,
                                                        String parentCodeType, List<String> operationCodes) {
        if (parentResourceTypeCode == null || parentResourceTypeCode.isBlank()
            || parentResourceCode == null || parentResourceCode.isBlank()) {
            return null;
        }
        Set<String> filtered = operationCodes == null ? Set.of()
            : Set.copyOf(operationCodes.stream().filter(Objects::nonNull).toList());
        if (filtered.isEmpty()) {
            return null;
        }
        return new ParentRequirement(parentResourceTypeCode,
            new ByCode(parentResourceCode, parentCodeType, null), filtered);
    }

    /** 条件参与事实须保留评估前候选；只在适配层派生布尔值，不向外开放原始事实或 TRACE。 */
    private static OutputSpec checkOutput() {
        return OutputSpec.rawAndKept();
    }

    /** SDK 契约 {@code context.clientIp} 键提取为受信 IP，其余键归调用方属性；
     *  顶层 evaluatedAt/timestamp 键由 CallerContext 结构拒绝（400 VALIDATION_FAILED，
     *  2026-09-27 外评处置修订；T-PERM-090 起统一走 {@link CallerContext#fromCallerMap} 公共工厂）。 */
    private static CallerContext callerContext(Map<String, Object> context) {
        return CallerContext.fromCallerMap(context);
    }
}
