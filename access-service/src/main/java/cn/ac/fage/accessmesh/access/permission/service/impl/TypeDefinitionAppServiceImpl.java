package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 类型定义应用服务实现类
 * <p>
 * 提供类型定义的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class TypeDefinitionAppServiceImpl implements TypeDefinitionAppService {

    private final TypeDefinitionMapper typeDefinitionMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final PermQueryEngine engine;
    private final ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    private final ResourceEntityDomainService resourceEntityDomainService;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final SubjectDomainService subjectDomainService;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final ResourceApiMappingMapper apiMappingMapper;
    private final TreeWriteLockSupport treeWriteLockSupport;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param typeDefinitionMapper      类型定义数据访问层
     * @param operationPermissionMapper 操作权限数据访问层（resource_type 联动预置写入，T-PERM-028）
     * @param engine                    权限查询引擎
     * @param resourceTypeOwnershipGuard 资源类型所有权守卫（extra.managedMode 声明校验与变更守卫，T-PERM-052）
     * @param resourceEntityDomainService 资源实体域服务（行数守卫查询 + 投影行软删）
     * @param localProjectionDomainService 本地投影域服务（TYPE_DEFINITION 实例投影同事务维护，T-PERM-051）
     * @param subjectDomainService      主体域服务（user_type/role_type 删除引用面行数守卫，T-PERM-056）
     * @param rolePermMapper            授权数据访问层（类型软删级联处置投影行下授权行，T-PERM-051）
     * @param apiMappingMapper          API 映射数据访问层（删除级联的受影响服务查询，deleteResources 同款）
     * @param cacheService              统一缓存入口（类型解析缓存提交后失效，codex 三轮复评 P1-2）
     */
    public TypeDefinitionAppServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                         OperationPermissionMapper operationPermissionMapper,
                                         PermQueryEngine engine,
                                         ResourceTypeOwnershipGuard resourceTypeOwnershipGuard,
                                         ResourceEntityDomainService resourceEntityDomainService,
                                         LocalProjectionDomainService localProjectionDomainService,
                                         SubjectDomainService subjectDomainService,
                                         RoleResourcePermissionMapper rolePermMapper,
                                         ResourceApiMappingMapper apiMappingMapper,
                                         TreeWriteLockSupport treeWriteLockSupport,
                                         CacheService cacheService) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.engine = engine;
        this.resourceTypeOwnershipGuard = resourceTypeOwnershipGuard;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.localProjectionDomainService = localProjectionDomainService;
        this.subjectDomainService = subjectDomainService;
        this.rolePermMapper = rolePermMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.treeWriteLockSupport = treeWriteLockSupport;
        this.cacheService = cacheService;
    }

    /**
     * 创建类型定义
     * <p>
     * typeValue 由服务端在 tenant+typeKey 内自动分配（全量行含软删行 max+1，软删不复用）；
     * typeCode 留空时按 {@code <TYPEKEY大写>_<typeValue>} 生成（如 resource_type 的 12 号 → RESOURCE_TYPE_12，经 BusinessKeys.generatedTypeCode 构造），显式提供时校验 tenant+typeKey 内唯一；
     * isSystem 固定 false——系统预置类型仅走租户初始化种子，不可由 API 创建。
     * 同事务维护 TYPE_DEFINITION 实例投影（code={typeKey}:{typeCode}，T-PERM-051）。
     * 需要TYPE_DEFINITION_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含类型键、名称等
     * @param operatorId 操作者ID，可选
     * @return 创建的类型定义响应
     * @throws SecurityException 无权限时抛出
     * @throws BizException      typeCode 重复、显式码抢占生成码、并发分配撞值时抛出（均 20049，DB 唯一索引兜底）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "TYPE_DEFINITION_CREATE", targetType = "type_definition", targetId = "#result.id()", summary = "'create type definition ' + #req.typeKey() + ':' + #result.typeCode()")
    public TypeDefinitionResp createType(Long tenantId, TypeCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on TYPE_DEFINITION");
        }

        // codex 三轮复评 P1-1：resource_type 类型创建与资源写入口共持 (resource_entity, 租户) 树写锁
        // （锁先于首次类型读取）——createResource/batchCreate 的所有权门禁对「类型不存在」放行
        // （由存在性校验兜底），创建类型入口不持锁时「门禁放行→并发建 SYNC 类型→资源插入落库」
        // 交错破坏单一所有权；同锁亦顺带封闭并发同码建类型的查重窗口
        if ("resource_type".equals(req.typeKey())) {
            treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        }

        // typeValue 自动分配：全量行（含软删行）max+1，软删不复用（T-PERM-023，收敛 T-PERM-019 D1）
        Integer maxTypeValue = typeDefinitionMapper.selectMaxTypeValueAllRows(tenantId, req.typeKey());
        int typeValue = (maxTypeValue != null ? maxTypeValue : 0) + 1;

        String typeCode = req.typeCode();
        if (typeCode == null || typeCode.isBlank()) {
            typeCode = BusinessKeys.generatedTypeCode(req.typeKey(), typeValue);
        } else {
            typeCode = typeCode.trim();
            if (typeDefinitionMapper.selectByTypeKeyAndCode(tenantId, req.typeKey(), typeCode) != null) {
                throw new BizException(PermissionErrorCode.TYPE_DEFINITION_CODE_DUPLICATE.getCode(),
                    "Type code already exists: " + typeCode);
            }
        }

        // T-PERM-052：extra 所有权声明结构校验（managedMode/syncSourceService 仅 resource_type、
        // 值域/首尾空白/来源引用/API 类型禁 SYNC 校验；对齐 SyncTypeGuard.validateSyncTypesExtra
        // 的保存边界先例）。create 恒 is_system=false——内部来源 access-service 仅系统预置类型可声明（种子）
        try {
            resourceTypeOwnershipGuard.validateExtraDeclaration(tenantId, req.typeKey(), typeCode, req.extra(), false);
        } catch (IllegalArgumentException e) {
            throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                PermissionErrorCode.INVALID_PARAM.getMessage() + ": " + e.getMessage());
        }

        TypeDefinition type = new TypeDefinition();
        type.setTenantId(tenantId);
        type.setTypeKey(req.typeKey());
        type.setTypeCode(typeCode);
        type.setTypeValue(typeValue);
        type.setName(req.name());
        type.setDescription(req.description());
        type.setIsSystem(false);
        type.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        type.setExtra(req.extra());
        type.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        type.setCreatedAt(now);
        type.setUpdatedAt(now);
        type.setDeleteFlag(0L);
        try {
            typeDefinitionMapper.insert(type);
        } catch (DataIntegrityViolationException e) {
            // DB 唯一索引兜底（ConflictRule 同模式）：显式码抢占未来生成码（生成路径不查重）、
            // check-then-insert 并发窗口、max+1 并发撞值——均映射 20049 而非裸 99999
            if (isUniqueViolationOn(e, "uk_type_definition_code")) {
                throw new BizException(PermissionErrorCode.TYPE_DEFINITION_CODE_DUPLICATE.getCode(),
                    "Type code already exists: " + typeCode);
            }
            if (isUniqueViolationOn(e, "uk_type_definition_value")) {
                throw new BizException(PermissionErrorCode.TYPE_DEFINITION_CODE_DUPLICATE.getCode(),
                    "类型值分配冲突（并发创建），请重试");
            }
            throw e;
        }
        // T-PERM-051：类型定义行同事务维护 TYPE_DEFINITION 实例投影（code={typeKey}:{typeCode}
        // 复合业务键，经 LocalProjectionDomainService 落库，owner=access-service；类型种子已声明
        // SYNC+access-service，人工/外部不得经资源管理面构造同类行，单 writer 口径成立）
        localProjectionDomainService.upsertTypeDefinitionResource(tenantId, req.typeKey(), typeCode, req.name());
        // T-PERM-028：resource_type 新类型联动预置 CRUD 操作位（同事务；schema 表注释承诺、
        // 原 DDL CROSS JOIN 预置仅覆盖建库时既有类型）。模板对齐 DDL 预置组：
        // CREATE(1,0)/VIEW(2,0)/UPDATE(4,2)/DELETE(8,2)；新类型位段空闲无 uk_typed_bit 冲突。
        // 跨域写入先例：ServiceConfig 删除级联直写 apiMappingMapper（T-PERM-027）。
        if ("resource_type".equals(req.typeKey())) {
            insertPresetOperations(tenantId, typeValue, operatorId, now);
            // T-PERM-047：预置操作位同样改变该类型操作集合，提交后失效 per-type 缓存。
            // 当前 typeValue 为全量行（含软删）max+1、软删不复用，新值键必为冷键——
            // 此处失效是语义完备性接线（写路径变更集合即失效），不依赖分配策略不变
            cacheService.evictAfterCommit(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId,
                PermCacheCatalog.operationPermissionsByTypeKey(typeValue));
        }
        // codex 三轮复评 P1-2：新建类型提交后失效双向解析缓存键（删建同码不同值时旧 code→value
        // 与新值反向键都不得残留）
        evictTypeResolutionCachesAfterCommit(tenantId, req.typeKey(), typeCode, typeValue);
        return toTypeResp(type);
    }

    /**
     * 类型解析缓存双向失效（codex 三轮复评 P1-2）：TYPE_VALUE（code→value）与 TYPE_CODE
     * （value→code）均 10s L2 且 null 不缓存——类型创建/删除提交后失效对应键，防「删类型→
     * 10s 内解析命中陈旧缓存」把已删类型值继续喂给消费方（管理面创建已同步改为门禁权威值，
     * 此处失效保护其余全部解析消费方）。updateType 不涉及（typeCode/typeValue 不可变）。
     */
    private void evictTypeResolutionCachesAfterCommit(Long tenantId, String typeKey, String typeCode, Integer typeValue) {
        cacheService.evictAfterCommit(PermCacheCatalog.TYPE_VALUE, tenantId, BusinessKeys.typeValueCacheKey(typeKey, typeCode));
        if (typeValue != null) {
            cacheService.evictAfterCommit(PermCacheCatalog.TYPE_CODE, tenantId, BusinessKeys.typeCodeCacheKey(typeKey, typeValue));
        }
    }

    /**
     * 为新 resource_type 预置 CRUD 四操作位
     *
     * @param tenantId   租户ID
     * @param typeValue  新类型的内部值（operation_permission.resource_type）
     * @param operatorId 操作者ID（created_by）
     * @param now        创建时间（与类型定义行同时刻）
     */
    private void insertPresetOperations(Long tenantId, int typeValue, Long operatorId, LocalDateTime now) {
        String[][] preset = {
            {"CREATE", "创建", "1", "0"},
            {"VIEW", "查看", "2", "0"},
            {"UPDATE", "更新", "4", "2"},
            {"DELETE", "删除", "8", "2"}
        };
        List<OperationPermission> toInsert = new java.util.ArrayList<>(preset.length);
        for (String[] row : preset) {
            OperationPermission op = new OperationPermission();
            op.setTenantId(tenantId);
            op.setResourceType(typeValue);
            op.setCode(row[0]);
            op.setName(row[1]);
            op.setBinaryBit(Long.parseLong(row[2]));
            op.setInheritMask(Long.parseLong(row[3]));
            op.setCreatedBy(operatorId);
            op.setCreatedAt(now);
            op.setUpdatedAt(now);
            op.setDeleteFlag(0L);
            toInsert.add(op);
        }
        operationPermissionMapper.insertBatch(toInsert);
    }

    /**
     * PG 唯一约束违反消息含约束名，沿 cause 链匹配（ConflictRule 同模式）
     */
    private boolean isUniqueViolationOn(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains(constraintName)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 获取类型定义详情
     * <p>
     * 根据类型定义ID查询类型的完整信息。
     * 需要TYPE_DEFINITION_VIEW权限（T-PERM-051：实例级门禁按复合业务键
     * {@code {typeKey}:{typeCode}} 判定——先载行取键再门禁；行缺失时退化为类型级
     * 校验保持既有可观察行为：无权限抛 SecurityException、有权限返回 null）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeId   类型定义ID
     * @return 类型定义响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public TypeDefinitionResp getType(Long tenantId, Long typeId) {
        Long operatorId = OperatorContext.getOperatorId();
        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, typeId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION,
                type != null ? instanceBusinessKey(type) : null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION:" + typeId);
        }
        return type != null ? toTypeResp(type) : null;
    }

    /**
     * 按条件统计有效类型定义数量
     * <p>
     * 需要TYPE_DEFINITION_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键，可选（精确过滤）
     * @param keyword  关键字，可选（name/typeCode LIKE，大小写敏感）
     * @return 有效行数
     */
    @Override
    @Transactional(readOnly = true)
    public long countTypes(Long tenantId, String typeKey, String keyword) {
        Long operatorId = OperatorContext.getOperatorId();
        requireTypeViewPermission(tenantId, operatorId);
        return typeDefinitionMapper.countByCondition(tenantId, normalize(typeKey), normalize(keyword));
    }

    /**
     * 按条件分页查询类型定义
     * <p>
     * 需要TYPE_DEFINITION_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键，可选（精确过滤）
     * @param keyword  关键字，可选（name/typeCode LIKE，大小写敏感）
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 类型定义响应列表（ORDER BY sort_order, id）
     */
    @Override
    @Transactional(readOnly = true)
    public List<TypeDefinitionResp> listTypes(Long tenantId, String typeKey, String keyword, int offset, int limit) {
        Long operatorId = OperatorContext.getOperatorId();
        requireTypeViewPermission(tenantId, operatorId);
        return typeDefinitionMapper.selectPageByCondition(tenantId, normalize(typeKey), normalize(keyword), limit, offset)
            .stream().map(this::toTypeResp).collect(Collectors.toList());
    }

    /**
     * 类型定义查看门禁（2026-09-03 用户决策放宽：与登录权限串投影口径对齐）。
     * <p>
     * 类型级 TYPE_DEFINITION:VIEW，或任一实例级 VIEW（对任一具体类型实例的授权），
     * 均可查询类型清单。此前仅认类型级，而登录权限串全集含实例级授权——前端
     * hasPerms 探查通过、后端拒绝，出现口径不一致（T-FE-018 评审发现）。
     * 实例级判定经 getDeniedResourceCodes 批量判定（内部经类型解析批量处理，无 N+1；
     * 无投影实体的 code 计入拒绝集合 fail-closed）。
     * </p>
     * <p>
     * T-PERM-051：实例业务键统一为复合键 {@code {typeKey}:{typeCode}}（typeCode 仅
     * tenant+type_key 内唯一，种子 user_type 与 resource_type 均有 USER/SERVICE 同名行，
     * 裸 typeCode 无法唯一命中投影行）；全拒判定沿用去重码集比较（复合键行级唯一，
     * 去重语义不变）。
     * </p>
     */
    private void requireTypeViewPermission(Long tenantId, Long operatorId) {
        if (engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION,
                null, OperationCodeConstants.VIEW)) {
            return;
        }
        // 全拒判定必须与引擎入参同一去重集合比较：复合键虽行级唯一，保持去重集合入参
        // 与 denied.size() 比较的同一口径（跨 type_key 重码 fail-open 修复语义不变，2026-09-03）
        Set<String> codes = typeDefinitionMapper.selectValidByTenant(tenantId).stream()
            .map(this::instanceBusinessKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> denied = engine.getDeniedResourceCodes(tenantId, operatorId,
                ResourceTypeCode.TYPE_DEFINITION, codes, OperationCodeConstants.VIEW);
        if (codes.isEmpty() || denied.size() >= codes.size()) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION");
        }
    }

    /**
     * TYPE_DEFINITION 实例复合业务键（T-PERM-051 定案 {typeKey}:{typeCode}，
     * 构造唯一入口 BusinessKeys.typeInstanceBusinessKey，格式 golden 锁定）
     */
    private String instanceBusinessKey(TypeDefinition type) {
        return BusinessKeys.typeInstanceBusinessKey(type.getTypeKey(), type.getTypeCode());
    }

    /**
     * 过滤参数规整：空白串归一为 null（与 SQL <if> 判空语义一致）
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 更新类型定义
     * <p>
     * 更新类型定义的名称、描述、排序顺序、扩展属性等。
     * 需要TYPE_DEFINITION_MANAGE权限（T-PERM-051：实例级门禁按复合业务键
     * {@code {typeKey}:{typeCode}} 判定——先载行取键再门禁；行缺失时退化为类型级
     * 校验，无权限先于 NOT_FOUND 抛出，保持既有可观察行为）。
     * name 变更同事务同步 TYPE_DEFINITION 投影行（description/sortOrder 不投影）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含类型ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的类型定义响应
     * @throws SecurityException     无权限时抛出
     * @throws BizException          类型定义不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "TYPE_DEFINITION_UPDATE", targetType = "type_definition", targetId = "#req.typeId()", summary = "'update type definition ' + #req.typeId()")
    public TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // T-PERM-051：复合业务键需先载行（typeCode/typeKey 不可变，锁内重读不改变键）
        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, req.typeId());
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION,
                type != null ? instanceBusinessKey(type) : null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + req.typeId());
        }
        if (type == null) throw new BizException(PermissionErrorCode.TYPE_DEFINITION_NOT_FOUND.getCode(), "Type not found: " + req.typeId());
        // codex 复评 P1：resource_type 类型的声明变更与资源写入口共持 (resource_entity, 租户)
        // 树写锁（锁内重读，T-PERM-044 先例）——堵「行数守卫查零行→并发资源插入→声明变更/删除
        // 落库」交错窗口；sync 入口门禁同样在锁后（见 ResourceEntitySyncAppServiceImpl）
        if ("resource_type".equals(type.getTypeKey())) {
            treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
            type = typeDefinitionMapper.selectValidById(tenantId, req.typeId());
            if (type == null) throw new BizException(PermissionErrorCode.TYPE_DEFINITION_NOT_FOUND.getCode(), "Type not found: " + req.typeId());
        }
        // T-PERM-052：extra 所有权声明结构校验 + 有效值变更守卫（类型下存在有效资源行时
        // managedMode/syncSourceService 不得变更，含删键隐式切回 MANAGED；20056）。
        // is_system 类型允许声明内部来源 access-service（事实链路类型种子同款，含 T-ADMIN-025 的 ADMIN_FILE）
        try {
            resourceTypeOwnershipGuard.validateExtraDeclaration(tenantId, type.getTypeKey(),
                type.getTypeCode(), req.extra(), Boolean.TRUE.equals(type.getIsSystem()));
        } catch (IllegalArgumentException e) {
            throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                PermissionErrorCode.INVALID_PARAM.getMessage() + ": " + e.getMessage());
        }
        resourceTypeOwnershipGuard.rejectIfDeclarationChangeBlocked(
                tenantId, type, req.extra() != null ? req.extra() : type.getExtra());
        if (req.name() != null) type.setName(req.name());
        if (req.description() != null) type.setDescription(req.description());
        if (req.sortOrder() != null) type.setSortOrder(req.sortOrder());
        if (req.extra() != null) type.setExtra(req.extra());
        type.setUpdatedAt(LocalDateTime.now());
        typeDefinitionMapper.update(type);
        // T-PERM-051：name 变更同步投影展示名（投影无 description/sortOrder 语义；
        // upsert 幂等——名称未实际变化时重写同值无害，与 ROLE/USER 投影同款）
        if (req.name() != null) {
            localProjectionDomainService.upsertTypeDefinitionResource(
                tenantId, type.getTypeKey(), type.getTypeCode(), req.name());
        }
        return toTypeResp(type);
    }

    /**
     * 批量删除类型定义
     * <p>
     * 批量软删除类型定义。系统内置类型（isSystem=true）不可删除。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要TYPE_DEFINITION_MANAGE权限（T-PERM-051：批量门禁按复合业务键
     * {@code {typeKey}:{typeCode}} 编码轨判定——原 type_definition.id 直传实体轨系
     * ID 空间错位；先批量载行构键再判，载行空集退化为类型级校验保持 fail-closed）。
     * 同事务级联：软删 TYPE_DEFINITION 投影行 + 投影行下授权行（deleteResources 同款，
     * 2026-09-07 用户定案级联方案），markRoles/markServiceCodes 提交后失效与广播。
     * T-PERM-050（2026-09-09 定案级联）：被删 resource_type 的操作定义行（含预置 CRUD 四操作位，
     * 对称于创建联动预置）与该类型下有效授权行（正常流仅剩 scope_all 类型级行）同事务级联软删，
     * markRoles 失效角色快照、OPERATION_PERMISSIONS_BY_TYPE 按类型集合提交后失效。
     * T-PERM-056（2026-09-09 用户定案删除保护）：user_type/role_type 类型下存在有效用户/角色行时
     * 整批拒绝删除（对齐 resource_type 行数守卫 20056 先例——用户/角色是业务主体数据，
     * 非类型从属配置，不级联）；role_type 面与角色写入口共持 ABSTRACT_ROLE 树写锁闭合并发交错。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        类型定义ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     * @throws BizException      user_type/role_type 下存在有效用户/角色行时抛出（20056）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "TYPE_DEFINITION_REMOVE", targetType = "type_definition", targetId = "", summary = "'batch remove type definitions'")
    public void deleteTypesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // T-PERM-051：复合业务键需先批量载行（原 getDeniedEntityIds 直传 type_definition.id
        // 系 ID 空间错位）；载行空集（全部不存在/已删）退化为类型级校验——无权限对不存在的
        // id 仍抛 SecurityException，保持既有 fail-closed 可观察行为
        List<TypeDefinition> entities = typeDefinitionMapper.selectValidByIds(tenantId, validInputIds);
        if (entities.isEmpty()) {
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION,
                    null, OperationCodeConstants.MANAGE)) {
                throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + validInputIds);
            }
            OperationLogRuntimeContext.markSkip();
            return;
        }
        // T-PERM-042：引擎纯查询（编码轨批量判定），拒绝时由调用方显式抛出
        Set<String> keys = entities.stream()
            .map(this::instanceBusinessKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> deniedKeys = engine.getDeniedResourceCodes(
            tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, keys, OperationCodeConstants.MANAGE);
        if (!deniedKeys.isEmpty()) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + deniedKeys);
        }

        // codex 复评 P1：含 resource_type 时与资源写入口共持树写锁并锁内重读（同 updateType）；
        // T-PERM-056：含 role_type 时与角色写入口共持 ABSTRACT_ROLE 树写锁——自定义 role_type
        // 角色行的唯一创建入口是角色同步通道（管理面 createRole 经 RoleType.fromValue 枚举校验
        // 只接受种子类型；updateRole/moveRole 可写已存在行但亦持同锁），「行数守卫查零行→并发
        // 建该类型角色→类型删除落库」交错由此闭合。锁序固定 RESOURCE_ENTITY→ABSTRACT_ROLE
        // 单向（角色写路径持锁后不再取 RESOURCE_ENTITY 锁，无对向死锁）
        boolean containsResourceType = entities.stream().anyMatch(e -> "resource_type".equals(e.getTypeKey()));
        boolean containsRoleType = entities.stream().anyMatch(e -> "role_type".equals(e.getTypeKey()));
        if (containsResourceType) {
            treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        }
        if (containsRoleType) {
            treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);
        }
        if (containsResourceType || containsRoleType) {
            entities = typeDefinitionMapper.selectValidByIds(tenantId, validInputIds);
            if (entities.isEmpty()) {
                OperationLogRuntimeContext.markSkip();
                return;
            }
        }

        Set<Long> validIds = entities.stream()
            .filter(e -> !Boolean.TRUE.equals(e.getIsSystem()))
            .map(TypeDefinition::getId)
            .collect(Collectors.toSet());

        if (validIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // T-PERM-052 评审批次（2026-09-05）：类型下存在有效资源行时不可删除（与声明变更守卫同款
        // 20056——软删类型后其行成「外部源失去通道、管理面守卫看不见」的永久孤儿）。整批校验，
        // 任一命中整批拒绝。codex 复评 P2：类型值去重后一次批量查询（循环单查违反 §8.4.8）
        List<TypeDefinition> deletableResourceTypes = entities.stream()
            .filter(e -> validIds.contains(e.getId()) && "resource_type".equals(e.getTypeKey()))
            .toList();
        Set<Integer> deletableTypeValues = deletableResourceTypes.stream()
            .map(TypeDefinition::getTypeValue)
            .collect(Collectors.toSet());
        if (!deletableResourceTypes.isEmpty()) {
            Set<Integer> typesWithRows = resourceEntityDomainService.findTypesWithValidRows(tenantId, deletableTypeValues);
            List<String> conflictCodes = deletableResourceTypes.stream()
                .filter(t -> typesWithRows.contains(t.getTypeValue()))
                .map(TypeDefinition::getTypeCode)
                .toList();
            if (!conflictCodes.isEmpty()) {
                throw new BizException(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(),
                    "类型下存在有效资源行，不可删除: " + String.join(", ", conflictCodes));
            }
        }

        // T-PERM-056（2026-09-09 用户定案删除保护）：user_type/role_type 引用面守卫——存在引用
        // 该 typeValue 的有效 abstract_user/abstract_role 行时整批拒绝（用户/角色是业务主体数据，
        // 对齐 resource_entity 面=守卫，而非操作位/投影=级联）；管理员须先删/迁走该类型用户/角色。
        // 并发语义：role_type 面经上方 ABSTRACT_ROLE 树写锁与全部角色写入口串行闭合；user_type
        // 面用户写入口无锁可复用（用户行无树结构，本无锁需求；为极窄交错给高频用户创建加分布式
        // 锁不成比例），为 best-effort 守卫（对齐 T-PERM-050 级联并发先例）——交错残留由 typeValue
        // 软删不复用兜底（孤儿 user_type 值永不撞新类型，uk_abstract_user 部分索引无冲突恶化）
        rejectIfSubjectTypeReferenced(tenantId, entities, validIds, "user_type", "用户行",
            subjectDomainService::findUserTypesWithValidRows);
        rejectIfSubjectTypeReferenced(tenantId, entities, validIds, "role_type", "角色行",
            subjectDomainService::findRoleTypesWithValidRows);

        // T-PERM-051：投影行级联定位（批量按复合键一次查询；typeCode/typeKey 不可变，
        // 锁内重读不改变键）。isSystem 行不删但保留投影（种子类型不删，投影随之保留）
        Set<String> deletableKeys = entities.stream()
            .filter(e -> validIds.contains(e.getId()))
            .map(this::instanceBusinessKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> projectionIds = localProjectionDomainService.findTypeDefinitionResourceIds(tenantId, deletableKeys);

        // 投影行下授权行级联（deleteResources 同款，2026-09-07 用户定案）：软删前登记受影响
        // roles（ROLE_PERM_SNAPSHOT 含旧 perm）与 serviceCodes（投影行若被 API 映射引用，
        // 删除影响 Gateway 本地快照构建——正常无映射，防御性登记）
        List<Long> permIds = List.of();
        if (!projectionIds.isEmpty()) {
            Set<Long> affectedRoleIds = rolePermMapper.selectRoleIdsByResourceIds(tenantId, projectionIds);
            if (!affectedRoleIds.isEmpty()) {
                PermissionChangeContext.markRoles(tenantId, affectedRoleIds);
            }
            Set<String> affectedServiceCodes = apiMappingMapper.selectByResourceEntityIds(
                tenantId, new HashSet<>(projectionIds)).stream()
                .map(ResourceApiMapping::getServiceCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
            if (!affectedServiceCodes.isEmpty()) {
                PermissionChangeContext.markServiceCodes(tenantId, affectedServiceCodes);
            }
            permIds = rolePermMapper.selectValidPermIdsByResourceIds(tenantId, projectionIds);
        }

        LocalDateTime now = LocalDateTime.now();
        typeDefinitionMapper.softDeleteBatch(tenantId, new ArrayList<>(validIds), now);
        if (!projectionIds.isEmpty()) {
            resourceEntityDomainService.softDeleteBatch(tenantId, projectionIds, now);
        }
        if (!permIds.isEmpty()) {
            rolePermMapper.softDeleteBatch(tenantId, permIds, now);
        }
        // T-PERM-050（2026-09-09 定案级联）：被删 resource_type 的操作定义行与类型级授权行同事务
        // 级联软删——对称于创建联动预置（建时自动生 4 行、删时自动清），与上方投影级联同款。
        // 级联面限定 typeKey=resource_type：type_value 仅 tenant+type_key 内唯一，user_type/role_type
        // 同值删除不得误伤 resource_type 空间（deletableResourceTypes 已按 typeKey 过滤）。
        if (!deletableResourceTypes.isEmpty()) {
            List<Long> operationIds = operationPermissionMapper
                .selectByTenantAndResourceTypes(tenantId, deletableTypeValues).stream()
                .map(OperationPermission::getId)
                .toList();
            if (!operationIds.isEmpty()) {
                operationPermissionMapper.softDeleteBatch(tenantId, operationIds, now);
            }
            // 正常流仅剩 scope_all 类型级行（资源行被行数守卫拒绝、实例级授权随资源删除级联）；
            // 防御性含引用已软删资源行的残留实例行
            Set<Long> typeGrantRoleIds = rolePermMapper.selectRoleIdsByResourceTypes(tenantId, deletableTypeValues);
            if (!typeGrantRoleIds.isEmpty()) {
                PermissionChangeContext.markRoles(tenantId, typeGrantRoleIds);
            }
            List<Long> typeGrantPermIds = rolePermMapper.selectValidPermIdsByResourceTypes(tenantId, deletableTypeValues);
            if (!typeGrantPermIds.isEmpty()) {
                rolePermMapper.softDeleteBatch(tenantId, typeGrantPermIds, now);
            }
            // T-PERM-047 终态复用：操作集合变更提交后按被删类型集合 per-type 失效
            cacheService.evictBatchAfterCommit(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId,
                deletableTypeValues.stream().map(PermCacheCatalog::operationPermissionsByTypeKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        // codex 三轮复评 P1-2：被删类型提交后失效双向解析缓存键（同码重建新值前，旧映射不得残留）；
        // codex 四轮复评 P2：按码键/值键各合并一次批量失效（逐项 evictAfterCommit = 2N 个事务回调）
        java.util.Set<String> valueCacheKeys = new LinkedHashSet<>();
        java.util.Set<String> codeCacheKeys = new LinkedHashSet<>();
        for (TypeDefinition deleted : entities) {
            if (validIds.contains(deleted.getId())) {
                valueCacheKeys.add(BusinessKeys.typeValueCacheKey(deleted.getTypeKey(), deleted.getTypeCode()));
                if (deleted.getTypeValue() != null) {
                    codeCacheKeys.add(BusinessKeys.typeCodeCacheKey(deleted.getTypeKey(), deleted.getTypeValue()));
                }
            }
        }
        cacheService.evictBatchAfterCommit(PermCacheCatalog.TYPE_VALUE, tenantId, valueCacheKeys);
        cacheService.evictBatchAfterCommit(PermCacheCatalog.TYPE_CODE, tenantId, codeCacheKeys);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " type_definition row(s)");
    }

    /**
     * T-PERM-056 主体类型删除守卫：存在引用该 typeValue 的有效 abstract_user/abstract_role 行时
     * 整批拒绝（20056，对齐 resource_type 行数守卫先例；用户/角色是业务主体数据不级联，
     * 2026-09-09 用户定案）。
     *
     * @param tenantId           租户ID
     * @param entities           锁内重读后的全部待删类型行（含 isSystem 行，由 validIds 过滤）
     * @param validIds           通过 isSystem 过滤的实际待删 ID 集
     * @param typeKey            主体类型键（user_type / role_type）
     * @param rowLabel           冲突文案中的引用行称谓（用户行 / 角色行）
     * @param typesWithValidRows 批量引用行存在性查询（域服务方法引用）
     * @throws BizException 存在有效引用行时抛出（20056，message 列冲突 typeCode）
     */
    private void rejectIfSubjectTypeReferenced(Long tenantId, List<TypeDefinition> entities, Set<Long> validIds,
            String typeKey, String rowLabel, BiFunction<Long, Set<Integer>, Set<Integer>> typesWithValidRows) {
        List<TypeDefinition> deletable = entities.stream()
            .filter(e -> validIds.contains(e.getId()) && typeKey.equals(e.getTypeKey()))
            .toList();
        if (deletable.isEmpty()) {
            return;
        }
        Set<Integer> typeValues = deletable.stream()
            .map(TypeDefinition::getTypeValue)
            .collect(Collectors.toSet());
        Set<Integer> typesWithRows = typesWithValidRows.apply(tenantId, typeValues);
        List<String> conflictCodes = deletable.stream()
            .filter(t -> typesWithRows.contains(t.getTypeValue()))
            .map(TypeDefinition::getTypeCode)
            .toList();
        if (!conflictCodes.isEmpty()) {
            throw new BizException(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(),
                "类型下存在有效" + rowLabel + "，不可删除: " + String.join(", ", conflictCodes));
        }
    }

    /**
     * 将TypeDefinition实体转换为响应对象
     *
     * @param t 类型定义实体
     * @return 类型定义响应对象
     */
    private TypeDefinitionResp toTypeResp(TypeDefinition t) {
        return new TypeDefinitionResp(
            t.getId(), t.getTenantId(),
            t.getTypeKey(), t.getTypeCode(), t.getTypeValue(), t.getName(),
            t.getDescription(), t.getIsSystem(), t.getSortOrder(),
            t.getExtra(), t.getCreatedAt()
        );
    }
}
