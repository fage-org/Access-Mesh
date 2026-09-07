package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final TreeWriteLockSupport treeWriteLockSupport;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param typeDefinitionMapper      类型定义数据访问层
     * @param operationPermissionMapper 操作权限数据访问层（resource_type 联动预置写入，T-PERM-028）
     * @param engine                    权限查询引擎
     * @param resourceEntityDomainService 资源实体域服务（行数守卫查询）
     * @param resourceTypeOwnershipGuard 资源类型所有权守卫（extra.managedMode 声明校验与变更守卫，T-PERM-052）
     * @param cacheService              统一缓存入口（类型解析缓存提交后失效，codex 三轮复评 P1-2）
     */
    public TypeDefinitionAppServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                         OperationPermissionMapper operationPermissionMapper,
                                         PermQueryEngine engine,
                                         ResourceTypeOwnershipGuard resourceTypeOwnershipGuard,
                                         ResourceEntityDomainService resourceEntityDomainService,
                                         TreeWriteLockSupport treeWriteLockSupport,
                                         CacheService cacheService) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.engine = engine;
        this.resourceTypeOwnershipGuard = resourceTypeOwnershipGuard;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.treeWriteLockSupport = treeWriteLockSupport;
        this.cacheService = cacheService;
    }

    /**
     * 创建类型定义
     * <p>
     * typeValue 由服务端在 tenant+typeKey 内自动分配（全量行含软删行 max+1，软删不复用）；
     * typeCode 留空时按 {@code TYPEKEY_<typeValue>} 生成，显式提供时校验 tenant+typeKey 内唯一；
     * isSystem 固定 false——系统预置类型仅走租户初始化种子，不可由 API 创建。
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
            typeCode = req.typeKey().toUpperCase() + "_" + typeValue;
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
        cacheService.evictAfterCommit(PermCacheCatalog.TYPE_VALUE, tenantId, typeKey + ":" + typeCode);
        if (typeValue != null) {
            cacheService.evictAfterCommit(PermCacheCatalog.TYPE_CODE, tenantId, typeKey + ":" + typeValue);
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
     * 需要TYPE_DEFINITION_VIEW权限。
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
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, String.valueOf(typeId), OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION:" + typeId);
        }

        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, typeId);
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
     */
    private void requireTypeViewPermission(Long tenantId, Long operatorId) {
        if (engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION,
                null, OperationCodeConstants.VIEW)) {
            return;
        }
        // 全拒判定必须与引擎入参同一去重集合比较：typeCode 仅 tenant+typeKey 内唯一，
        // 跨 type_key 重码下用未去重 codes.size() 比较会令全拒恒 false（fail-open）
        Set<String> codes = new LinkedHashSet<>(typeDefinitionMapper.selectValidCodesByTenant(tenantId));
        Set<String> denied = engine.getDeniedResourceCodes(tenantId, operatorId,
                ResourceTypeCode.TYPE_DEFINITION, codes, OperationCodeConstants.VIEW);
        if (codes.isEmpty() || denied.size() >= codes.size()) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION");
        }
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
     * 需要TYPE_DEFINITION_MANAGE权限。
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

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, String.valueOf(req.typeId()), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + req.typeId());
        }

        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, req.typeId());
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
        return toTypeResp(type);
    }

    /**
     * 批量删除类型定义
     * <p>
     * 批量软删除类型定义。系统内置类型（isSystem=true）不可删除。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要TYPE_DEFINITION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        类型定义ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // T-PERM-042：引擎纯查询，拒绝时由调用方显式抛出
        Set<Long> deniedIds = engine.getDeniedEntityIds(
            tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, validInputIds, OperationCodeConstants.MANAGE);
        if (!deniedIds.isEmpty()) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + deniedIds);
        }

        List<TypeDefinition> entities = typeDefinitionMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // codex 复评 P1：含 resource_type 时与资源写入口共持树写锁并锁内重读（同 updateType）
        if (entities.stream().anyMatch(e -> "resource_type".equals(e.getTypeKey()))) {
            treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
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
        if (!deletableResourceTypes.isEmpty()) {
            Set<Integer> typesWithRows = resourceEntityDomainService.findTypesWithValidRows(
                tenantId, deletableResourceTypes.stream().map(TypeDefinition::getTypeValue).collect(Collectors.toSet()));
            List<String> conflictCodes = deletableResourceTypes.stream()
                .filter(t -> typesWithRows.contains(t.getTypeValue()))
                .map(TypeDefinition::getTypeCode)
                .toList();
            if (!conflictCodes.isEmpty()) {
                throw new BizException(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(),
                    "类型下存在有效资源行，不可删除: " + String.join(", ", conflictCodes));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        typeDefinitionMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        // codex 三轮复评 P1-2：被删类型提交后失效双向解析缓存键（同码重建新值前，旧映射不得残留）；
        // codex 四轮复评 P2：按码键/值键各合并一次批量失效（逐项 evictAfterCommit = 2N 个事务回调）
        java.util.Set<String> valueCacheKeys = new LinkedHashSet<>();
        java.util.Set<String> codeCacheKeys = new LinkedHashSet<>();
        for (TypeDefinition deleted : entities) {
            if (validIds.contains(deleted.getId())) {
                valueCacheKeys.add(deleted.getTypeKey() + ":" + deleted.getTypeCode());
                if (deleted.getTypeValue() != null) {
                    codeCacheKeys.add(deleted.getTypeKey() + ":" + deleted.getTypeValue());
                }
            }
        }
        cacheService.evictBatchAfterCommit(PermCacheCatalog.TYPE_VALUE, tenantId, valueCacheKeys);
        cacheService.evictBatchAfterCommit(PermCacheCatalog.TYPE_CODE, tenantId, codeCacheKeys);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " type_definition row(s)");
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
