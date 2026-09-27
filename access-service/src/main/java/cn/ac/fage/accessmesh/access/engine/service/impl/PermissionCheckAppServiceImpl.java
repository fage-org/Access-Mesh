package cn.ac.fage.accessmesh.access.engine.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.access.engine.dto.CheckInterfaceResp;
import cn.ac.fage.accessmesh.access.engine.query.ByCode;
import cn.ac.fage.accessmesh.access.engine.query.CallerContext;
import cn.ac.fage.accessmesh.access.engine.query.DecisionResult;
import cn.ac.fage.accessmesh.access.engine.query.Inheritance;
import cn.ac.fage.accessmesh.access.engine.query.OutputSpec;
import cn.ac.fage.accessmesh.access.engine.query.ParentRequirement;
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
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.engine.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.engine.dto.PermQuery;
import cn.ac.fage.accessmesh.access.engine.util.PermResultUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;

/**
 * 权限检查应用服务实现
 * <p>
 * 提供纯校验功能：单次校验、批量校验、接口级校验。
 * check/batchCheck 已随 T-PERM-089 迁移新 execute（X03 等价迁移：外层职责保留——
 * 主体业务键解析、USER_NOT_FOUND 缺省、原序/重复项（按下标 item key）、请求级父上下文
 * 与请求级单一评估时刻〔RunState 单时钟〕）；checkInterface 仍走旧引擎（LEGACY_API，
 * T-PERM-090 迁移）。
 * </p>
 */
@Service
public class PermissionCheckAppServiceImpl implements PermissionCheckAppService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final TypeResolutionService typeResolutionService;
    private final QueryExecutionEngine queryEngine;
    private final PermQueryEngine engine;
    private final ResourceApiMappingMapper apiMappingMapper;

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService 类型解析服务
     * @param queryEngine            新查询执行器（check/batchCheck）
     * @param engine                 旧权限查询引擎（仅 checkInterface，T-PERM-090 迁移）
     * @param apiMappingMapper       API映射数据访问层
     */
    public PermissionCheckAppServiceImpl(TypeResolutionService typeResolutionService,
                                         QueryExecutionEngine queryEngine,
                                         PermQueryEngine engine,
                                         ResourceApiMappingMapper apiMappingMapper) {
        this.typeResolutionService = typeResolutionService;
        this.queryEngine = queryEngine;
        this.engine = engine;
        this.apiMappingMapper = apiMappingMapper;
    }

    /**
     * 单次权限校验
     * <p>
     * 校验指定用户对某资源的某操作是否有权限。
     * 无编码目标（含空白串归一，2026-09-27 用户拍板）= TYPE_LEVEL；有编码目标 =
     * 单 clause TARGET_SET，判定面继承按 inheritMode 显式开（PARENT/BOTH）。
     * context.clientIp 提取为受信 IP（SDK 契约）；顶层 evaluatedAt/timestamp 键
     * 不再静默处理——CallerContext 结构拒绝（500，2026-09-27 用户拍板）。
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
        if (userId == null) return AuthCheckResp.deny("USER_NOT_FOUND");

        QueryItem item = QueryItem.decision("check", selection(req.resourceTypeCode(), req.resourceCode(),
            req.operationCode(), req.codeType(), req.domainCode(), PermQuery.inheritClosureOf(req.inheritMode()),
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
                    List.of(), List.of()))
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
                PermQuery.inheritClosureOf(item.inheritMode()), parent), checkOutput()));
        }
        QueryResult result = queryEngine.execute(new QueryRequest(tenantId, new User(userId), caller,
            ReadOptions.defaults(), items));
        List<AuthCheckItemResult> results = new ArrayList<>(req.items().size());
        for (int i = 0; i < req.items().size(); i++) {
            BatchAuthCheckReq.AuthCheckItem item = req.items().get(i);
            DecisionResult outcome = (DecisionResult) result.orderedResults().get(i);
            results.add(new AuthCheckItemResult(
                item.resourceTypeCode(), item.resourceCode(), item.operationCode(),
                outcome.outcome() == DecisionResult.Decision.ALLOW,
                outcome.reason() != null ? outcome.reason().name() : null,
                List.copyOf(outcome.details().matchedRoleIds()),
                List.copyOf(outcome.details().matchedPermissionIds())));
        }
        return new BatchAuthCheckResp(List.copyOf(results));
    }

    /**
     * 接口级权限校验
     * <p>
     * 校验用户是否有权访问指定的API接口。
     * 根据服务编码和HTTP方法查找API映射，匹配路径模式，
     * 然后使用PermQuery.forInterfaceCheck校验ACCESS权限。
     * LEGACY_API 迁移期形态（T-PERM-090 经新 execute 表达共同集合语义）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口校验请求，包含用户标识、服务编码、HTTP方法和请求路径
     * @return 接口校验响应，包含是否允许、拒绝原因与匹配资源详情列表（T-API-003：resourceId 与 matched id 字段族恢复回传）
     */
    @Override
    @Transactional(readOnly = true)
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return CheckInterfaceResp.deny("USER_NOT_FOUND");

        List<ResourceApiMapping> mappings = apiMappingMapper.selectForInterfaceCheck(
            tenantId, req.serviceCode(), req.httpMethod());
        if (mappings.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");

        List<ResourceApiMapping> matched = mappings.stream()
            .filter(m -> pathMatches(m.getPathPattern(), req.path())).toList();
        if (matched.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");

        Set<Long> entityIds = matched.stream()
            .map(ResourceApiMapping::getResourceEntityId).filter(Objects::nonNull).collect(Collectors.toSet());

        PermQuery q = PermQuery.forInterfaceCheck(tenantId, userId, Set.of("API"), entityIds, OperationCode.ACCESS);
        q.setEvalContext(cn.ac.fage.accessmesh.access.engine.dto.PermEvalContext.fromCallerMap(req.context()));
        return PermResultUtils.toCheckInterfaceResp(engine.query(q), 30);
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
     * 主行照常判定；depend_on 子行 fail-closed），避免新引擎 ByCode 空白码结构拒绝放大为 500；
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

    /** check 族输出：命中 ID（T-API-003 回传）＋保留事实（conditionEvaluated 从保留事实派生，零额外 I/O）——
     *  即 OutputSpec.kept() 公共工厂形态（外评可裁剪项收口，勿再私有重造同形构造）。 */
    private static OutputSpec checkOutput() {
        return OutputSpec.kept();
    }

    /** SDK 契约 {@code context.clientIp} 键提取为受信 IP，其余键归调用方属性；
     *  顶层 evaluatedAt/timestamp 键由 CallerContext 结构拒绝（2026-09-27 用户拍板，直接 500）。 */
    private static CallerContext callerContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return CallerContext.of(null);
        }
        String clientIp = null;
        Map<String, Object> rest = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (CallerContext.KEY_CLIENT_IP.equals(entry.getKey())) {
                clientIp = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            } else {
                rest.put(entry.getKey(), entry.getValue());
            }
        }
        return new CallerContext(clientIp, rest);
    }

    /**
     * 路径匹配检查
     * <p>
     * 检查请求路径是否匹配路径模式。
     * 支持精确匹配和Ant风格模式匹配（如 /api/**）。
     * </p>
     *
     * @param pattern 路径模式
     * @param path    请求路径
     * @return 是否匹配
     */
    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        return PATH_MATCHER.match(pattern, path);
    }
}
