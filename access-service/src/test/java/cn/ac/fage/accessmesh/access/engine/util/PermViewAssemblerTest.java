package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.ResourceDescription;
import cn.ac.fage.accessmesh.access.engine.query.Stage;
import cn.ac.fage.accessmesh.access.engine.query.StageFacts;
import cn.ac.fage.accessmesh.access.domain.entity.BizDomain;
import cn.ac.fage.accessmesh.access.domain.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.ac.fage.accessmesh.access.engine.query.PresentationEntry.Derivation.CHILD;
import static cn.ac.fage.accessmesh.access.engine.query.PresentationEntry.Derivation.OPERATION_COVERAGE;
import static cn.ac.fage.accessmesh.access.engine.query.PresentationEntry.Derivation.ORIGINAL;
import static cn.ac.fage.accessmesh.access.engine.query.PresentationEntry.Derivation.PARENT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限视图装配器测试（登录权限串唯一存续消费面的域归属映射构建；
 * T-PERM-091 起消费新 execute 的 GrantSetResult 投影）
 */
@ExtendWith(MockitoExtension.class)
class PermViewAssemblerTest {

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private BizDomainMapper bizDomainMapper;

    private PermViewAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PermViewAssembler(typeResolutionService, domainClassifyService, bizDomainMapper);
    }

    /**
     * T-PERM-060 回归锁：buildDomainCodeMap 对 distinct typeCode 必须一次批量反查
     * （findDomainIdsByTypeCodes 收全集），禁止退回逐类型点查——
     * 登录权限串热路径上逐类型点查按 distinct 类型数 ×3 放大（类型数无硬上限，
     * schema 预置 25 类且可自定义新增；旧实现下本用例必失败）。
     */
    @Test
    void shouldResolveDomainIdsForAllDistinctTypeCodesInSingleBatchCall() {
        // MENU 两行（res-200/201 同类型去重）+ DEPT 一行（res-300）
        GrantSetResult result = grantResult(
            List.of(entry(401L, 200L, 1), entry(402L, 201L, 1), entry(403L, 300L, 2)),
            Map.of(
                200L, resource(200L, 1, "menu:list"),
                201L, resource(201L, 1, "menu:detail"),
                300L, resource(300L, 2, "dept-a")));

        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1, 2)))
            .thenReturn(Map.of(1, "MENU", 2, "DEPT"));
        when(domainClassifyService.findDomainIdsByTypeCodes(1L, Set.of("MENU", "DEPT")))
            .thenReturn(Map.of("MENU", 10L, "DEPT", 20L));
        when(bizDomainMapper.selectValidByIds(1L, Set.of(10L, 20L))).thenReturn(List.of(
            domain(10L, "OPS"), domain(20L, "HR")));

        cn.ac.fage.accessmesh.access.engine.dto.PermViewResult view = assembler.assemble(1L, result, null);

        assertEquals(Map.of(200L, "OPS", 201L, "OPS", 300L, "HR"), view.getDomainCodeMap());
        verify(domainClassifyService, times(1)).findDomainIdsByTypeCodes(1L, Set.of("MENU", "DEPT"));
    }

    /**
     * 批量映射缺 key（类型未被具体域认领）= 该资源不进 domainCodeMap，
     * 与逐类型点查返回 null 的旧语义一致。
     */
    @Test
    void shouldSkipResourcesWhoseTypeIsMissingFromBatchDomainMap() {
        GrantSetResult result = grantResult(
            List.of(entry(401L, 200L, 1), entry(403L, 300L, 2)),
            Map.of(
                200L, resource(200L, 1, "menu:list"),
                300L, resource(300L, 2, "dept-a")));

        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1, 2)))
            .thenReturn(Map.of(1, "MENU", 2, "DEPT"));
        // DEPT 未被认领：批量映射缺 key
        when(domainClassifyService.findDomainIdsByTypeCodes(1L, Set.of("MENU", "DEPT")))
            .thenReturn(Map.of("MENU", 10L));
        when(bizDomainMapper.selectValidByIds(1L, Set.of(10L))).thenReturn(List.of(domain(10L, "OPS")));

        cn.ac.fage.accessmesh.access.engine.dto.PermViewResult view = assembler.assemble(1L, result, null);

        assertEquals(Map.of(200L, "OPS"), view.getDomainCodeMap());
    }

    /**
     * T-PERM-091 复评回归锁（设计 §6.4 方向优先）：资源 100 授 UPDATE（覆盖 VIEW）展开到
     * 父 90/子 110 后，父/子投影行不得被事实过滤丢弃——事实↔投影按源授权行
     * （sourcePermissionId↔permissionId）关联，displayedEntityId 是展示资源不作关联键。
     * 旧实现把 displayedEntityId 拼进来源键，展开行与事实 resourceEntityId 错配被整批
     * 删除，本用例必失败。
     */
    @Test
    void shouldKeepParentAndChildExpansionEntriesJoinedBySourcePermission() {
        GrantFact source = new GrantFact(401L, 20L, 1, 100L, 2L,
            false, false, null, false, null, "MANUAL");
        GrantSetResult result = grantResult(List.of(source), Map.of(), List.of(
            eff(401L, 100L, ORIGINAL, "UPDATE", "UPDATE", 2L),
            eff(401L, 100L, OPERATION_COVERAGE, "UPDATE", "VIEW", 1L),
            eff(401L, 90L, PARENT, "UPDATE", "UPDATE", 2L),
            eff(401L, 90L, PARENT, "UPDATE", "VIEW", 1L),
            eff(401L, 110L, CHILD, "UPDATE", "UPDATE", 2L),
            eff(401L, 110L, CHILD, "UPDATE", "VIEW", 1L)));
        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
            .thenReturn(Map.of(1, "MENU"));

        cn.ac.fage.accessmesh.access.engine.dto.PermViewResult view = assembler.assemble(1L, result, null);

        // 六行投影全部保留：源资源 2 行（ORIGINAL＋OPERATION_COVERAGE）＋父 2 行＋子 2 行
        assertEquals(6, view.getEffectiveOperationEntries().size());
        // 方向优先：父/子行保持 PARENT/CHILD，不因操作覆盖被改标或筛掉
        Map<Long, List<String>> derivationsByEntity = view.getEffectiveOperationEntries().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                cn.ac.fage.accessmesh.access.engine.query.ResultDetails.EffectiveOperationEntry::displayedEntityId,
                java.util.stream.Collectors.mapping(
                    e -> e.derivation() + ":" + e.operationCode(),
                    java.util.stream.Collectors.toList())));
        assertEquals(Map.of(
            100L, List.of("ORIGINAL:UPDATE", "OPERATION_COVERAGE:VIEW"),
            90L, List.of("PARENT:UPDATE", "PARENT:VIEW"),
            110L, List.of("CHILD:UPDATE", "CHILD:VIEW")), derivationsByEntity);
    }

    /**
     * 方向优先下的操作码过滤：筛 VIEW 时三个展示资源上的 VIEW 行都保留（OPERATION_COVERAGE
     * ＋PARENT＋CHILD），不以 derivation==OPERATION_COVERAGE 作唯一筛选（设计 §6.4）。
     */
    @Test
    void shouldMatchOperationCodeAcrossAllDerivationsWhenFilteringByOperation() {
        GrantFact source = new GrantFact(401L, 20L, 1, 100L, 2L,
            false, false, null, false, null, "MANUAL");
        GrantSetResult result = grantResult(List.of(source), Map.of(), List.of(
            eff(401L, 100L, ORIGINAL, "UPDATE", "UPDATE", 2L),
            eff(401L, 100L, OPERATION_COVERAGE, "UPDATE", "VIEW", 1L),
            eff(401L, 90L, PARENT, "UPDATE", "UPDATE", 2L),
            eff(401L, 90L, PARENT, "UPDATE", "VIEW", 1L),
            eff(401L, 110L, CHILD, "UPDATE", "UPDATE", 2L),
            eff(401L, 110L, CHILD, "UPDATE", "VIEW", 1L)));
        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
            .thenReturn(Map.of(1, "MENU"));
        cn.ac.fage.accessmesh.access.engine.dto.PermViewFilter filter =
            new cn.ac.fage.accessmesh.access.engine.dto.PermViewFilter();
        filter.setOperationCodes(Set.of("VIEW"));

        cn.ac.fage.accessmesh.access.engine.dto.PermViewResult view = assembler.assemble(1L, result, filter);

        // 覆盖出的 VIEW 在全部三个派生来源上被识别（旧实现仅剩源资源 OPERATION_COVERAGE 一行）
        assertEquals(3, view.getEffectiveOperationEntries().size());
        assertEquals(List.of("OPERATION_COVERAGE", "PARENT", "CHILD"),
            view.getEffectiveOperationEntries().stream()
                .map(e -> e.derivation().name())
                .toList());
        // 事实经 VIEW 投影命中，不被操作码过滤删掉
        assertEquals(1, view.getEntries().size());
    }

    // ========== 夹具（新 execute 结果构造） ==========

    private GrantSetResult grantResult(List<GrantFact> facts, Map<Long, ResourceDescription> resources) {
        return grantResult(facts, resources, List.of());
    }

    private GrantSetResult grantResult(List<GrantFact> facts, Map<Long, ResourceDescription> resources,
                                       List<cn.ac.fage.accessmesh.access.engine.query.ResultDetails.EffectiveOperationEntry> effectiveOperations) {
        StageFacts stage = new StageFacts(Stage.GRANT_LIST, facts, facts, StageFacts.Status.PRESENT);
        ResultDetails details = new ResultDetails(Set.of(), List.of(), List.of(), List.of(stage),
            new ResultDetails.Descriptions(resources, Map.of(), Map.of(), Map.of()),
            effectiveOperations, List.of(), new ResultDetails.ParentCheckSummary(List.of()),
            new ResultDetails.ExecutionTrace(List.of(), List.of(), List.of(), List.of()));
        return new GrantSetResult("view", GrantSetResult.CollectionStatus.PRESENT,
            new EvaluationCoverage(EvaluationCoverage.SubjectResolution.USER_EFFECTIVE_WITH_MUTEX,
                EvaluationCoverage.ConditionCoverage.EVALUATED, EvaluationCoverage.MutexCoverage.EVALUATED,
                EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED, Set.of(Stage.GRANT_LIST),
                Map.of(), true, EvaluationCoverage.AuthorizationStage.FACT_COLLECTION),
            details);
    }

    private static GrantFact entry(Long permissionId, Long resourceEntityId, Integer resourceType) {
        return new GrantFact(permissionId, 20L, resourceType, resourceEntityId, 1L,
            false, false, null, false, null, "MANUAL");
    }

    /** 有效操作投影行：授予位 2=UPDATE（覆盖位 1=VIEW），effectiveBits=3。 */
    private static cn.ac.fage.accessmesh.access.engine.query.ResultDetails.EffectiveOperationEntry eff(
            Long sourcePermissionId, Long displayedEntityId,
            cn.ac.fage.accessmesh.access.engine.query.PresentationEntry.Derivation derivation,
            String grantedOperationCode, String operationCode, Long operationBinaryBit) {
        return new cn.ac.fage.accessmesh.access.engine.query.ResultDetails.EffectiveOperationEntry(
            sourcePermissionId, 20L, displayedEntityId, derivation, 1, 2L,
            grantedOperationCode, 3L, operationCode, operationBinaryBit);
    }

    private static ResourceDescription resource(Long id, Integer resourceType, String code) {
        return new ResourceDescription(id, resourceType, code, "default", null, null, null, null, null, null, null);
    }

    private static BizDomain domain(Long id, String code) {
        BizDomain d = new BizDomain();
        d.setId(id);
        d.setCode(code);
        return d;
    }
}
