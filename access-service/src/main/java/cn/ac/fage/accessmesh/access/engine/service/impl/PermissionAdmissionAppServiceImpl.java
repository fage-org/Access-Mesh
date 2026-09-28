package cn.ac.fage.accessmesh.access.engine.service.impl;

import cn.ac.fage.accessmesh.access.engine.query.AdmissionResult;
import cn.ac.fage.accessmesh.access.engine.query.CallerContext;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.OutputSpec;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryItem;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.QueryResult;
import cn.ac.fage.accessmesh.access.engine.query.ReadOptions;
import cn.ac.fage.accessmesh.access.engine.query.TypeOperation;
import cn.ac.fage.accessmesh.access.engine.query.User;
import cn.ac.fage.accessmesh.access.engine.util.InterfaceAdmissionSnapshotAssembler;
import cn.ac.fage.accessmesh.access.engine.util.InterfaceAdmissionSnapshotAssembler.RouteRequirement;
import cn.ac.fage.accessmesh.access.engine.service.PermissionAdmissionAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.CallerType;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 操作准入应用服务实现（T-ACCESS-059，契约总册 §25.2 / 设计 §8.4）。
 * <p>
 * 在线判定与快照构建共用路由匹配与要求解析（本地投影与在线一致，N12）：
 * service＋method＋规范化路径从完整启用路由集取全部命中，多匹配同要求去重、
 * 异要求 20070；引用悬空 20071。快照构建带配置代次自一致校验（构建前后复读比对，
 * 变更即废弃重建）；代次复读走独立 {@code selectConfigGeneration} 语句且
 * flushCache=true 强制落库——仅独立语句不够：同语句同参数二次调用仍会命中
 * MyBatis SESSION 级一级缓存，自一致比对恒相等（2026-09-28 外评核实修正）。
 * 代次稳定后另有终校验（独立 {@code selectAuthState} 语句复读启停）：入口校验
 * 与首次代次读之间停用不改变代次比对结果，须在返回前确认服务状态仍适用。
 * 要求解析按映射集一次批量完成（操作行 + 类型反查各一条，禁止逐路由点查）。
 * </p>
 */
@Service
public class PermissionAdmissionAppServiceImpl implements PermissionAdmissionAppService {

    private static final Logger log = LoggerFactory.getLogger(PermissionAdmissionAppServiceImpl.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 构建重试上限：持续并发变更按技术故障失败关闭（不无限重试）。 */
    private static final int MAX_BUILD_ATTEMPTS = 3;

    /**
     * 快照建议有效期（{@link PermissionAdmissionAppService#SNAPSHOT_TTL}，T-ACCESS-060 边界
     * 推导与启动校验方程见常量注释）。
     */
    private static final Duration SNAPSHOT_TTL = PermissionAdmissionAppService.SNAPSHOT_TTL;

    private final QueryExecutionEngine queryEngine;
    private final TypeResolutionService typeResolutionService;
    private final ResourceApiMappingMapper apiMappingMapper;
    private final ServiceConfigMapper serviceConfigMapper;
    private final InterfaceAdmissionSnapshotAssembler snapshotAssembler;

    public PermissionAdmissionAppServiceImpl(QueryExecutionEngine queryEngine,
                                             TypeResolutionService typeResolutionService,
                                             ResourceApiMappingMapper apiMappingMapper,
                                             ServiceConfigMapper serviceConfigMapper,
                                             InterfaceAdmissionSnapshotAssembler snapshotAssembler) {
        this.queryEngine = queryEngine;
        this.typeResolutionService = typeResolutionService;
        this.apiMappingMapper = apiMappingMapper;
        this.serviceConfigMapper = serviceConfigMapper;
        this.snapshotAssembler = snapshotAssembler;
    }

    @Override
    @Transactional(readOnly = true)
    public InterfaceAdmissionResp interfaceAdmission(Long tenantId, InterfaceAdmissionReq req) {
        requireOwnedService(tenantId, req.serviceCode());
        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, req.serviceCode());
        if (config == null || config.getStatus() == null || config.getStatus() != 1) {
            // 未登记/停用＝该服务无参与授权的路由（沿 check-interface 无注册拒绝口径）
            return InterfaceAdmissionResp.notRegistered();
        }
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return InterfaceAdmissionResp.userNotFound();

        List<ResourceApiMapping> mappings = apiMappingMapper.selectForInterfaceCheck(
            tenantId, req.serviceCode(), req.httpMethod());
        List<ResourceApiMapping> matched = mappings.stream()
            .filter(m -> pathMatches(m.getPathPattern(), req.path())).toList();
        if (matched.isEmpty()) return InterfaceAdmissionResp.notRegistered();

        List<RouteRequirement> resolved = snapshotAssembler.resolveRouteRequirements(tenantId, matched);
        LinkedHashSet<AdmissionRequirement> requirements = new LinkedHashSet<>();
        for (RouteRequirement route : resolved) {
            requirements.add(route.requirement());
        }
        if (requirements.size() > 1) {
            throw new BizException(AccessErrorCode.ADMISSION_REQUIREMENT_AMBIGUOUS.getCode(),
                "多条启用路由对同一路径要求不同: " + req.httpMethod() + " " + req.path() + " -> " + requirements);
        }
        AdmissionRequirement requirement = requirements.iterator().next();

        QueryItem item = QueryItem.admission("interface-admission",
            new TypeOperation(requirement.resourceTypeCode(), requirement.operationCode()), OutputSpec.kept());
        QueryResult result = queryEngine.execute(new QueryRequest(tenantId, new User(userId),
            CallerContext.fromCallerMap(req.context()), ReadOptions.defaults(), List.of(item)));
        AdmissionResult admission = (AdmissionResult) result.orderedResults().getFirst();
        return admission.outcome() == AdmissionResult.Admission.MAY_ENTER
            ? InterfaceAdmissionResp.mayEnter(requirement)
            : InterfaceAdmissionResp.deny(admission.reason().name(), requirement);
    }

