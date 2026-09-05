package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

import cn.ac.fage.accessmesh.access.permission.dto.req.ApiMappingUpdateReq;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ApiMappingResp;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceResp;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceTreeResp;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceTreeResp.ResourceTreeNode;

import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;

import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;

import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;

import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;

import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceType;

import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;

import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;

import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;

import cn.ac.fage.accessmesh.access.permission.service.ResourceManageAppService;

import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard;

import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;

import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;

import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;

import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;

import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;


import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;

import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;

import cn.ac.fage.accessmesh.access.permission.util.TreeBuilder;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.HashSet;

import java.util.List;

import java.util.Map;

import java.util.Objects;

import java.util.Set;

import java.util.stream.Collectors;

/**
 * 资源管理服务实现类
 * <p>
 * 提供资源实体的CRUD操作、树结构查询、批量操作等功能。
 * 资源实体是权限系统中的受保护对象，如菜单、按钮、API接口等。
 * 同时提供资源与API接口的映射关系管理功能。
 * 所有操作均进行权限校验，确保操作者有相应权限。
 * </p>
 */
@Service
public class ResourceManageAppServiceImpl implements ResourceManageAppService {

    private static final Logger log = LoggerFactory.getLogger(ResourceManageAppServiceImpl.class);

    private final ResourceEntityMapper resourceEntityMapper;

    private final ResourceApiMappingMapper apiMappingMapper;

    private final ResourceEntityDomainService resourceEntityDomainService;

    private final TypeResolutionService typeResolutionService;

    private final DomainClassifyService domainClassifyService;

    private final PermQueryEngine engine;

    private final RoleResourcePermissionMapper rolePermMapper;
    private final ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    private final TreeWriteLockSupport treeWriteLockSupport;

