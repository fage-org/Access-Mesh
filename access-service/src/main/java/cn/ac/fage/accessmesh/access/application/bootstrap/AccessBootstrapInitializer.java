package cn.ac.fage.accessmesh.access.application.bootstrap;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
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
 *   <li><b>部分存在 / 固定业务键被其他数据占用</b> → 抛 IllegalStateException 报告全部冲突项，
 *       不自动修复、不补权、不扩权（启动失败 fail-fast）。授权属性漂移除外（2026-09-02 口径定案，
 *       T-FE-018 联调暴露：canGrant/condition/操作位等管理端运营修改是产品正常能力）——
 *       身份行存在而属性不符仅 warn 放行，不构成冲突、不重种覆盖；授权缺行 + 同身份键软删墓碑
 *       亦除外（2026-09-05 三分定案 T-ACCESS-029：管理端整行撤销 → WARN 放行不补回，仅
 *       无任何历史记录的缺行维持 fail-fast）。</li>
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
        ResourceTypeCode.ORG,
        ResourceTypeCode.TYPE_DEFINITION, ResourceTypeCode.RESOURCE, ResourceTypeCode.OPERATION,
        ResourceTypeCode.OPERATION_LOG, ResourceTypeCode.PERMISSION_CHANGE_LOG, ResourceTypeCode.DOMAIN,
        ResourceTypeCode.CONFLICT_RULE, ResourceTypeCode.CONDITION, ResourceTypeCode.DEPENDENCY,
        ResourceTypeCode.SYSTEM_CONFIG);

    /** sys_user.user_type：本地用户管理展示值（与 createUser 链一致；权限域类型由投影链解析） */
    private static final int SYS_USER_TYPE_PERSON = 1;

    private static final String TYPE_KEY_RESOURCE = "resource_type";
    private static final String TYPE_KEY_ROLE = "role_type";
    private static final String TYPE_KEY_USER = "user_type";

    private final UserDomainService userDomainService;
    private final MenuDomainService menuDomainService;
    private final OrgDomainService orgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final UserOrgDomainService userOrgDomainService;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final BootstrapSeedWriter seedWriter;

    public AccessBootstrapInitializer(UserDomainService userDomainService,
                                      MenuDomainService menuDomainService,
                                      OrgDomainService orgDomainService,
                                      OrgTreeConfigDomainService orgTreeConfigDomainService,
                                      UserOrgDomainService userOrgDomainService,
                                      LocalProjectionDomainService localProjectionDomainService,
                                      SubjectDomainService subjectDomainService,
                                      TypeResolutionService typeResolutionService,
                                      BootstrapSeedWriter seedWriter) {
        this.userDomainService = userDomainService;
        this.menuDomainService = menuDomainService;
        this.orgDomainService = orgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.userOrgDomainService = userOrgDomainService;
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
        // ADMIN_FILE 文件夹投影预置（T-ADMIN-025）：幂等 insert-if-absent，置于三状态检测之前——
        // no-op 路径同样执行（建于本特性前的库重启自愈补种），冲突/创建失败路径随事务整体回滚。
        // 不参与固定图检测：文件夹无管理面写入口（类型 SYNC+access-service，资源 CRUD 20055），
        // 缺失只可能是库先于本特性存在，无运营意图可保护（对齐 MENU/ORG 投影豁免口径）
        for (BootstrapGraphDefinition.FolderSeed seed : BootstrapGraphDefinition.adminFileFolderSeeds()) {
            localProjectionDomainService.ensureAdminFileFolder(tenantId, seed.code(), seed.name());
        }
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

        // —— 授权（固定图全量，子集匹配：固定图条目齐全即可，角色上的多余授权不冲突；
        //    2026-09-02 口径定案【T-FE-018 联调暴露】：缺行 fail-fast、属性漂移放行——
        //    身份键 =（资源实体/范围 + 类型）判定「行」是否存在；grantedBits/canGrant/
        //    conditionId/dependOn/grantSource 为可变属性，管理端运营修改（授权页加条件、
        //    关转授、改操作位）是产品正常能力，漂移仅 warn 不阻断启动、不重种覆盖；
        //    2026-09-05 墓碑三分【T-ACCESS-029，§14.2 收缩通道】：缺行 + 同身份键软删墓碑
        //    （delete_flag=id 历史行）= 管理端整行撤销 → WARN 放行不补回；缺行 + 无任何
        //    历史记录 = 初始化残缺/键被占用/硬删 → 维持 fail-fast ——
        if (rolePresent && roleId != null) {
            Map<GrantIdentity, List<GrantKey>> existingByIdentity =
                seedWriter.findValidGrants(tenantId, roleId).stream()
                    .collect(Collectors.groupingBy(GrantIdentity::of,
                        Collectors.mapping(GrantKey::of, Collectors.toList())));
            List<RoleResourcePermission> missingGrants = new ArrayList<>();
            for (RoleResourcePermission grant : buildExpectedGrants(
                    tenantId, roleId, resourceTypes, operationBits, apiResourceIds, serviceResourceId)) {
                List<GrantKey> candidates = existingByIdentity.get(GrantIdentity.of(grant));
                if (candidates == null) {
                    missingGrants.add(grant);
                } else if (!candidates.contains(GrantKey.of(grant))) {
                    log.warn("bootstrap 固定图授权属性漂移（管理端运营修改，放行不重种）: {} "
                        + "固定图期望属性={} 实际={}",
                        grantIdentityDesc(grant), GrantKey.of(grant), candidates);
                }
            }
            classifyMissingGrants(tenantId, roleId, missingGrants, conflicts);
        }

        // —— 菜单种子（T-FE-015；path 为期望键子集匹配：固定图 15 行齐全即可，
        //    管理页后建的额外菜单不冲突。结构键 menuType/resourceType/resourceCode/parentPath/status
        //    严格比对（resourceCode 种子恒 null，实例挂接会改变可见性派生口径），
        //    displayName/icon/sortOrder 容忍漂移——菜单管理页可改，不构成固定图冲突） ——
        Map<String, SysMenu> menusByPath = menuDomainService.selectAllValid(tenantId).stream()
            .collect(Collectors.toMap(SysMenu::getPath, m -> m, (a, b) -> a));
        int menusMatched = 0;
        for (BootstrapGraphDefinition.MenuSeed seed : BootstrapGraphDefinition.menuSeeds()) {
            SysMenu existing = menusByPath.get(seed.path());
            if (existing == null) {
                continue;
            }
            menusMatched++;
            if (!seed.menuType().equals(existing.getMenuType())) {
                conflicts.add("菜单 '" + seed.path() + "' menuType=" + existing.getMenuType()
                    + " 与固定图期望 " + seed.menuType() + " 不符");
            }
            if (!Objects.equals(seed.resourceType(), existing.getResourceType())) {
                conflicts.add("菜单 '" + seed.path() + "' resourceType=" + existing.getResourceType()
                    + " 与固定图期望 " + seed.resourceType() + " 不符（可见性派生口径漂移）");
            }
            if (!Objects.equals(seed.resourceCode(), existing.getResourceCode())) {
                conflicts.add("菜单 '" + seed.path() + "' resourceCode=" + existing.getResourceCode()
                    + " 与固定图期望 " + seed.resourceCode() + " 不符（种子恒 null；实例挂接把"
                    + " scopeAll 派生改为实例授权派生，普通用户可见性随之改变）");
            }
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                conflicts.add("菜单 '" + seed.path() + "' 已停用 (status=" + existing.getStatus() + ")");
            }
            Long expectedParentId = seed.parentPath() == null ? null
                : (menusByPath.get(seed.parentPath()) != null ? menusByPath.get(seed.parentPath()).getId() : null);
            boolean parentOk = seed.parentPath() == null
                ? existing.getParentId() == null || existing.getParentId() == 0L
                : Objects.equals(existing.getParentId(), expectedParentId);
            if (!parentOk) {
                conflicts.add("菜单 '" + seed.path() + "' parentId=" + existing.getParentId()
                    + " 与固定图期望父级（" + (seed.parentPath() == null ? "顶层" : seed.parentPath()) + "）不符");
            }
        }
        boolean menusPresent = menusMatched > 0;
        boolean menusComplete = menusMatched == BootstrapGraphDefinition.menuSeeds().size();
        if (menusPresent && !menusComplete) {
            conflicts.add("菜单种子部分存在: " + menusMatched + "/"
                + BootstrapGraphDefinition.menuSeeds().size() + "（按 path 匹配）");
        }

        // —— 默认组织树（T-FE-015 设计定案：bootstrap 种默认树；/user/page 与 member-candidates
        //    为默认树身份目录视图，无默认树配置则用户列表恒空。检测键：默认配置 → 根组织 code
        //    稳定业务键 + 结构键（status/orgType/parent）+ 树配置 treeType/singleAssoc + admin
        //    直绑根组织；根组织名称容忍改名。ORG 资源投影、组织角色投影与组织型 user_role
        //    不参与检测——upsert 语义、管理端改动时自然补齐（与菜单投影豁免同口径） ——
        boolean defaultTreePresent = false;
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (!defaultConfigs.isEmpty()) {
            defaultTreePresent = true;
            if (defaultConfigs.size() > 1) {
                conflicts.add("默认组织树配置多于一条 (" + defaultConfigs.size()
                    + "，uk_tree_config_default 应已兜底)");
            }
            SysOrgTreeConfig defaultConfig = defaultConfigs.get(0);
            if (!BootstrapGraphDefinition.DEFAULT_TREE_TYPE.equals(defaultConfig.getTreeType())) {
                conflicts.add("默认组织树配置 treeType=" + defaultConfig.getTreeType()
                    + " 与固定图期望 " + BootstrapGraphDefinition.DEFAULT_TREE_TYPE + " 不符");
            }
            if (!Boolean.TRUE.equals(defaultConfig.getSingleAssoc())) {
                conflicts.add("默认组织树配置 singleAssoc=" + defaultConfig.getSingleAssoc()
                    + " 与固定图期望 true 不符（身份目录单归属语义）");
            }
            SysOrg rootOrg = orgDomainService.selectValidById(tenantId, defaultConfig.getRootOrgId());
            if (rootOrg == null) {
                conflicts.add("默认组织树配置指向的根组织不存在 (rootOrgId="
                    + defaultConfig.getRootOrgId() + ")");
            } else {
                if (!BootstrapGraphDefinition.DEFAULT_TREE_ROOT_ORG_CODE.equals(rootOrg.getCode())) {
                    conflicts.add("默认组织树根组织业务键漂移: code=" + rootOrg.getCode()
                        + "（固定图期望 '" + BootstrapGraphDefinition.DEFAULT_TREE_ROOT_ORG_CODE + "'）");
                }
                if (rootOrg.getStatus() == null || rootOrg.getStatus() != 1) {
                    conflicts.add("默认组织树根组织已停用 (status=" + rootOrg.getStatus() + ")");
                }
                if (!"1".equals(rootOrg.getOrgType())) {
                    conflicts.add("默认组织树根组织 orgType=" + rootOrg.getOrgType()
                        + " 与固定图期望 1 不符（组织型；漂移使 bindUserOrg 投影轨道错位）");
                }
                if (rootOrg.getParentId() == null || rootOrg.getParentId() != 0L) {
                    conflicts.add("默认组织树根组织 parentId=" + rootOrg.getParentId()
                        + " 与固定图期望 0 不符（根组织必须直挂顶层）");
                }
                if (adminPresent && adminSubjectId != null) {
                    boolean adminBound = userOrgDomainService.findByUserId(tenantId, adminSubjectId).stream()
                        .anyMatch(uo -> rootOrg.getId().equals(uo.getOrgId()));
                    if (!adminBound) {
                        conflicts.add("首管理员未挂默认树根组织（身份目录开箱可见 admin 的固定图绑定缺失）");
                    }
                }
            }
        }

        boolean apiResourcesComplete = apiResourceIds.size() == expectedApiCodes.size();
        boolean complete = adminPresent && rolePresent && serviceResourcePresent && apiResourcesComplete
            && menusComplete && defaultTreePresent;
        if (!complete) {
            // 状态③而非①：任一固定图对象已存在而其余缺失时按"部分存在"报告，
            // 避免走创建链撞唯一约束（报不可诊断的数据库异常）
            boolean anyPresent = adminPresent || rolePresent || serviceResourcePresent
                || !apiResourceIds.isEmpty() || menusPresent || defaultTreePresent;
            if (anyPresent) {
                conflicts.add("固定图部分存在: admin=" + adminPresent + ", 管理角色=" + rolePresent
                    + ", SERVICE资源=" + serviceResourcePresent
                    + ", API资源=" + apiResourceIds.size() + "/" + expectedApiCodes.size()
                    + ", 菜单种子=" + menusMatched + "/" + BootstrapGraphDefinition.menuSeeds().size()
                    + ", 默认树=" + defaultTreePresent);
            }
        }
        return complete;
    }

    /** 映射存在键：serviceCode|resourceId|METHOD|path（与 resource_api_mapping 唯一索引同构——Gateway 快照按 serviceCode 过滤）。 */
    private static String mappingKey(String serviceCode, Long resourceEntityId,
                                     String httpMethod, String pathPattern) {
        return serviceCode + "|" + resourceEntityId + "|" + httpMethod.toUpperCase() + "|" + pathPattern;
    }

    /**
     * 缺行授权三分判定（T-ACCESS-029，2026-09-05 定案，架构 §14.2 收缩通道）：缺行 + 同身份键
     * 软删墓碑 = 管理端整行撤销 → WARN（列明授权键）放行、不补回；缺行 + 无任何历史记录 =
     * 初始化残缺/键被占用/硬删 → 维持 fail-fast。墓碑查询仅在实际存在缺行时执行一次；身份键匹配
     * 在内存按 {@link GrantIdentity} 完成（scopeAll 行 resource_entity_id 为 NULL，SQL 等值条件
     * {@code = NULL} 恒不命中，不能下推到 SQL）。已知取舍（定案接受）：误删与故意撤销不可区分；
     * 全瘫场景（撤销全部管理 API 授权）仍需人工恢复——人工恢复路径见 rebuild-runbook。
     */
    private void classifyMissingGrants(Long tenantId, Long roleId,
                                       List<RoleResourcePermission> missingGrants, List<String> conflicts) {
        if (missingGrants.isEmpty()) {
            return;
        }
        Set<GrantIdentity> tombstones = seedWriter.findSoftDeletedGrants(tenantId, roleId).stream()
            .map(GrantIdentity::of)
            .collect(Collectors.toSet());
        List<String> revokedKeys = new ArrayList<>();
        for (RoleResourcePermission grant : missingGrants) {
            if (tombstones.contains(GrantIdentity.of(grant))) {
                revokedKeys.add(grantIdentityDesc(grant));
            } else {
                conflicts.add("管理角色授权缺失（缺行无软删墓碑=非管理端撤销，fail-fast；属性漂移放行）: "
                    + grantIdentityDesc(grant));
            }
        }
        if (!revokedKeys.isEmpty()) {
            log.warn("bootstrap 固定图授权缺行且存在软删墓碑（管理端撤销过，放行不补回；"
                + "误删与撤销不可区分，如需恢复经授权页重新授予）: {}", revokedKeys);
        }
    }

    /** 授权键描述（缺行冲突与墓碑告警共用）：资源类型#操作位@范围+资源实体（scopeAll 无实体段）。 */
    private static String grantIdentityDesc(RoleResourcePermission grant) {
        return grant.getResourceType() + "#bits=" + grant.getGrantedBits()
            + (Boolean.TRUE.equals(grant.getScopeAll()) ? "@ALL" : "@instance")
            + (grant.getResourceEntityId() == null ? "" : grant.getResourceEntityId());
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

        // 菜单种子 + MENU 投影（T-FE-015；逐行 insert 回填主键，种子清单「先父后子」排序保证
        // parentPath 解析时父行已建。投影对齐 MenuWriteAppService「全类型全量维护」终态——
        // 种子行 resource_code 恒 null，可见性派生只走 scopeAll 授权不经投影，投影使菜单实例
        // 在授权页资源树可见。检测分支不校验/不修复投影：upsert 语义、管理页改动时自然补齐）
        LocalDateTime now = LocalDateTime.now();
        List<SysMenu> seedMenus = new ArrayList<>();
        Map<String, Long> seedMenuIds = new LinkedHashMap<>();
        for (BootstrapGraphDefinition.MenuSeed seed : BootstrapGraphDefinition.menuSeeds()) {
            SysMenu menu = new SysMenu();
            menu.setTenantId(tenantId);
            // 种子清单「先父后子」排序的创建期断言：父行缺失（清单排序被破坏）立即失败，
            // 不静默插成顶级菜单（静默错插只能等下次重启 inspect 才暴露）
            menu.setParentId(seed.parentPath() == null ? null
                : Objects.requireNonNull(seedMenuIds.get(seed.parentPath()),
                    "menuSeeds 排序破坏: '" + seed.path() + "' 的父级 '" + seed.parentPath() + "' 未先建"));
            menu.setDisplayName(seed.displayName());
            menu.setPath(seed.path());
            menu.setIcon(seed.icon());
            menu.setSortOrder(seed.sortOrder());
            menu.setMenuType(seed.menuType());
            menu.setStatus(1);
            menu.setResourceType(seed.resourceType());
            menu.setResourceCode(seed.resourceCode());
            menu.setSourceService(BootstrapGraphDefinition.API_SERVICE_CODE);
            menu.setCreatedAt(now);
            menu.setUpdatedAt(now);
            menu.setDeleteFlag(0L);
            menuDomainService.insert(menu);
            seedMenuIds.put(seed.path(), menu.getId());
            seedMenus.add(menu);
        }
        for (SysMenu menu : seedMenus) {
            localProjectionDomainService.upsertAdminMenu(tenantId, menu.getId(),
                menu.getDisplayName(), menu.getParentId(), menu.getStatus(), menu.getSortOrder());
        }

        // 默认组织树（T-FE-015 设计定案）：根组织 + 默认树配置 + 首管理员挂根组织（镜像
        // OrgWriteAppServiceImpl / UserWriteAppServiceImpl 生产链的领域服务写入——bootstrap
        // 无登录态不走带门禁的 AppService）。ORG 投影与 user_role 投影同步维护，缓存失效
        // 登记 visibility（树配置影响 ORG_VISIBILITY 默认树范围）与角色/用户快照。
        SysOrg rootOrg = new SysOrg();
        rootOrg.setTenantId(tenantId);
        rootOrg.setParentId(0L);
        rootOrg.setOrgType("1");
        rootOrg.setCode(BootstrapGraphDefinition.DEFAULT_TREE_ROOT_ORG_CODE);
        rootOrg.setName(BootstrapGraphDefinition.DEFAULT_TREE_ROOT_ORG_NAME);
        rootOrg.setStatus(1);
        rootOrg.setSortOrder(0);
        rootOrg.setLevel(1);
        rootOrg.setCreatedAt(now);
        rootOrg.setUpdatedAt(now);
        rootOrg.setDeleteFlag(0L);
        orgDomainService.insert(rootOrg);
        Long orgRoleId = localProjectionDomainService.upsertAdminOrg(
            tenantId, rootOrg.getId(), rootOrg.getOrgType(), rootOrg.getName(),
            rootOrg.getParentId(), null, rootOrg.getStatus(), rootOrg.getSortOrder(),
            "{\"orgType\":\"1\"}");
        PermissionChangeContext.markRoles(tenantId, orgRoleId);

        SysOrgTreeConfig treeConfig = new SysOrgTreeConfig();
        treeConfig.setTenantId(tenantId);
        treeConfig.setRootOrgId(rootOrg.getId());
        treeConfig.setTreeName(BootstrapGraphDefinition.DEFAULT_TREE_NAME);
        treeConfig.setTreeType(BootstrapGraphDefinition.DEFAULT_TREE_TYPE);
        treeConfig.setIsDefault(true);
        treeConfig.setSingleAssoc(true);
        treeConfig.setCreatedAt(now);
        treeConfig.setUpdatedAt(now);
        treeConfig.setDeleteFlag(0L);
        orgTreeConfigDomainService.insert(treeConfig);

        SysUserOrg adminOrg = new SysUserOrg();
        adminOrg.setTenantId(tenantId);
        adminOrg.setUserId(subjectId);
        adminOrg.setOrgId(rootOrg.getId());
        adminOrg.setIsPrimary(true);
        adminOrg.setCreatedAt(now);
        adminOrg.setUpdatedAt(now);
        adminOrg.setDeleteFlag(0L);
        userOrgDomainService.insertBatch(List.of(adminOrg));
        // 普通组织（orgType=1）→ ORG 轨，relation 指向组织自身 parentId（与生产链一致）
        localProjectionDomainService.bindUserOrg(tenantId, subjectId, rootOrg.getId(),
            "ORG", rootOrg.getParentId());
        PermissionChangeContext.markVisibility(tenantId);

        // 缓存失效登记（@PermissionChange afterCommit 统一 flush；空库首建无旧快照，防御性登记）
        PermissionChangeContext.markUsers(tenantId, Set.of(subjectId));
        PermissionChangeContext.markRoles(tenantId, Set.of(roleId));

        log.info("Bootstrap graph created: tenant={}, adminSubjectId={}, roleId={}, "
                + "apiResources={}, mappings={}, grants={}, menus={}, defaultTreeRootOrg={}",
            tenantId, subjectId, roleId, apiResourceIds.size(), mappingCount, grants.size(),
            seedMenus.size(), rootOrg.getId());
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
            Long bits = operationBits.get(BusinessKeys.operationCodeKey(spec.resourceTypeCode(), spec.operationCode()));
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
     * 授权身份键（2026-09-02 口径定案：缺行判定、属性漂移放行）：资源实体/范围 + 类型——
     * 判定固定图要求的授权「行」是否存在；行在而属性不符属管理端运营修改，仅 warn 不冲突；
     * 行缺（无有效行）的处置按 {@link #classifyMissingGrants} 三分（T-ACCESS-029：
     * 同身份键软删墓碑 WARN 放行 / 无任何历史 fail-fast）。
     */
    private record GrantIdentity(Long resourceEntityId, Integer resourceType, boolean scopeAll) {
        static GrantIdentity of(RoleResourcePermission p) {
            return new GrantIdentity(p.getResourceEntityId(), p.getResourceType(),
                Boolean.TRUE.equals(p.getScopeAll()));
        }
    }

    /**
     * 授权完整属性键（漂移检测与 warn 明细用）：grantedBits/canGrant/conditionId/dependOn/
     * grantSource 全量参与——身份行存在但与本键不符即漂移（运营改操作位/加条件/关转授/
     * AUTO_DEP 行并存等），放行并告警；仅身份键无匹配（缺行）进入
     * {@link #classifyMissingGrants} 三分（墓碑 WARN 放行 / 无历史 fail-fast）。
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
                bits.put(BusinessKeys.operationCodeKey(typeCode, op.getCode()), op.getBinaryBit());
            }
        }
        for (BootstrapGraphDefinition.GrantSpec spec : BootstrapGraphDefinition.allGrants()) {
            String key = BusinessKeys.operationCodeKey(spec.resourceTypeCode(), spec.operationCode());
            if (!bits.containsKey(key)) {
                throw new IllegalStateException("operation_permission 种子缺失: " + key
                    + " —— 请确认唯一权威 DDL 已完整执行（docs/design/schema/access-service.sql）");
            }
        }
        return bits;
    }
}
