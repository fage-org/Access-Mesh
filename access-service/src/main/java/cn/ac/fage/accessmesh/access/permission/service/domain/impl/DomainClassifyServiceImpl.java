package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.ConfigType;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
     * 获取指定域声明的资源类型码集合
     * <p>
     * 如果是全局域，返回所有未被其他域认领的类型（隐式计算）。
     * 如果是非全局域，返回CLASSIFY配置中声明的类型。
     * </p>
     *
     * @param tenantId  租户ID
     * @param domainCode 业务域编码
     * @return 资源类型编码集合
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
     * <p>
     * 根据查询模式判断资源类型是否在域范围内：
     * - ALL: 覆盖所有类型
     * - GLOBAL_PLUS: 覆盖全局域 + 指定域声明的类型
     * - DOMAIN_ONLY: 仅覆盖指定域声明的类型
     * </p>
     *
     * @param tenantId         租户ID
     * @param mode             域查询模式
     * @param domainCode       业务域编码
     * @param resourceTypeCode 资源类型编码
     * @return 是否在域范围内
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

        if (mode == DomainQueryMode.ALL) {
            return true;
        }
        if (domainCode == null || domainCode.isBlank()) {
            return true;
        }

        Long domainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
        if (domainId == null) {
            return false;
        }

        Set<String> classifiedCodes = isGlobalDomain(tenantId, domainId)
            ? computeGlobalTypeCodes(tenantId)
            : loadClassifyTypeCodes(tenantId, domainId);

        if (mode == DomainQueryMode.DOMAIN_ONLY) {
            return classifiedCodes.contains(resourceTypeCode);
        }

        // GLOBAL_PLUS: 该域类型 + 未被任何域认领的类型（全局域的隐式范围）
        if (classifiedCodes.contains(resourceTypeCode)) {
            return true;
        }

        // 未被任何具体域认领的类型属于 GLOBAL_PLUS 的隐式范围。
        Set<String> allClaimedCodes = getAllClaimedTypeCodes(tenantId);
        return !allClaimedCodes.contains(resourceTypeCode);
    }

    /**
     * 通过资源类型码反查所属的业务域ID
     */
    @Override
    public Long findDomainIdByTypeCode(Long tenantId, String resourceTypeCode) {
        if (resourceTypeCode == null || resourceTypeCode.isBlank()) return null;
        return findDomainIdsByTypeCodes(tenantId, Set.of(resourceTypeCode)).get(resourceTypeCode);
    }

    @Override
    public Map<String, Long> findDomainIdsByTypeCodes(Long tenantId, Set<String> resourceTypeCodes) {
        if (resourceTypeCodes == null || resourceTypeCodes.isEmpty()) {
            return Map.of();
        }
        List<BizDomain> specificDomains = bizDomainMapper.selectNonGlobalByTenant(tenantId);
        Map<Long, Set<String>> classifiedCodesByDomain = domainConfigMapper.selectByTenantId(tenantId).stream()
            .filter(config -> ConfigType.CLASSIFY.getValue().equals(config.getConfigType()))
            .collect(Collectors.toMap(
                DomainConfig::getBizDomainId,
                config -> parseResourceTypeCodes(config.getExtra()).stream()
                    .map(code -> code.toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet()),
                (left, right) -> {
                    Set<String> merged = new HashSet<>(left);
                    merged.addAll(right);
                    return merged;
                }
            ));
        BizDomain globalDomain = bizDomainMapper.selectGlobalByTenant(tenantId);
        Map<String, Long> result = new LinkedHashMap<>();
        for (String typeCode : resourceTypeCodes) {
            if (typeCode == null || typeCode.isBlank()) {
                continue;
            }
            String normalized = typeCode.toUpperCase(Locale.ROOT);
            Long domainId = specificDomains.stream()
                .filter(domain -> classifiedCodesByDomain
                    .getOrDefault(domain.getId(), Set.of()).contains(normalized))
                .map(BizDomain::getId)
                .findFirst()
                .orElse(globalDomain == null ? null : globalDomain.getId());
            if (domainId != null) {
                result.put(typeCode, domainId);
            }
        }
        return result;
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
     * 加载租户下所有 resource_type 类型码
     */
    private Set<String> loadAllResourceTypeCodes(Long tenantId) {
        return typeDefinitionMapper.selectByTenantAndTypeKey(tenantId, "resource_type")
            .stream().map(TypeDefinition::getTypeCode).filter(Objects::nonNull).collect(Collectors.toSet());
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
