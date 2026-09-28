package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作准入快照响应体（T-ACCESS-059，契约总册 §25.2 / 设计 §8.4）。
 * <p>
 * 版本化新协议（N22 新旧 schema 命名空间隔离——与旧 {@code InterfaceSnapshotResp}
 * 的 API:ACCESS 放行结构互不复用）。routes[] 携带该服务完整启用路由与各自要求，
 * 不按用户权限裁剪；operationCandidates[] 为最终准入投影（原始 GrantFact 不去重合并，
 * 按 type-operation＋条件身份＋候选类别归并）。本地判定序（网关固定）：
 * 校验模式/版本/时效→完整路由匹配与歧义检测→唯一要求→评该要求条件分支；
 * 存在无条件或条件通过分支即 MAY_ENTER，无通过分支但有需远端求值候选则回源在线判定，
 * 其余拒绝；坏条件显式不可用，不变无条件。
 * </p>
 *
 * @param schemaVersion       协议版本（当前 1；未知版本消费方按配置故障处理）
 * @param tenantId            租户 ID
 * @param subject             被检查主体（可信调用方断言）
 * @param serviceCode         服务编码
 * @param generatedAt         构建时刻
 * @param expiresAt           失效时刻（过期快照不得继续本地判定）
 * @param configGeneration    配置代次（构建期自一致校验；接收侧代次匹配检查）
 * @param routes              完整启用路由与各自准入要求
 * @param operationCandidates 上述要求的候选分支投影
 * @param authorizationStage  恒 OPERATION_ADMISSION
 * @param finalCheckRequired  恒 true（快照不携带绕过业务最终检查的证明）
 */
public record InterfaceAdmissionSnapshotResp(
    int schemaVersion,
    Long tenantId,
    Subject subject,
    String serviceCode,
    LocalDateTime generatedAt,
    LocalDateTime expiresAt,
    long configGeneration,
    List<RouteEntry> routes,
    List<OperationCandidateEntry> operationCandidates,
    String authorizationStage,
    boolean finalCheckRequired
) {

    /** 当前协议版本。 */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** 被检查主体（网关断言的主体业务键回显）。 */
    public record Subject(String subjectTypeCode, String subjectExternalId) {}

    /**
     * 启用路由条目（method + 路径模式 + 准入要求）。
     */
    public record RouteEntry(String httpMethod, String pathPattern, AdmissionRequirement requiredPermission) {}

    /**
     * 准入候选分支投影。
     * <p>
     * 每个元素承载一个条件身份（无条件用 {@code conditionId=null}）＋一个候选类别
     * （ALL/INSTANCE/CONTEXT_DEFERRED）；仅上下文子行提供的候选保留 CONTEXT_DEFERRED，
     * 不伪装成主授权。条件候选在 gatewayEvaluable=true 且规则类型全在四类型白名单内时
     * 内联 conditionRules 规则原文（String，{logic, items[]} 形态）；其余省略该字段、
     * 网关按需回源。CONTEXT_DEFERRED 分支恒 gatewayEvaluable=false（网关不做父运行时
     * 判定，业务必须传真实父上下文）。
     * </p>
     *
     * @param resourceTypeCode 业务资源类型码
     * @param operationCode    操作码
     * @param conditionId      条件 ID；无条件分支为 null
     * @param gatewayEvaluable 该分支条件可否本地评估（false＝需回源，不表示通过）
     * @param candidateKind    候选类别 ALL / INSTANCE / CONTEXT_DEFERRED
     * @param conditionRules   条件规则原文 JSON；无条件或不可下发时为 null
     */
    public record OperationCandidateEntry(
        String resourceTypeCode,
        String operationCode,
        Long conditionId,
        boolean gatewayEvaluable,
        String candidateKind,
        String conditionRules
    ) {}
}
