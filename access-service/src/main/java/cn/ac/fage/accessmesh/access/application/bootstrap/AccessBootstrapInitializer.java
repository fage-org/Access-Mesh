package cn.ac.fage.accessmesh.access.application.bootstrap;

import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.BootstrapSeedWriter;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.dev33.satoken.secure.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空库 bootstrap 事务化 initializer（T-ACCESS-020，architecture §14）。
 * <p>
 * 幂等三状态（固定图子集匹配口径，2026-08-24 用户决策）：
 * <ol>
 *   <li><b>固定图完全不存在</b> → 本事务内创建完整固定图（首管理员主体链 + 管理用功能角色 +
 *       SERVICE/API 资源与映射 + 全量固定图授权）；</li>
 *   <li><b>固定图完整匹配</b> → 整体 no-op，绝不重置密码（name/密码/邮箱等可变属性不参与匹配）；
 *       固定图之外的数据（E2E 等管理链路创建的用户/角色/授权）不构成冲突——否则 T-ACCESS-021
 *       第⑦步"重启后权限仍生效"无法通过；</li>
 *   <li><b>部分存在 / 属性不匹配 / 固定业务键被其他数据占用</b> → 抛 IllegalStateException
 *       报告全部冲突项，不自动修复、不补权、不扩权（启动失败 fail-fast）。</li>
 * </ol>
 * 复用既有领域服务：主体+投影（{@link LocalProjectionDomainService#createLocalUserSubject}）、
 * sys_user（{@link UserDomainService}）、角色（{@link SubjectDomainService#createRole} + ROLE 投影）、
 * 授权/资源/绑定（{@link BootstrapSeedWriter}，其授权写入复用 PermissionGrantPlanDomainService.apply）。
 * 不走带操作者权限校验的管理 AppService。类型值与操作位一律经 type_definition / operation_permission
 * 解析，禁止硬编码内部数值。
 * </p>
 */
@Component
public class AccessBootstrapInitializer {

    private static final Logger log = LoggerFactory.getLogger(AccessBootstrapInitializer.class);

    /** 固定图授权涉及的资源类型码全集（类型解析与操作位查询范围；须与
     * BootstrapGraphDefinition.businessGrants 涉及的类型同步增减，缺项会导致操作位 fail-fast） */
    private static final Set<String> GRANT_RESOURCE_TYPES = Set.of(
        ResourceTypeCode.API, ResourceTypeCode.SERVICE, ResourceTypeCode.USER, ResourceTypeCode.ROLE,
        ResourceTypeCode.TYPE_DEFINITION, ResourceTypeCode.RESOURCE, ResourceTypeCode.OPERATION,
        ResourceTypeCode.OPERATION_LOG, ResourceTypeCode.PERMISSION_CHANGE_LOG, ResourceTypeCode.DOMAIN,
        ResourceTypeCode.CONFLICT_RULE, ResourceTypeCode.CONDITION, ResourceTypeCode.DEPENDENCY);

    /** sys_user.user_type：本地用户管理展示值（与 createUser 链一致；权限域类型由投影链解析） */
    private static final int SYS_USER_TYPE_PERSON = 1;

    private static final String TYPE_KEY_RESOURCE = "resource_type";
    private static final String TYPE_KEY_ROLE = "role_type";
    private static final String TYPE_KEY_USER = "user_type";

    private final UserDomainService userDomainService;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final BootstrapSeedWriter seedWriter;

    public AccessBootstrapInitializer(UserDomainService userDomainService,
                                      LocalProjectionDomainService localProjectionDomainService,
                                      SubjectDomainService subjectDomainService,
                                      TypeResolutionService typeResolutionService,
                                      BootstrapSeedWriter seedWriter) {
        this.userDomainService = userDomainService;
        this.localProjectionDomainService = localProjectionDomainService;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.seedWriter = seedWriter;
    }

    /**
     * 幂等入口：单事务内完成固定图检测与（必要时）创建。
     *
     * @param adminPassword 明文密码（仅 BCrypt 哈希落库，禁止写日志）
     */
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    public void initialize(String adminPassword) {
        Long tenantId = BootstrapGraphDefinition.TENANT_ID;
        Map<String, Integer> resourceTypes = resolveGrantResourceTypes(tenantId);
        Integer basicRoleType = requireType(tenantId, TYPE_KEY_ROLE, BootstrapGraphDefinition.ADMIN_ROLE_TYPE_CODE);
        Integer localUserType = requireType(tenantId, TYPE_KEY_USER, LocalProjectionOwner.SUBJECT_LOCAL_USER);
        Map<String, Long> operationBits = loadOperationBits(tenantId, resourceTypes);

        List<String> conflicts = new ArrayList<>();
        boolean complete = inspect(tenantId, resourceTypes, basicRoleType, localUserType, operationBits, conflicts);
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("bootstrap 固定图冲突（不自动修复，请人工核对）: "
                + String.join("; ", conflicts));
        }
        if (complete) {
            log.info("Bootstrap graph already present and matching — no-op (password untouched)");
            return;
        }
        createGraph(tenantId, resourceTypes, basicRoleType, operationBits, adminPassword);
    }

    // ===== 三状态检测（固定图子集匹配） =====

    /**
     * 逐项检测固定图，冲突写入 {@code conflicts}。
     *
     * @return true 表示固定图完整匹配（状态② no-op）；false 表示完全不存在（状态① 创建）；
     *         有冲突时返回值无意义（调用方以 conflicts 非空先行 fail-fast）
     */
    private boolean inspect(Long tenantId, Map<String, Integer> resourceTypes, Integer basicRoleType,
                            Integer localUserType, Map<String, Long> operationBits, List<String> conflicts) {
        boolean adminPresent = false;
        Long adminSubjectId = null;

        // —— 首管理员主体链 ——
        SysUser admin = userDomainService.findByUsername(tenantId, BootstrapGraphDefinition.ADMIN_USERNAME);
        if (admin != null) {
            adminPresent = true;
            adminSubjectId = admin.getId();
            if (admin.getStatus() == null || admin.getStatus() != 1) {
                conflicts.add("sys_user 'admin' 存在但已停用 (status=" + admin.getStatus() + ")");
            }
            // 禁用主体引擎侧有效角色置空（T-ACCESS-019 DDL 语义）——no-op 判定必须将其视为冲突；
            // 身份键 user_type=LOCAL_USER / external_id=主体 ID（§14.2 固定图身份）漂移同样构成冲突
            AbstractUser subject = subjectDomainService.selectValidUserById(tenantId, adminSubjectId);
            if (subject == null) {
                conflicts.add("sys_user 'admin' 存在但 abstract_user(LOCAL_USER) 主体缺失（主体链断裂）");
            } else if (!Objects.equals(subject.getUserType(), localUserType)) {
                conflicts.add("admin 主体身份漂移: abstract_user.user_type=" + subject.getUserType()
                    + "（固定图要求 LOCAL_USER=" + localUserType + "）");
            } else if (!String.valueOf(adminSubjectId).equals(subject.getExternalId())) {
                conflicts.add("admin 主体身份漂移: abstract_user.external_id=" + subject.getExternalId()
                    + "（固定图要求主体 ID " + adminSubjectId + "）");
            } else if (!Boolean.TRUE.equals(subject.getEnabled())) {
                conflicts.add("admin 主体 (abstract_user) 已禁用——禁用主体有效角色置空，权限不可用");
            }
            List<ResourceEntity> userResources = seedWriter.findResources(
                tenantId, resourceTypes.get(ResourceTypeCode.USER), Set.of(String.valueOf(adminSubjectId)));
            if (userResources.isEmpty()) {
                conflicts.add("sys_user 'admin' 存在但 resource_entity(USER) 投影缺失");
            } else if (userResources.get(0).getStatus() == null || userResources.get(0).getStatus() != 1) {
                conflicts.add("admin 的 resource_entity(USER) 投影已停用 (status="
                    + userResources.get(0).getStatus() + ")");
            }
        }

        // —— 管理用功能角色 ——
        boolean rolePresent = false;
        Long roleId = null;
        AbstractRole role = seedWriter.findRoleByExternalId(tenantId, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);
        if (role != null) {
            rolePresent = true;
            roleId = role.getId();
            if (!Objects.equals(role.getRoleType(), basicRoleType)) {
                conflicts.add("abstract_role external_id='" + BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID
                    + "' 被其他角色类型占用 (role_type=" + role.getRoleType()
                    + ", 期望 BASIC_ROLE=" + basicRoleType + ")");
            }
            if (role.getStatus() == null || role.getStatus() != 1) {
                conflicts.add("bootstrap 管理角色已停用 (status=" + role.getStatus() + ")，其授权不会生效");
            }
            // ROLE 资源投影（upsertRoleResource 同事务产物）：缺失/停用会破坏角色树与授权页链路
            List<ResourceEntity> roleResources = seedWriter.findResources(
                tenantId, resourceTypes.get(ResourceTypeCode.ROLE), Set.of(String.valueOf(roleId)));
            if (roleResources.isEmpty()) {
                conflicts.add("bootstrap 管理角色的 resource_entity(ROLE) 投影缺失");
            } else if (roleResources.get(0).getStatus() == null || roleResources.get(0).getStatus() != 1) {
                conflicts.add("bootstrap 管理角色的 resource_entity(ROLE) 投影已停用 (status="
                    + roleResources.get(0).getStatus() + ")");
            }
        }

        // —— 绑定（双方齐全才可比；relation_id 必须为 null——组角色关联等 relation 绑定不算固定图直绑） ——
        if (adminPresent && rolePresent) {
            boolean directBindingExists = seedWriter.findValidBindings(tenantId, adminSubjectId, roleId).stream()
                .anyMatch(binding -> binding.getRelationId() == null);
            if (!directBindingExists) {
                conflicts.add("首管理员与 bootstrap 管理角色的 user_role 直绑缺失（关联缺失或仅存 relation 绑定）");
            }
        }

        // —— SERVICE 资源 ——
        List<ResourceEntity> serviceResources = seedWriter.findResources(
            tenantId, resourceTypes.get(ResourceTypeCode.SERVICE),
            Set.of(BootstrapGraphDefinition.SERVICE_RESOURCE_CODE));
        boolean serviceResourcePresent = !serviceResources.isEmpty();
        Long serviceResourceId = serviceResourcePresent ? serviceResources.get(0).getId() : null;
        if (serviceResourcePresent && (serviceResources.get(0).getStatus() == null
                || serviceResources.get(0).getStatus() != 1)) {
            conflicts.add("SERVICE 资源 '" + BootstrapGraphDefinition.SERVICE_RESOURCE_CODE
                + "' 已停用 (status=" + serviceResources.get(0).getStatus() + ")——固定图种子对象失效");
        }

        // —— API 资源 ——
        Map<String, Long> apiResourceIds = new HashMap<>();
        Set<String> expectedApiCodes = BootstrapGraphDefinition.apiRoutes().stream()
            .map(route -> BootstrapGraphDefinition.apiResourceCode(route.method(), route.path()))
            .collect(Collectors.toSet());
        Set<String> disabledApiCodes = new HashSet<>();
        for (ResourceEntity resource : seedWriter.findResources(
                tenantId, resourceTypes.get(ResourceTypeCode.API), expectedApiCodes)) {
            apiResourceIds.put(resource.getCode(), resource.getId());
            if (resource.getStatus() == null || resource.getStatus() != 1) {
                disabledApiCodes.add(resource.getCode());
            }
        }
        if (!disabledApiCodes.isEmpty()) {
            conflicts.add("API 资源已停用（Gateway 实例级鉴权将失效）: " + disabledApiCodes);
        }
        if (!apiResourceIds.isEmpty() && apiResourceIds.size() < expectedApiCodes.size()) {
            Set<String> missing = new HashSet<>(expectedApiCodes);
            missing.removeAll(apiResourceIds.keySet());
            conflicts.add("API 资源部分存在，缺失: " + missing);
        }

        // —— 映射（管理 API 清单中有映射的接口；目标接口无映射） ——
        if (!apiResourceIds.isEmpty()) {
            List<BootstrapGraphDefinition.ApiRoute> mappedRoutes = BootstrapGraphDefinition.apiRoutes().stream()
                .filter(BootstrapGraphDefinition.ApiRoute::withMapping)
                .toList();
            Set<Long> mappedResourceIds = mappedRoutes.stream()
                .map(route -> BootstrapGraphDefinition.apiResourceCode(route.method(), route.path()))
                .map(apiResourceIds::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Set<String> mappedKeys = seedWriter.findMappings(tenantId, mappedResourceIds).stream()
                .filter(mapping -> Boolean.TRUE.equals(mapping.getEnabled()))
                .map(mapping -> mappingKey(mapping.getServiceCode(),
                    mapping.getResourceEntityId(), mapping.getHttpMethod(), mapping.getPathPattern()))
                .collect(Collectors.toSet());
            List<String> missingMappings = mappedRoutes.stream()
                .filter(route -> apiResourceIds.containsKey(
                    BootstrapGraphDefinition.apiResourceCode(route.method(), route.path())))
                .filter(route -> !mappedKeys.contains(mappingKey(BootstrapGraphDefinition.API_SERVICE_CODE,
                    apiResourceIds.get(BootstrapGraphDefinition.apiResourceCode(route.method(), route.path())),
                    route.method(), route.path())))
                .map(route -> route.method() + " " + route.path())
                .toList();
            if (!missingMappings.isEmpty()) {
                conflicts.add("API 映射缺失或未启用（serviceCode=" + BootstrapGraphDefinition.API_SERVICE_CODE
                    + "）: " + missingMappings);
            }
        }

        // —— 授权（固定图全量，子集匹配：固定图条目齐全即可，角色上的多余授权不冲突） ——
        if (rolePresent && roleId != null) {
            Set<GrantKey> existing = seedWriter.findValidGrants(tenantId, roleId).stream()
                .map(GrantKey::of)
                .collect(Collectors.toSet());
            List<String> missingGrants = buildExpectedGrants(
                    tenantId, roleId, resourceTypes, operationBits, apiResourceIds, serviceResourceId).stream()
                .filter(grant -> !existing.contains(GrantKey.of(grant)))
                .map(grant -> grant.getResourceType() + "#bits=" + grant.getGrantedBits()
                    + (grant.getScopeAll() ? "@ALL" : "@instance") + grant.getResourceEntityId())
                .toList();
            if (!missingGrants.isEmpty()) {
                conflicts.add("管理角色授权缺失或属性不匹配（canGrant 参与匹配）: " + missingGrants);
            }
        }

        boolean apiResourcesComplete = apiResourceIds.size() == expectedApiCodes.size();
        boolean complete = adminPresent && rolePresent && serviceResourcePresent && apiResourcesComplete;
        if (!complete) {
            // 状态③而非①：任一固定图对象已存在而其余缺失时按"部分存在"报告，
            // 避免走创建链撞唯一约束（报不可诊断的数据库异常）
            boolean anyPresent = adminPresent || rolePresent || serviceResourcePresent || !apiResourceIds.isEmpty();
            if (anyPresent) {
                conflicts.add("固定图部分存在: admin=" + adminPresent + ", 管理角色=" + rolePresent
                    + ", SERVICE资源=" + serviceResourcePresent
                    + ", API资源=" + apiResourceIds.size() + "/" + expectedApiCodes.size());
            }
        }
        return complete;
    }

    /** 映射存在键：serviceCode|resourceId|METHOD|path（与 resource_api_mapping 唯一索引同构——Gateway 快照按 serviceCode 过滤）。 */
    private static String mappingKey(String serviceCode, Long resourceEntityId,
                                     String httpMethod, String pathPattern) {
        return serviceCode + "|" + resourceEntityId + "|" + httpMethod.toUpperCase() + "|" + pathPattern;
    }

    // ===== 状态①：单事务创建固定图 =====

    private void createGraph(Long tenantId, Map<String, Integer> resourceTypes, Integer basicRoleType,
                             Map<String, Long> operationBits, String adminPassword) {
        // 主体 + USER 投影（同 ID 双表的前半：abstract_user + resource_entity(USER)）
        Long subjectId = localProjectionDomainService.createLocalUserSubject(
            tenantId, BootstrapGraphDefinition.ADMIN_NAME, true,
            "{\"username\":\"" + BootstrapGraphDefinition.ADMIN_USERNAME + "\"}");

        // sys_user（id=主体 ID，T-ORG-001 同 ID 双表；密码 BCrypt 哈希落库，无明文）
        SysUser admin = new SysUser();
        admin.setId(subjectId);
        admin.setTenantId(tenantId);
        admin.setUsername(BootstrapGraphDefinition.ADMIN_USERNAME);
        admin.setPassword(BCrypt.hashpw(adminPassword));
        admin.setName(BootstrapGraphDefinition.ADMIN_NAME);
        admin.setStatus(1);
        admin.setGender(0);
        admin.setUserType(SYS_USER_TYPE_PERSON);
        // 密码经环境变量由管理员自设（非随机分发），不强制改密（2026-08-24 用户决策）
        admin.setForceResetPwd(false);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        admin.setDeleteFlag(0L);
        userDomainService.insert(admin);

        // 管理用功能角色 + ROLE 投影
        Long roleId = subjectDomainService.createRole(tenantId, null, basicRoleType,
            BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID, BootstrapGraphDefinition.ADMIN_ROLE_NAME,
            0, "{}");
        localProjectionDomainService.upsertRoleResource(
            tenantId, roleId, BootstrapGraphDefinition.ADMIN_ROLE_NAME, 1, null);

        // 绑定
        seedWriter.insertRoleBinding(tenantId, subjectId, roleId);

        // SERVICE 资源（固定图种子对象；MANAGE_API_MAPPING 已类型级，保留供未来实例级授权）
        Long serviceResourceId = seedWriter.insertResource(tenantId,
            resourceTypes.get(ResourceTypeCode.SERVICE),
            BootstrapGraphDefinition.SERVICE_RESOURCE_CODE, BootstrapGraphDefinition.SERVICE_RESOURCE_NAME);

        // API 资源 + 映射（目标接口仅资源、无映射）
        Map<String, Long> apiResourceIds = new LinkedHashMap<>();
        int mappingCount = 0;
        for (BootstrapGraphDefinition.ApiRoute route : BootstrapGraphDefinition.apiRoutes()) {
            String code = BootstrapGraphDefinition.apiResourceCode(route.method(), route.path());
            Long resourceId = seedWriter.insertResource(
                tenantId, resourceTypes.get(ResourceTypeCode.API), code, route.name());
            apiResourceIds.put(code, resourceId);
            if (route.withMapping()) {
                seedWriter.insertApiMapping(tenantId, resourceId, route.method(), route.path());
                mappingCount++;
            }
        }

        // 授权（业务门禁全 scopeAll + 实例级 API:ACCESS；清单见 BootstrapGraphDefinition，计数以 AccessBootstrapPgIT 断言为准）
        List<RoleResourcePermission> grants = buildExpectedGrants(
            tenantId, roleId, resourceTypes, operationBits, apiResourceIds, serviceResourceId);
        seedWriter.insertGrants(tenantId, roleId, grants);

        // 缓存失效登记（@PermissionChange afterCommit 统一 flush；空库首建无旧快照，防御性登记）
        PermissionChangeContext.markUsers(tenantId, Set.of(subjectId));
        PermissionChangeContext.markRoles(tenantId, Set.of(roleId));

        log.info("Bootstrap graph created: tenant={}, adminSubjectId={}, roleId={}, "
                + "apiResources={}, mappings={}, grants={}",
            tenantId, subjectId, roleId, apiResourceIds.size(), mappingCount, grants.size());
    }

    // ===== 期望授权构造（检测比对与创建落库共用） =====

    /**
     * 按固定图定义构造期望授权（无 id）。resourceCode 为 null 的条目 scopeAll=true；
     * 实例条目的资源 id 来自 API/SERVICE 资源定位结果——检测场景下资源缺失的条目跳过
     * （资源缺失已由检测先行报告，此处不重复、不空指针）。
     */
    private List<RoleResourcePermission> buildExpectedGrants(Long tenantId, Long roleId,
                                                             Map<String, Integer> resourceTypes,
                                                             Map<String, Long> operationBits,
                                                             Map<String, Long> apiResourceIds,
                                                             Long serviceResourceId) {
        LocalDateTime now = LocalDateTime.now();
        List<RoleResourcePermission> grants = new ArrayList<>();
        for (BootstrapGraphDefinition.GrantSpec spec : BootstrapGraphDefinition.allGrants()) {
            boolean scopeAll = spec.resourceCode() == null;
            Long resourceEntityId = null;
            if (!scopeAll) {
                resourceEntityId = ResourceTypeCode.API.equals(spec.resourceTypeCode())
                    ? apiResourceIds.get(spec.resourceCode())
                    : serviceResourceId;
                if (resourceEntityId == null) {
                    continue;
                }
            }
            Long bits = operationBits.get(spec.resourceTypeCode() + ":" + spec.operationCode());
            if (bits == null) {
                continue;
            }
            RoleResourcePermission grant = new RoleResourcePermission();
            grant.setTenantId(tenantId);
            grant.setAbstractRoleId(roleId);
            grant.setResourceEntityId(resourceEntityId);
            grant.setGrantedBits(bits);
            grant.setResourceType(resourceTypes.get(spec.resourceTypeCode()));
            grant.setScopeAll(scopeAll);
            grant.setCanGrant(spec.canGrant());
            grant.setGrantSource(GrantSource.MANUAL.getValue());
            grant.setCreatedAt(now);
            grant.setUpdatedAt(now);
            grant.setDeleteFlag(0L);
            grants.add(grant);
        }
        return grants;
    }

    /**
     * 授权匹配键：范围/资源 + 类型 + 操作位 + 转授位 + 完整可变属性（canGrant 参与匹配——目标 API
     * 授权传递链依赖；conditionId/dependOn/grantSource 参与匹配——条件授权或依赖派生（AUTO_DEP）
     * 行不能冒充固定图要求的无条件 MANUAL 直接授权，否则生命周期不再由固定图控制）。
     */
    private record GrantKey(Long resourceEntityId, Integer resourceType, Long grantedBits,
                            boolean scopeAll, boolean canGrant, Long conditionId, Long dependOn,
                            String grantSource) {
        static GrantKey of(RoleResourcePermission p) {
            return new GrantKey(p.getResourceEntityId(), p.getResourceType(), p.getGrantedBits(),
                Boolean.TRUE.equals(p.getScopeAll()), Boolean.TRUE.equals(p.getCanGrant()),
                p.getConditionId(), p.getDependOn(), p.getGrantSource());
        }
    }

    // ===== 种子解析（三状态判定前的显式前置检查） =====

    /** 固定图授权涉及的资源类型值（经 type_definition 批量解析，逐项 fail-fast）。 */
    private Map<String, Integer> resolveGrantResourceTypes(Long tenantId) {
        Map<String, Integer> values = typeResolutionService.batchResolveTypeValues(
            tenantId, TYPE_KEY_RESOURCE, GRANT_RESOURCE_TYPES);
        for (String code : GRANT_RESOURCE_TYPES) {
            if (values.get(code) == null) {
                throw new IllegalStateException("type_definition 种子缺失: "
                    + TYPE_KEY_RESOURCE + "/" + code
                    + " —— 请确认唯一权威 DDL 已完整执行（docs/design/schema/access-service.sql）");
            }
        }
        return values;
    }

    private Integer requireType(Long tenantId, String typeKey, String typeCode) {
        Integer value = typeResolutionService.resolveTypeValue(tenantId, typeKey, typeCode);
        if (value == null) {
            throw new IllegalStateException("type_definition 种子缺失: " + typeKey + "/" + typeCode
                + " —— 请确认唯一权威 DDL 已完整执行（docs/design/schema/access-service.sql）");
        }
        return value;
    }

    /** 固定图全部授权所需操作位（resourceTypeCode:operationCode → binary_bit，缺失 fail-fast）。 */
    private Map<String, Long> loadOperationBits(Long tenantId, Map<String, Integer> resourceTypes) {
        Set<Integer> typeValues = new HashSet<>(resourceTypes.values());
        Set<String> operationCodes = BootstrapGraphDefinition.allGrants().stream()
            .map(BootstrapGraphDefinition.GrantSpec::operationCode)
            .collect(Collectors.toSet());
        Map<Integer, String> typeValueToCode = new HashMap<>();
        resourceTypes.forEach((code, value) -> typeValueToCode.put(value, code));

        Map<String, Long> bits = new HashMap<>();
        for (OperationPermission op : seedWriter.findOperations(tenantId, typeValues, operationCodes)) {
            String typeCode = typeValueToCode.get(op.getResourceType());
            if (typeCode != null) {
                bits.put(typeCode + ":" + op.getCode(), op.getBinaryBit());
            }
        }
        for (BootstrapGraphDefinition.GrantSpec spec : BootstrapGraphDefinition.allGrants()) {
            if (!bits.containsKey(spec.resourceTypeCode() + ":" + spec.operationCode())) {
                throw new IllegalStateException("operation_permission 种子缺失: "
                    + spec.resourceTypeCode() + ":" + spec.operationCode()
                    + " —— 请确认唯一权威 DDL 已完整执行（docs/design/schema/access-service.sql）");
            }
        }
        return bits;
    }
}
