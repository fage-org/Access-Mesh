package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.query.DomainTypeFilter;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.permission.enums.ConfigType;
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 域分类领域服务实现
 * <p>
 * 通过 domain_config CLASSIFY 配置确定业务域的分类范围。
 * 全局域(global=true)的范围 = 全部类型 - 其他域声明的类型（隐式计算，不存配置）。
 * </p>
 */
@Service
public class DomainClassifyServiceImpl implements DomainClassifyService {

    private static final Logger log = LoggerFactory.getLogger(DomainClassifyServiceImpl.class);

    private final BizDomainMapper bizDomainMapper;
    private final DomainConfigMapper domainConfigMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final TypeResolutionService typeResolutionService;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param bizDomainMapper     业务域数据访问层
     * @param domainConfigMapper  域配置数据访问层
     * @param typeResolutionService 类型解析服务
     * @param objectMapper        JSON解析器
     */
    public DomainClassifyServiceImpl(BizDomainMapper bizDomainMapper,
                                      DomainConfigMapper domainConfigMapper,
                                      TypeDefinitionMapper typeDefinitionMapper,
                                      TypeResolutionService typeResolutionService,
                                      ObjectMapper objectMapper) {
        this.bizDomainMapper = bizDomainMapper;
        this.domainConfigMapper = domainConfigMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.typeResolutionService = typeResolutionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据查询模式构建资源类型过滤条件
     */
    @Override
    public DomainTypeFilter buildTypeFilter(Long tenantId, DomainQueryMode mode, String domainCode) {
        if (mode == DomainQueryMode.ALL) {
            return DomainTypeFilter.noFilter();
        }
        if (domainCode == null || domainCode.isBlank()) {
            return DomainTypeFilter.noFilter();
        }

        Long domainId = domainCode != null
            ? typeResolutionService.resolveDomainId(tenantId, domainCode)
            : null;
        if (domainId == null) {
            return DomainTypeFilter.none();
        }

        Set<String> domainTypeCodes = isGlobalDomain(tenantId, domainId)
            ? computeGlobalTypeCodes(tenantId)
            : loadClassifyTypeCodes(tenantId, domainId);
        Set<Integer> domainTypeValues = resolveToTypeValues(tenantId, domainTypeCodes);

        if (mode == DomainQueryMode.DOMAIN_ONLY) {
            return DomainTypeFilter.only(domainTypeValues);
        }

        // GLOBAL_PLUS: 该域类型 + 未被任何域认领的类型
        Set<Integer> allClaimedValues = getAllClaimedTypeValues(tenantId);
        return DomainTypeFilter.globalPlus(domainTypeValues, allClaimedValues);
    }

    /**
     * 获取指定域声明的资源类型码集合
     */
    @Override
    public Set<String> getClassifiedTypeCodes(Long tenantId, String domainCode) {
        Long domainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
        if (domainId == null) return Set.of();

        boolean isGlobal = isGlobalDomain(tenantId, domainId);
        if (isGlobal) {
            return computeGlobalTypeCodes(tenantId);
        }
        return loadClassifyTypeCodes(tenantId, domainId);
    }

    /**
     * 判断域查询模式是否覆盖指定资源类型码
     */
    @Override
    public boolean matchesTypeCode(Long tenantId, DomainQueryMode mode, String domainCode, String resourceTypeCode) {
        if (resourceTypeCode == null || resourceTypeCode.isBlank()) {
            return false;
        }
        Integer resourceTypeValue = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceTypeValue == null) {
            return false;
        }
        return buildTypeFilter(tenantId, mode, domainCode).matches(resourceTypeValue);
    }

    /**
     * 通过资源类型码反查所属的业务域ID
     */
    @Override
    public Long findDomainIdByTypeCode(Long tenantId, String resourceTypeCode) {
        if (resourceTypeCode == null || resourceTypeCode.isBlank()) return null;

        // 查所有非全局域
        List<BizDomain> specificDomains = bizDomainMapper.selectNonGlobalByTenant(tenantId);

        for (BizDomain domain : specificDomains) {
            Set<String> typeCodes = loadClassifyTypeCodes(tenantId, domain.getId());
            if (typeCodes.contains(resourceTypeCode)) {
                return domain.getId();
            }
        }

        BizDomain globalDomain = bizDomainMapper.selectGlobalByTenant(tenantId);
        return globalDomain != null ? globalDomain.getId() : null;
    }

