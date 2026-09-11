package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限视图装配器测试（登录权限串唯一存续消费面的域归属映射构建）
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
        PermResult result = PermResult.builder(true, null)
            .instanceEntries(List.of(
                entry(401L, 200L, 1),
                entry(402L, 201L, 1),
                entry(403L, 300L, 2)))
            .resourceMap(Map.of(
                200L, resource(200L, 1, "menu:list"),
                201L, resource(201L, 1, "menu:detail"),
                300L, resource(300L, 2, "dept-a")))
            .build();

        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1, 2)))
            .thenReturn(Map.of(1, "MENU", 2, "DEPT"));
        when(domainClassifyService.findDomainIdsByTypeCodes(1L, Set.of("MENU", "DEPT")))
            .thenReturn(Map.of("MENU", 10L, "DEPT", 20L));
        when(bizDomainMapper.selectValidByIds(1L, Set.of(10L, 20L))).thenReturn(List.of(
            domain(10L, "OPS"), domain(20L, "HR")));

        PermViewResult view = assembler.assemble(1L, result, null);

        assertEquals(Map.of(200L, "OPS", 201L, "OPS", 300L, "HR"), view.getDomainCodeMap());
        verify(domainClassifyService, times(1)).findDomainIdsByTypeCodes(1L, Set.of("MENU", "DEPT"));
    }

    /**
     * 批量映射缺 key（类型未被具体域认领）= 该资源不进 domainCodeMap，
     * 与逐类型点查返回 null 的旧语义一致。
     */
    @Test
    void shouldSkipResourcesWhoseTypeIsMissingFromBatchDomainMap() {
        PermResult result = PermResult.builder(true, null)
            .instanceEntries(List.of(entry(401L, 200L, 1), entry(403L, 300L, 2)))
            .resourceMap(Map.of(
                200L, resource(200L, 1, "menu:list"),
                300L, resource(300L, 2, "dept-a")))
            .build();

        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1, 2)))
            .thenReturn(Map.of(1, "MENU", 2, "DEPT"));
        // DEPT 未被认领：批量映射缺 key
        when(domainClassifyService.findDomainIdsByTypeCodes(1L, Set.of("MENU", "DEPT")))
            .thenReturn(Map.of("MENU", 10L));
        when(bizDomainMapper.selectValidByIds(1L, Set.of(10L))).thenReturn(List.of(domain(10L, "OPS")));

        PermViewResult view = assembler.assemble(1L, result, null);

        assertEquals(Map.of(200L, "OPS"), view.getDomainCodeMap());
    }

    private static RolePermEntry entry(Long permissionId, Long resourceEntityId, Integer resourceType) {
        return new RolePermEntry(permissionId, 20L, resourceEntityId, null, resourceType,
            1L, "VIEW", 1L, "MANUAL", false, null, false, null, false);
    }

    private static ResourceEntity resource(Long id, Integer resourceType, String code) {
        ResourceEntity res = new ResourceEntity();
        res.setId(id);
        res.setResourceType(resourceType);
        res.setCode(code);
        res.setCodeType("default");
        return res;
    }

    private static BizDomain domain(Long id, String code) {
        BizDomain d = new BizDomain();
        d.setId(id);
        d.setCode(code);
        return d;
    }
}