    /**
     * 构造函数注入依赖
     *
     * @param resourceEntityMapper        资源实体数据访问层
     * @param apiMappingMapper            资源API映射数据访问层
     * @param resourceEntityDomainService 资源实体领域服务
     * @param typeResolutionService       类型解析服务
     * @param domainClassifyService       域分类服务
     * @param engine                      权限查询引擎
     * @param rolePermMapper              角色资源权限数据访问层
     * @param resourceTypeOwnershipGuard  资源类型所有权守卫（SYNC 类型管理面只读，T-PERM-052）
     */
    public ResourceManageAppServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                     ResourceApiMappingMapper apiMappingMapper,
                                     ResourceEntityDomainService resourceEntityDomainService,
                                     TypeResolutionService typeResolutionService,
                                     DomainClassifyService domainClassifyService,
                                     PermQueryEngine engine,
                                     RoleResourcePermissionMapper rolePermMapper,
                                     ResourceTypeOwnershipGuard resourceTypeOwnershipGuard,
                                     TreeWriteLockSupport treeWriteLockSupport) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.engine = engine;
        this.rolePermMapper = rolePermMapper;
        this.resourceTypeOwnershipGuard = resourceTypeOwnershipGuard;
        this.treeWriteLockSupport = treeWriteLockSupport;
    }

    /**
     * 创建资源实体
     * <p>
     * 创建新的资源实体。资源实体是权限系统中的受保护对象。
     * 支持层级结构，可指定父资源。需要RESOURCE_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含资源编码、名称、类型等
     * @param operatorId 操作者ID，可选
     * @return 创建的资源响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_ENTITY_CREATE", targetType = "resource_entity", targetId = "#result.id()", summary = "'create resource ' + #req.code()")
    public ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无创建资源的权限");
        }
        // codex 二轮复评 P1-2：管理面创建与声明变更/删除（type-definition 侧同持本锁）互斥，
        // 锁先于所有权门禁与首次实体读取——堵「门禁读 MANAGED→并发翻转 SYNC/删类型（锁内查
        // 零行放行）→插入落库」，手工行写入 SYNC 类型/已删类型
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        // T-PERM-052：SYNC 类型管理面只读（20055）——事实链路四类型（USER/ORG/MENU/ROLE）种子声明
        // SYNC+access-service，原类型保留清单已收编进本门禁（2026-09-05 内部来源统一）。
        // codex 三轮复评 P1-2（写路径权威化）：门禁为库内直查，返回类型权威行——typeValue 直接
        // 消费该结果、类型不存在当场 fail-closed，不再经 TYPE_VALUE 类型缓存（删除类型无失效时
        // 陈旧缓存会产出引用已删类型值的孤儿行）
        TypeDefinition ownedType = resourceTypeOwnershipGuard.rejectIfSyncManagedType(tenantId, req.resourceTypeCode());
        if (ownedType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "未知的resourceTypeCode: " + req.resourceTypeCode());
        }

        Long parentId = resolveParentId(tenantId, req);

        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(parentId);
        entity.setResourceType(ownedType.getTypeValue());
        entity.setCode(req.code());
        entity.setCodeType(normalizedCodeType(req.codeType()));
        entity.setName(req.name());
        entity.setPath(req.path());
        entity.setStatus(req.status() != null ? req.status() : 1);
        entity.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        entity.setExtra(req.extra());
        // maintain_source NOT NULL（DDL DEFAULT 'MANUAL' 不生效——flex insert 显式带列），
        // 与其余全部本地写入方同款（bootstrap/投影/sync 各自设值）
        entity.setMaintainSource(PermConstants.MaintainSource.MANUAL);
        entity.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleteFlag(0L);
        resourceEntityMapper.insert(entity);
        return toResourceResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_ENTITY_BATCH_CREATE", targetType = "resource_entity", targetId = "", summary = "'batch create resources, created=' + #result.size()")
    public List<ResourceResp> batchCreateResources(Long tenantId, List<ResourceCreateReq> reqs, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无批量创建资源的权限");
        }

        if (reqs == null || reqs.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return List.of();
        }
        // codex 二轮复评 P1-2：同 createResource——批量创建与声明变更/删除互斥，锁先于批量门禁
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        // T-PERM-052：SYNC 类型管理面只读（含事实链路四类型；类型码去重后一次批量判定，20055）。
        // codex 三轮复评 P1-2：批量门禁同样返回码→类型权威行——typeValue 直接消费（不经
        // TYPE_VALUE 类型缓存），类型码缺失走既有逐项错误路径
        Map<String, TypeDefinition> ownedTypeMap = resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(
            tenantId,
            reqs.stream()
                .map(ResourceCreateReq::resourceTypeCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet()));

        Set<Long> allParentIds = reqs.stream()
            .filter(req -> !hasParentBusinessKey(req))
            .map(req -> normalizeParentId(req.parentId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<String> allCodes = reqs.stream()
            .map(ResourceCreateReq::code)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());

        Map<Long, ResourceEntity> parentMap = resourceEntityDomainService.batchSelectByIdsMap(tenantId, allParentIds);
        Set<String> existingCodes = resourceEntityDomainService.findExistingCodes(tenantId, allCodes);
        List<ResourceResolveRequest> parentResolveRequests = reqs.stream()
            .filter(this::hasParentBusinessKey)
            .map(this::toParentResolveRequest)
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> parentIdByKey = typeResolutionService.batchResolveResourceIds(tenantId, parentResolveRequests);

        LocalDateTime now = LocalDateTime.now();
        List<ResourceEntity> toInsert = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < reqs.size(); i++) {
            ResourceCreateReq req = reqs.get(i);

            Long parentId;
            if (hasParentBusinessKey(req)) {
                ResourceResolveRequest parentReq = toParentResolveRequest(req);
                parentId = parentIdByKey.get(parentReq.toKey());
                if (parentId == null) {
                    errors.add("req[" + i + "]: parent resource not found: " + parentKeyText(parentReq));
                    continue;
                }
            } else {
                parentId = normalizeParentId(req.parentId());
            }

            if (!hasParentBusinessKey(req) && req.parentId() != null && req.parentId() > 0 && !parentMap.containsKey(req.parentId())) {
                errors.add("req[" + i + "]: 父资源不存在: " + req.parentId());
                continue;
            }

            if (req.code() != null && !req.code().isBlank() && existingCodes.contains(req.code())) {
                errors.add("req[" + i + "]: 编码已存在: " + req.code());
                continue;
            }

            TypeDefinition ownedType = ownedTypeMap.get(req.resourceTypeCode());
            Integer resourceType = ownedType != null ? ownedType.getTypeValue() : null;
            if (resourceType == null) {
                errors.add("req[" + i + "]: 未知的resourceTypeCode: " + req.resourceTypeCode());
                continue;
            }

            ResourceEntity entity = new ResourceEntity();
            entity.setTenantId(tenantId);
            entity.setParentId(parentId);
            entity.setResourceType(resourceType);
            entity.setCode(req.code());
            entity.setCodeType(normalizedCodeType(req.codeType()));
            entity.setName(req.name());
            entity.setPath(req.path());
            entity.setStatus(req.status() != null ? req.status() : 1);
            entity.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
            entity.setExtra(req.extra());
            entity.setMaintainSource(PermConstants.MaintainSource.MANUAL);
            entity.setCreatedBy(operatorId);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setDeleteFlag(0L);
            toInsert.add(entity);
        }

        if (!errors.isEmpty()) {
            log.warn("批量创建资源验证错误: {}", errors);
        }

        if (!toInsert.isEmpty()) {
            resourceEntityMapper.insertBatch(toInsert);
        } else {
            OperationLogRuntimeContext.markSkip();
        }

        return toInsert.stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    @Override
    public ResourceResp getResource(Long tenantId, ResourceKeyReq key) {
        // T-PERM-028：详情读门禁（类型级 RESOURCE:VIEW，对齐 tree 门禁先例）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on RESOURCE");
        }
        return toResourceResp(selectResourceByBusinessKey(tenantId, key));
    }

    /**
     * codeType 归一：null/空白 → default，去首尾空白（与 ResourceKeyReq.normalizedCodeType 同款；
     * create 侧同样归一，否则带空白 codeType 的行创建后无法经业务键寻址——双轨评审 P2）
     */
    private static String normalizedCodeType(String codeType) {
        return codeType == null || codeType.isBlank() ? ResourceKeyReq.CODE_TYPE_DEFAULT : codeType.trim();
    }

    /**
     * 以业务键 (resourceTypeCode, code, codeType) 定位有效资源实体
     *
     * @param tenantId 租户ID
     * @param key      资源业务键
     * @return 资源实体
     * @throws BizException 资源类型不存在（20021）或资源不存在（20004）时抛出
     */
    private ResourceEntity selectResourceByBusinessKey(Long tenantId, ResourceKeyReq key) {
        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", key.resourceTypeCode());
        if (resourceType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "Unknown resourceTypeCode: " + key.resourceTypeCode());
        }
        ResourceEntity entity = resourceEntityMapper.selectByTypeCodeAndCodeType(tenantId, resourceType, key.code(), key.normalizedCodeType());
        if (entity == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "Resource not found: " + key.resourceTypeCode() + ":" + key.code() + "/" + key.normalizedCodeType());
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_ENTITY_UPDATE", targetType = "resource_entity", targetId = "#req.code()", summary = "'update resource ' + #req.resourceTypeCode() + ':' + #req.code()")
    public ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 树写锁无条件持有（与 moveResource 同把）：普通字段更新全列回写锁前读到的 parent
        //（update(entity) 非 null 列全写），无锁时并发移动会被静默回滚、经两步合法移动+回写
        // 可闭合成环——锁内读写串行后回写旧值不可能覆盖并发变更
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);

        ResourceEntity entity = selectResourceByBusinessKey(tenantId,
            new ResourceKeyReq(req.resourceTypeCode(), req.code(), req.codeType()));
        // T-PERM-052：SYNC 类型管理面只读（原行级 owner=access-service 投影防线已收编，20055）
        resourceTypeOwnershipGuard.rejectIfSyncManagedType(tenantId, req.resourceTypeCode());

        // T-PERM-042：资源实体管理链路按 resource_entity.id 门禁（entityId 轨，§12.3 边界）
        if (!engine.hasPermissionByEntityId(tenantId, operatorId, ResourceTypeCode.RESOURCE, entity.getId(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on RESOURCE:" + entity.getId());
        }

        if (req.name() != null) entity.setName(req.name());
        if (req.path() != null) entity.setPath(req.path());
        if (req.status() != null) entity.setStatus(req.status());
        if (req.sortOrder() != null) entity.setSortOrder(req.sortOrder());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(operatorId);
        // T-PERM-028：extraClear 显式清空（JSON null 无法区分「未传」与「清空」），优先于 extra。
        // extra 置 null 须强制写列：BaseMapper.update(entity) 默认忽略 null 字段，
        // UpdateEntity 代理记录 set 调用（含 null 入参）为显式更新列（mybatis-flex 标准方式）
        if (Boolean.TRUE.equals(req.extraClear())) {
            entity.setExtra(null);
            ResourceEntity patch = com.mybatisflex.core.util.UpdateEntity.of(ResourceEntity.class);
            patch.setId(entity.getId());
            patch.setName(entity.getName());
            patch.setPath(entity.getPath());
            patch.setStatus(entity.getStatus());
            patch.setSortOrder(entity.getSortOrder());
            patch.setExtra(null);
            patch.setUpdatedAt(entity.getUpdatedAt());
            patch.setUpdatedBy(entity.getUpdatedBy());
            resourceEntityMapper.update(patch);
        } else {
            if (req.extra() != null) entity.setExtra(req.extra());
            resourceEntityMapper.update(entity);
        }
        return toResourceResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_ENTITY_MOVE", targetType = "resource_entity", targetId = "#req.resource.code()", summary = "'move resource ' + #req.resource.resourceTypeCode() + ':' + #req.resource.code()")
    public void moveResource(Long tenantId, ResourceMoveReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // T-PERM-044：树写锁先于跨类型/防环校验（check-then-act 窗口收口，同 moveRole）
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);

        ResourceEntity entity = selectResourceByBusinessKey(tenantId, req.resource());
        // T-PERM-052：SYNC 类型管理面只读（树位置归声明来源维护，20055）
        resourceTypeOwnershipGuard.rejectIfSyncManagedType(tenantId, req.resource().resourceTypeCode());

        // T-PERM-042：资源实体管理链路按 resource_entity.id 门禁（entityId 轨，§12.3 边界）
        if (!engine.hasPermissionByEntityId(tenantId, operatorId, ResourceTypeCode.RESOURCE, entity.getId(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on RESOURCE:" + entity.getId());
        }

        Long newParentId = null;
        if (req.parent() != null) {
            ResourceEntity parent = selectResourceByBusinessKey(tenantId, req.parent());
            // T-PERM-028：跨类型拦截 + 防环（目标父不得是自身或其子孙；原内部 id 实现缺失两项校验，
            // 对齐前端设计 §4 与 mock 语义——环节点从树构建静默消失，先例 ROLE_PARENT_INVALID）
            if (!parent.getResourceType().equals(entity.getResourceType())) {
                throw new BizException(PermissionErrorCode.RESOURCE_PARENT_INVALID.getCode(),
                    "不可跨资源类型移动: " + req.parent().resourceTypeCode() + " -> " + req.resource().resourceTypeCode());
            }
            if (parent.getId().equals(entity.getId())
                || resourceEntityDomainService.batchGetDescendantIds(tenantId, Set.of(entity.getId()))
                    .getOrDefault(entity.getId(), List.of()).contains(parent.getId())) {
                throw new BizException(PermissionErrorCode.RESOURCE_PARENT_INVALID.getCode(),
                    "目标父资源不能是自身或其子孙节点: " + req.parent().code());
            }
            newParentId = parent.getId();
        }
        entity.setParentId(newParentId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        // parentId 置 null（移到顶层）须强制写列：UpdateEntity 显式更新列（同 extraClear）
        if (newParentId == null) {
            ResourceEntity patch = com.mybatisflex.core.util.UpdateEntity.of(ResourceEntity.class);
            patch.setId(entity.getId());
            patch.setParentId(null);
            patch.setUpdatedAt(entity.getUpdatedAt());
            patch.setUpdatedBy(entity.getUpdatedBy());
            resourceEntityMapper.update(patch);
        } else {
            resourceEntityMapper.update(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "RESOURCE_ENTITY_REMOVE", targetType = "resource_entity", targetId = "", summary = "'batch remove resources'")
    public void deleteResources(Long tenantId, List<ResourceKeyReq> keys, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (keys == null || keys.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // T-PERM-052 评审批次（2026-09-05）：remove 与 update/move/sync 同持树写锁——后代展开与
        // 批量软删之间的并发 sync 插入会产生「父已删、子存活」的悬挂引用（存量缺口顺手收口）
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);

        // T-PERM-028：业务键批量解析为实体（按 resourceTypeCode 分组批量查询，避免N+1；
        // 未命中的键静默跳过，对齐原 ids 批删语义）
        List<ResourceEntity> entities = resolveResourcesByKeys(tenantId, keys);
        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }
        Set<Long> existingResourceIds = entities.stream()
            .map(ResourceEntity::getId)
            .collect(Collectors.toSet());

        Set<Long> deniedIds = engine.getDeniedEntityIds(tenantId, operatorId, ResourceTypeCode.RESOURCE, existingResourceIds, OperationCodeConstants.MANAGE);

        Set<Long> permittedIds = existingResourceIds.stream()
            .filter(id -> !deniedIds.contains(id))
            .collect(Collectors.toSet());

        if (permittedIds.isEmpty()) {
            log.info("操作者{}无权删除任何请求的资源: denied={}", operatorId, deniedIds.size());
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Map<Long, List<Long>> descendantsMap = resourceEntityDomainService.batchGetDescendantIds(tenantId, permittedIds);

        Set<Long> allIdsToDelete = new HashSet<>(permittedIds);
        for (List<Long> descendants : descendantsMap.values()) {
            allIdsToDelete.addAll(descendants);
        }

        // T-PERM-052：SYNC 类型级联守卫——对删除全集（含展开的后代，非仅请求根集合）判定：
        // sync 通道允许跨类型父子边，MANAGED 根的子树可能含 SYNC 类型后代，只判根集合会连带
        // 清掉外部来源维护的子树（一次批量取实体收集类型值，20055）
        Set<Integer> allTypeValues = resourceEntityDomainService
            .batchSelectByIdsMap(tenantId, allIdsToDelete).values().stream()
            .map(ResourceEntity::getResourceType)
            .collect(Collectors.toSet());
        resourceTypeOwnershipGuard.rejectIfAnySyncManagedByValues(tenantId, allTypeValues);

        // T-PERM-018 (C9)：软删 perm 前登记受影响范围。资源软删会级联软删 role_resource_permission，
        // ROLE_PERM_SNAPSHOT 缓存含旧 perm → 软删前查受影响 roleIds + serviceCodes 双重登记，afterCommit AOP 失效与广播
        List<Long> resourceIdsToDelete = new ArrayList<>(allIdsToDelete);
        Set<Long> affectedRoleIds = rolePermMapper.selectRoleIdsByResourceIds(tenantId, resourceIdsToDelete);
        if (!affectedRoleIds.isEmpty()) {
            PermissionChangeContext.markRoles(tenantId, affectedRoleIds);
        }
        // 资源 → API mapping → serviceCode：删除资源影响 Gateway 本地快照构建，软删前查出并 markServiceCodes
        Set<String> affectedServiceCodes = apiMappingMapper.selectByResourceEntityIds(tenantId, allIdsToDelete).stream()
            .map(ResourceApiMapping::getServiceCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        if (!affectedServiceCodes.isEmpty()) {
            PermissionChangeContext.markServiceCodes(tenantId, affectedServiceCodes);
        }

        List<Long> permIds = rolePermMapper.selectValidPermIdsByResourceIds(tenantId, resourceIdsToDelete);

        LocalDateTime now = LocalDateTime.now();
        resourceEntityDomainService.softDeleteBatch(tenantId, resourceIdsToDelete, now);

        if (!permIds.isEmpty()) {
            rolePermMapper.softDeleteBatch(tenantId, permIds, now);
        }

        OperationLogRuntimeContext.setSummary(
            "soft-deleted " + allIdsToDelete.size() + " resource(s), rootPermitted="
                + permittedIds.size() + ", denied=" + deniedIds.size()
        );
    }

    /**
     * 批量解析业务键为有效资源实体（固定两次批量查询，T-PERM-028 复评 P2 收口）
     * <p>
     * 一次 batchResolveTypeValues 解析全部类型 + 一次跨类型 selectByTypesAndCodesAndCodeTypes
     * 查询（笛卡尔命中超集），再按 (resourceType, code, codeType) 三元组内存精确过滤——
     * 查询次数不随请求内资源类型数增长（循环内禁止单条数据库查询，project-rules §8.4.8）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keys     资源业务键列表
     * @return 命中的有效实体列表（未命中的键静默跳过，对齐原 ids 批删语义）
     */
    private List<ResourceEntity> resolveResourcesByKeys(Long tenantId, List<ResourceKeyReq> keys) {
        // resourceTypeCode → (code → codeType 集合)
        Map<String, Map<String, Set<String>>> grouped = new HashMap<>();
        for (ResourceKeyReq key : keys) {
            if (key == null || key.resourceTypeCode() == null || key.resourceTypeCode().isBlank()
                || key.code() == null || key.code().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(key.resourceTypeCode(), k -> new HashMap<>())
                .computeIfAbsent(key.code(), k -> new HashSet<>())
                .add(key.normalizedCodeType());
        }
        if (grouped.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> typeValues = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", grouped.keySet());
        Set<Integer> resolvedTypes = typeValues.values().stream()
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (resolvedTypes.isEmpty()) {
            return List.of();
        }

        // 请求三元组集合（typeValue:code:codeType），未知类型码的键静默跳过
        Set<String> triples = new HashSet<>();
        for (Map.Entry<String, Integer> entry : typeValues.entrySet()) {
            Map<String, Set<String>> codes = grouped.getOrDefault(entry.getKey(), Map.of());
            for (Map.Entry<String, Set<String>> codeEntry : codes.entrySet()) {
                for (String codeType : codeEntry.getValue()) {
                    triples.add(entry.getValue() + ":" + codeEntry.getKey() + ":" + codeType);
                }
            }
        }

        Set<String> allCodes = grouped.values().stream()
            .flatMap(m -> m.keySet().stream()).collect(Collectors.toSet());
        Set<String> allCodeTypes = grouped.values().stream()
            .flatMap(m -> m.values().stream().flatMap(Set::stream)).collect(Collectors.toSet());

        List<ResourceEntity> entities = new ArrayList<>();
        for (ResourceEntity entity : resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(tenantId, resolvedTypes, allCodes, allCodeTypes)) {
            if (triples.contains(entity.getResourceType() + ":" + entity.getCode() + ":" + entity.getCodeType())) {
                entities.add(entity);
            }
        }
        return entities;
    }

    @Override
    public List<ResourceTreeResp> getResourceTree(Long tenantId, String resourceTypeCode, String domainCode) {
        // T-PERM-042：授权页资源树读门禁（architecture §14.5 终态，类型级 RESOURCE:VIEW）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on RESOURCE");
        }
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.RESOURCE);
        }

        List<ResourceEntity> allEntities = resourceEntityMapper.selectResourceTree(tenantId, resourceType, matchNone);

        TreeBuilder<ResourceEntity, ResourceTreeNode> treeBuilder = new TreeBuilder<>(
            ResourceEntity::getId,
            ResourceEntity::getParentId,
            (entity, children) -> new ResourceTreeNode(
                entity.getId(), entity.getParentId(),
                typeResolutionService.resolveTypeCode(entity.getTenantId(), "resource_type", entity.getResourceType()),
                entity.getCode(), entity.getCodeType(), entity.getName(),
                entity.getPath(), entity.getStatus(), entity.getSortOrder(), children
            )
        );

        List<ResourceEntity> roots = allEntities.stream()
            .filter(r -> r.getParentId() == null)
            .collect(Collectors.toList());

        return treeBuilder.buildTrees(roots, allEntities).stream()
            .map(ResourceTreeResp::new)
            .collect(Collectors.toList());
    }

    @Override
    public List<ResourceResp> listResources(Long tenantId, String resourceTypeCode, String domainCode, int offset, int limit) {
        // T-PERM-028：列表读门禁（类型级 RESOURCE:VIEW，对齐 tree 门禁先例）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on RESOURCE");
        }
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.RESOURCE);
        }

        return resourceEntityMapper.selectResourceListPaged(tenantId, resourceType, matchNone, offset, limit)
            .stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    @Override
    public long countResources(Long tenantId, String resourceTypeCode, String domainCode) {
        // T-PERM-028：列表读门禁（类型级 RESOURCE:VIEW，与 listResources 同口径；list 端点先调本方法）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on RESOURCE");
        }
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.RESOURCE);
        }

        return resourceEntityMapper.selectResourceListCount(tenantId, resourceType, matchNone);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "RESOURCE_API_MAPPING_ADD", targetType = "resource_api_mapping", targetId = "#result.id()", summary = "'add api mapping for service ' + #req.serviceCode()")
    public ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationCodeConstants.MANAGE_API_MAPPING)) {
            throw new SecurityException("Permission denied: MANAGE_API_MAPPING on SERVICE:" + req.serviceCode());
        }

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.resourceId());
        if (entity == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在: " + req.resourceId());
        }

        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setTenantId(tenantId);
        mapping.setResourceEntityId(req.resourceId());
        mapping.setServiceCode(req.serviceCode());
        mapping.setHttpMethod(req.httpMethod());
        mapping.setPathPattern(req.pathPattern());
        // 契约可选字段缺省 0（与 DDL match_order DEFAULT 0 对齐；显式 null 会绕过列默认直写违例）
        mapping.setMatchOrder(req.matchOrder() != null ? req.matchOrder() : 0);
        mapping.setEnabled(req.enabled() != null ? req.enabled() : true);
        mapping.setExtra(req.extra());
        LocalDateTime now = LocalDateTime.now();
        mapping.setCreatedAt(now);
        mapping.setUpdatedAt(now);
        mapping.setDeleteFlag(0L);
        apiMappingMapper.insert(mapping);
        // API mapping 变更不影响 ROLE_PERM_SNAPSHOT（perm 记录未变），仅影响 Gateway 本地快照 → 广播 serviceCodes
        PermissionChangeContext.markServiceCodes(tenantId, req.serviceCode());
        return toApiMappingResp(mapping, entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "RESOURCE_API_MAPPING_REMOVE", targetType = "resource_api_mapping", targetId = "", summary = "'batch remove resource api mappings'")
    public void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (mappingIds == null || mappingIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validMappingIds = mappingIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (validMappingIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<ResourceApiMapping> mappings = apiMappingMapper.selectValidByIds(tenantId, validMappingIds);
        if (mappings.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<String> serviceCodes = mappings.stream()
            .map(ResourceApiMapping::getServiceCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());

        // T-PERM-042：SERVICE 业务码门禁（serviceCode 即 resource_entity(SERVICE).code）。
        // 引擎纯查询，拒绝时由调用方显式抛出
        Set<String> deniedServiceCodes = engine.getDeniedResourceCodes(
            tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCodes, OperationCodeConstants.MANAGE_API_MAPPING);
        if (!deniedServiceCodes.isEmpty()) {
            throw new SecurityException("Permission denied: MANAGE_API_MAPPING on SERVICE:" + deniedServiceCodes);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> mappingIdsToDelete = mappings.stream()
            .map(ResourceApiMapping::getId)
            .collect(Collectors.toList());
        int n = apiMappingMapper.softDeleteBatch(tenantId, mappingIdsToDelete, now);

        if (n <= 0) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // API mapping 删除影响 Gateway 本地快照构建 → 广播受影响 serviceCodes（perm 未变，不 markRoles）
        PermissionChangeContext.markServiceCodes(tenantId, serviceCodes);

        OperationLogRuntimeContext.setSummary(
            "soft-deleted " + n + " resource_api_mapping row(s), ids=" + mappingIdsToDelete
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId, String serviceCode) {
        Long operatorId = OperatorContext.getOperatorId();

        // T-PERM-027（§7.5）：映射列表补 SERVICE:VIEW 门禁——指定 serviceCode 按实例校验，
        // 未指定（管理全量列表）按类型级校验并对结果做服务维裁剪，避免暴露跨服务 API 路径。
        // 空白 serviceCode 与 null 同义（与 selectValidList 的过滤判定对齐，避免门禁与 SQL 语义分叉）
        String normalizedServiceCode = serviceCode == null || serviceCode.isBlank() ? null : serviceCode;
        boolean filteredByService = normalizedServiceCode != null;
        if (filteredByService) {
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, normalizedServiceCode, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on SERVICE:" + normalizedServiceCode);
            }
        } else if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE");
        }

        List<ResourceApiMapping> mappings = new ArrayList<>(apiMappingMapper.selectValidList(tenantId, resourceId, normalizedServiceCode));

        if (!filteredByService && !mappings.isEmpty()) {
            Set<String> mappingServiceCodes = mappings.stream()
                .map(ResourceApiMapping::getServiceCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
            if (!mappingServiceCodes.isEmpty()) {
                Set<String> denied = engine.getDeniedResourceCodes(
                    tenantId, operatorId, ResourceTypeCode.SERVICE, mappingServiceCodes, OperationCodeConstants.VIEW);
                if (!denied.isEmpty()) {
                    mappings = mappings.stream()
                        .filter(mapping -> !denied.contains(mapping.getServiceCode()))
                        .collect(Collectors.toList());
                }
            }
        }

        return toEnrichedApiMappingResps(tenantId, mappings);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "RESOURCE_API_MAPPING_UPDATE", targetType = "resource_api_mapping", targetId = "#req.mappingId()", summary = "'update api mapping ' + #req.mappingId()")
    public ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        ResourceApiMapping mapping = apiMappingMapper.selectValidById(req.mappingId(), tenantId);
        if (mapping == null || !Objects.equals(mapping.getResourceEntityId(), req.resourceId())) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "API映射不存在: " + req.mappingId());
        }

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, mapping.getServiceCode(), OperationCodeConstants.MANAGE_API_MAPPING)) {
            throw new SecurityException("Permission denied: MANAGE_API_MAPPING on SERVICE:" + mapping.getServiceCode());
        }

        if (req.httpMethod() != null) {
            mapping.setHttpMethod(req.httpMethod());
        }
        if (req.pathPattern() != null) {
            mapping.setPathPattern(req.pathPattern());
        }
        if (req.matchOrder() != null) {
            mapping.setMatchOrder(req.matchOrder());
        }
        if (req.enabled() != null) {
            mapping.setEnabled(req.enabled());
        }
        if (req.extra() != null) {
            mapping.setExtra(req.extra());
        }
        mapping.setUpdatedAt(LocalDateTime.now());
        apiMappingMapper.update(mapping);

        // API mapping 变更影响 Gateway 本地快照 → 广播 serviceCode（perm 未变，不 markRoles）
        PermissionChangeContext.markServiceCodes(tenantId, mapping.getServiceCode());

        ResourceApiMapping updated = apiMappingMapper.selectValidById(req.mappingId(), tenantId);
        ResourceEntity updatedResource = resourceEntityDomainService.selectValidById(tenantId, updated.getResourceEntityId());
        return toApiMappingResp(updated, updatedResource);
    }

    private Long resolveParentId(Long tenantId, ResourceCreateReq req) {
        if (!hasParentBusinessKey(req)) {
            return normalizeParentId(req.parentId());
        }
        ResourceResolveRequest parentReq = toParentResolveRequest(req);
        Long parentId = typeResolutionService.resolveResourceId(
            tenantId,
            parentReq.resourceTypeCode(),
            parentReq.resourceCode(),
            parentReq.codeType(),
            parentReq.domainCode()
        );
        if (parentId == null) {
            throw new BizException(
                PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "parent resource not found: " + parentKeyText(parentReq)
            );
        }
        return parentId;
    }

    private boolean hasParentBusinessKey(ResourceCreateReq req) {
        return req != null
            && req.parentResourceTypeCode() != null && !req.parentResourceTypeCode().isBlank()
            && req.parentResourceCode() != null && !req.parentResourceCode().isBlank();
    }

    private ResourceResolveRequest toParentResolveRequest(ResourceCreateReq req) {
        return new ResourceResolveRequest(
            req.parentResourceTypeCode(),
            req.parentResourceCode(),
            req.parentCodeType(),
            req.parentDomainCode()
        );
    }

    private Long normalizeParentId(Long parentId) {
        return parentId != null && parentId > 0 ? parentId : null;
    }

    private String parentKeyText(ResourceResolveRequest req) {
        return req.resourceTypeCode() + ":" + req.resourceCode();
    }

    private ResourceResp toResourceResp(ResourceEntity entity) {
        String resourceTypeName = ResourceType.safeGetLabel(entity.getResourceType());
        return new ResourceResp(
            entity.getId(), entity.getTenantId(),
            entity.getParentId(), typeResolutionService.resolveTypeCode(entity.getTenantId(), "resource_type", entity.getResourceType()), resourceTypeName,
            entity.getCode(), entity.getCodeType(), entity.getName(),
            entity.getPath(), entity.getStatus(), entity.getSortOrder(),
            entity.getExtra(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    /**
     * 映射实体转响应，冗余关联资源的业务展示字段（T-PERM-027 §7.3）
     *
     * @param mapping  映射实体
     * @param resource 关联资源实体，可为 null（资源已软删时字段置 null）
     */
    private ApiMappingResp toApiMappingResp(ResourceApiMapping mapping, ResourceEntity resource) {
        return new ApiMappingResp(
            mapping.getId(), mapping.getTenantId(),
            mapping.getResourceEntityId(), mapping.getServiceCode(),
            mapping.getHttpMethod(), mapping.getPathPattern(),
            mapping.getMatchOrder(), mapping.getEnabled(),
            mapping.getExtra(), mapping.getCreatedAt(), mapping.getUpdatedAt(),
            resource != null ? resource.getCode() : null,
            resource != null ? resource.getName() : null,
            resource != null && resource.getResourceType() != null
                ? typeResolutionService.resolveTypeCode(mapping.getTenantId(), "resource_type", resource.getResourceType())
                : null,
            resource != null ? resource.getMaintainSource() : null
        );
    }

    /**
     * 批量转换并补全资源业务字段（一次批量查询避免 N+1）
     */
    private List<ApiMappingResp> toEnrichedApiMappingResps(Long tenantId, List<ResourceApiMapping> mappings) {
        Set<Long> resourceIds = mappings.stream()
            .map(ResourceApiMapping::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = resourceIds.isEmpty() ? Map.of()
            : resourceEntityMapper.selectValidByIds(tenantId, resourceIds).stream()
                .collect(Collectors.toMap(ResourceEntity::getId, r -> r));
        return mappings.stream()
            .map(mapping -> toApiMappingResp(mapping, resourceMap.get(mapping.getResourceEntityId())))
            .collect(Collectors.toList());
    }
}