    /**
     * 确保租户的全局域存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureGlobalDomain(Long tenantId) {
        boolean exists = bizDomainMapper.countGlobalByTenant(tenantId) > 0;
        if (!exists) {
            BizDomain global = new BizDomain();
            global.setTenantId(tenantId);
            global.setCode("GLOBAL");
            global.setName("全局");
            global.setGlobal(true);
            global.setDeleteFlag(0L);
            bizDomainMapper.insert(global);
        }
    }

    // ===== 内部方法 =====

    /**
     * 判断指定域是否为全局域
     */
    private boolean isGlobalDomain(Long tenantId, Long domainId) {
        BizDomain domain = bizDomainMapper.selectValidById(domainId, tenantId);
        return domain != null && Boolean.TRUE.equals(domain.getGlobal());
    }

    /**
     * 计算全局域的类型码 = 全部类型码 - 其他域声明的类型码
     */
    private Set<String> computeGlobalTypeCodes(Long tenantId) {
        Set<String> allCodes = loadAllResourceTypeCodes(tenantId);
        Set<String> claimedCodes = getAllClaimedTypeCodes(tenantId);
        Set<String> globalCodes = new HashSet<>(allCodes);
        globalCodes.removeAll(claimedCodes);
        return globalCodes;
    }

    /**
     * 从 domain_config 加载指定域的 CLASSIFY 配置
     */
    private Set<String> loadClassifyTypeCodes(Long tenantId, Long bizDomainId) {
        DomainConfig config = domainConfigMapper.selectValidByTypeString(tenantId, bizDomainId, ConfigType.CLASSIFY.getValue());
        if (config == null || config.getExtra() == null) return Set.of();
        return parseResourceTypeCodes(config.getExtra());
    }

    /**
     * 获取所有非全局域声明的类型码
     */
    private Set<String> getAllClaimedTypeCodes(Long tenantId) {
        List<BizDomain> specificDomains = bizDomainMapper.selectNonGlobalByTenant(tenantId);
        Set<String> claimed = new HashSet<>();
        for (BizDomain domain : specificDomains) {
            claimed.addAll(loadClassifyTypeCodes(tenantId, domain.getId()));
        }
        return claimed;
    }

    /**
     * 获取所有非全局域声明的类型值（用于 GLOBAL_PLUS 过滤）
     */
    private Set<Integer> getAllClaimedTypeValues(Long tenantId) {
        Set<String> claimedCodes = getAllClaimedTypeCodes(tenantId);
        return resolveToTypeValues(tenantId, claimedCodes);
    }

    /**
     * 加载租户下所有 resource_type 类型码
     */
    private Set<String> loadAllResourceTypeCodes(Long tenantId) {
        return typeDefinitionMapper.selectByTenantAndTypeKey(tenantId, "resource_type")
            .stream().map(TypeDefinition::getTypeCode).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 将类型码集合解析为类型值集合
     */
    private Set<Integer> resolveToTypeValues(Long tenantId, Set<String> typeCodes) {
        if (typeCodes == null || typeCodes.isEmpty()) return Set.of();
        Map<String, Integer> map = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", typeCodes);
        return new HashSet<>(map.values());
    }

    /**
     * 解析 CLASSIFY 配置的 extra JSON
     * <p>
     * 格式: {"resourceTypeCodes":["ORG","USER"]}
     * </p>
     */
    private Set<String> parseResourceTypeCodes(String extra) {
        Set<String> result = new HashSet<>();
        try {
            JsonNode node = objectMapper.readTree(extra);
            JsonNode codes = node.get("resourceTypeCodes");
            if (codes != null && codes.isArray()) {
                for (JsonNode c : codes) {
                    String code = c.asText();
                    if (code != null && !code.isBlank()) {
                        result.add(code);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析CLASSIFY配置失败: {}", e.getMessage());
        }
        return result;
    }
}