    @Override
    @Transactional(readOnly = true)
    public InterfaceAdmissionSnapshotResp interfaceAdmissionSnapshot(Long tenantId, InterfaceAdmissionSnapshotReq req) {
        requireOwnedService(tenantId, req.serviceCode());
        ServiceConfig config = serviceConfigMapper.selectByTenantAndServiceCode(tenantId, req.serviceCode());
        if (config == null || config.getStatus() == null || config.getStatus() != 1) {
            // 未登记/停用＝该服务无参与授权的路由：空路由成功快照，网关本地无命中按
            // 无注册匹配拒绝（DENY→403），与在线端点 notRegistered 语义对称（2026-09-28
            // 外评拍板：20071 仅保留给引用损坏等配置故障——停用是例行管理操作，
            // 抛 20071 会使网关对该服务全量 503 重试风暴）
            return emptySnapshot(tenantId, req, config, config == null ? 0L : config.getConfigGeneration());
        }
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());

        for (int attempt = 1; attempt <= MAX_BUILD_ATTEMPTS; attempt++) {
            long generationBefore = readGeneration(tenantId, req.serviceCode());
            List<ResourceApiMapping> mappings = apiMappingMapper.selectEnabledByServiceCode(tenantId, req.serviceCode());
            List<RouteRequirement> routes = snapshotAssembler.resolveRouteRequirements(tenantId, mappings);

            List<InterfaceAdmissionSnapshotResp.OperationCandidateEntry> candidates = List.of();
            if (userId != null && !routes.isEmpty()) {
                candidates = buildCandidates(tenantId, userId, routes);
            }
            long generationAfter = readGeneration(tenantId, req.serviceCode());
            if (generationAfter != generationBefore) {
                // 构建期配置代次改变：废弃本份结果重建（拍板限定语义：自一致校验，非跨节点强一致）
                log.info("Admission snapshot build discarded on config generation change (tenant={}, service={}, {} -> {})",
                    tenantId, req.serviceCode(), generationBefore, generationAfter);
                continue;
            }
            // 终校验（代次保护的完备面）：代次比对只覆盖首次代次读之后的变更，入口校验与
            // 首次代次读之间停用会使代次前后一致但状态已变——强制落库复读确认
            // 仍在启用，把配置校验纳入每次构建的保护范围
            ServiceConfig authState = serviceConfigMapper.selectAuthState(tenantId, req.serviceCode());
            if (authState == null || authState.getStatus() == null || authState.getStatus() != 1) {
                log.info("Admission snapshot build hit disabled/unregistered service at final check (tenant={}, service={})",
                    tenantId, req.serviceCode());
                return emptySnapshot(tenantId, req, authState, generationAfter);
            }
            LocalDateTime generatedAt = LocalDateTime.now();
            return snapshotAssembler.assemble(tenantId, req, generationAfter, generatedAt,
                generatedAt.plus(SNAPSHOT_TTL), routes, candidates);
        }
        throw new SystemException(GlobalErrorCode.SYSTEM_ERROR.code(),
            "准入快照构建期间配置持续变更（重试 " + MAX_BUILD_ATTEMPTS + " 次后代次仍不稳定）: " + req.serviceCode());
    }

    /** 候选构建：全部去重要求一次 execute 多 admissionFacts item（N13 同批要求共享读算）。 */
    private List<InterfaceAdmissionSnapshotResp.OperationCandidateEntry> buildCandidates(
            Long tenantId, Long userId, List<RouteRequirement> routes) {
        Map<String, AdmissionRequirement> requirements = new LinkedHashMap<>();
        for (RouteRequirement route : routes) {
            requirements.putIfAbsent(requirementKey(route.requirement()), route.requirement());
        }
        List<QueryItem> items = new ArrayList<>(requirements.size());
        for (AdmissionRequirement requirement : requirements.values()) {
            items.add(QueryItem.admissionFacts("requirement-" + requirementKey(requirement),
                new TypeOperation(requirement.resourceTypeCode(), requirement.operationCode()),
                OutputSpec.kept()));
        }
        QueryResult result = queryEngine.execute(new QueryRequest(tenantId, new User(userId),
            CallerContext.of(null), ReadOptions.defaults(), items));
        Map<String, GrantSetResult> factsByRequirement = new LinkedHashMap<>();
        List<AdmissionRequirement> ordered = List.copyOf(requirements.values());
        for (int i = 0; i < ordered.size(); i++) {
            GrantSetResult grantSet = (GrantSetResult) result.orderedResults().get(i);
            // 键=要求键（装配器按 requirementKey 查找），item key 仅保输入关联
            factsByRequirement.put(requirementKey(ordered.get(i)), grantSet);
        }
        return snapshotAssembler.projectCandidates(tenantId, requirements.values(), factsByRequirement);
    }

    /** 空路由快照（未登记/停用服务）：无参与授权的路由，网关本地恒无命中→DENY。 */
    private InterfaceAdmissionSnapshotResp emptySnapshot(Long tenantId, InterfaceAdmissionSnapshotReq req,
                                                         ServiceConfig config, long generation) {
        LocalDateTime generatedAt = LocalDateTime.now();
        long resolved = config == null ? 0L : generation;
        return snapshotAssembler.assemble(tenantId, req, resolved, generatedAt,
            generatedAt.plus(SNAPSHOT_TTL), List.of(), List.of());
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        return PATH_MATCHER.match(pattern, path);
    }

    private static String requirementKey(AdmissionRequirement requirement) {
        return requirement.resourceTypeCode() + ":" + requirement.operationCode();
    }

    /**
     * 复读配置代次：独立语句 + flushCache=true 强制清本会话一级缓存后落库
     * （同语句二次调用走 SESSION 缓存会恒命中首次结果，自一致校验失效——外评修正）。
     */
    private long readGeneration(Long tenantId, String serviceCode) {
        Long generation = serviceConfigMapper.selectConfigGeneration(tenantId, serviceCode);
        return generation == null ? -1L : generation;
    }

    /**
     * 凭证身份服务归属约束（沿 sync-v2 先例）：per-service 凭证的 tenant/service 由认证链派生，
     * 请求 serviceCode 必须等于凭证所属服务；网关内部密钥形态（无 X-Service-Code 自报头，
     * 绑定 serviceCode=null）不受限——沿 check-interface 既有平台信任域口径。
     */
    private void requireOwnedService(Long tenantId, String serviceCode) {
        if (AccessRequestContext.getCallerType() == CallerType.SERVICE
            && AccessRequestContext.getServiceCode() != null
            && (!Objects.equals(tenantId, AccessRequestContext.getTenantId())
                || !Objects.equals(serviceCode, AccessRequestContext.getServiceCode()))) {
            throw new SecurityException("Service identity does not own requested service");
        }
    }
}
