package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionRecentChangesReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionRecentChangesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RecentChangeResp;
import cn.ac.fage.accessmesh.permission.entity.OperationLog;
import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.permission.service.LogQueryAppService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日志查询服务实现类
 * <p>
 * 提供变更日志和操作日志的只读查询功能。
 * 所有查询均需要SYSTEM_CONFIG_VIEW权限。
 * 支持按实体类型、用户、角色、时间范围、事件类型等多维度过滤查询。
 * </p>
 */
@Service
public class LogQueryAppServiceImpl implements LogQueryAppService {

    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final PermQueryEngine engine;
    private final TypeResolutionService typeResolutionService;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param changeLogMapper         权限变更日志数据访问层
     * @param operationLogMapper      操作日志数据访问层
     * @param engine                  权限查询引擎
     * @param typeResolutionService   类型解析服务
     * @param objectMapper            JSON解析器
     */
    public LogQueryAppServiceImpl(PermissionChangeLogMapper changeLogMapper,
                                OperationLogMapper operationLogMapper,
                                PermQueryEngine engine,
                                TypeResolutionService typeResolutionService,
                                ObjectMapper objectMapper) {
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.engine = engine;
        this.typeResolutionService = typeResolutionService;
        this.objectMapper = objectMapper;
    }

    // ===== 变更日志查询 =====

    /**
     * 查询变更日志列表
     * <p>
     * 查询指定实体类型的变更日志。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型
     * @param entityId   实体ID
     * @param offset     分页偏移量
     * @param limit      分页大小
     * @return 变更日志响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.selectByTenantEntityTypeEntityId(tenantId, entityType, entityId, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    /**
     * 统计变更日志数量
     * <p>
     * 统计指定实体类型的变更日志总数。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型
     * @param entityId   实体ID
     * @return 变更日志总数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public long countChangeLogs(Long tenantId, String entityType, Long entityId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.countByTenantEntityTypeEntityId(tenantId, entityType, entityId);
    }

    /**
     * 查询变更日志列表（支持多条件过滤）
     * <p>
     * 支持按用户、角色、时间范围、事件类型等多维度过滤查询。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param userId     用户ID，可选过滤条件
     * @param roleId     角色ID，可选过滤条件
     * @param since      开始时间，可选
     * @param until      结束时间，可选
     * @param eventTypes 事件类型列表，可选过滤条件
     * @param offset     分页偏移量
     * @param limit      分页大小
     * @return 变更日志响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                                       LocalDateTime since, LocalDateTime until,
                                                       List<String> eventTypes, int offset, int limit) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.selectFiltered(tenantId, userId, roleId, since, until, eventTypes, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    /**
     * 统计变更日志数量（支持多条件过滤）
     * <p>
     * 统计符合条件的变更日志总数。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param userId     用户ID，可选过滤条件
     * @param roleId     角色ID，可选过滤条件
     * @param since      开始时间，可选
     * @param until      结束时间，可选
     * @param eventTypes 事件类型列表，可选过滤条件
     * @return 变更日志总数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                         LocalDateTime since, LocalDateTime until,
                                         List<String> eventTypes) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.countFiltered(tenantId, userId, roleId, since, until, eventTypes);
    }

    // ===== 最近变更查询 =====

    /**
     * 查询最近变更
     * <p>
     * 查询用户或角色的最近权限变更记录。
     * 支持按目标类型（USER/ROLE）、时间范围、事件类型过滤。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      最近变更查询请求
     * @return 最近变更响应，包含变更列表和分页信息
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public PermissionRecentChangesResp getRecentChanges(Long tenantId, PermissionRecentChangesReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        Long userId = null;
        Long roleId = null;
        if (PermConstants.TargetType.USER.equalsIgnoreCase(req.targetType()) && req.subjectTypeCode() != null && req.subjectExternalId() != null) {
            userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        } else if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType()) && req.roleTypeCode() != null && req.roleExternalId() != null) {
            roleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        }
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        if (PermConstants.TargetType.USER.equalsIgnoreCase(req.targetType()) && userId == null) {
            return new PermissionRecentChangesResp(List.of(), 0, pageNum, pageSize, false);
        }
        if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType()) && roleId == null) {
            return new PermissionRecentChangesResp(List.of(), 0, pageNum, pageSize, false);
        }
        long total = countChangeLogsFiltered(
            tenantId, userId, roleId, req.since(), req.until(), req.eventTypes());
        List<ChangeLogResp> logs = listChangeLogsFiltered(
            tenantId, userId, roleId, req.since(), req.until(), req.eventTypes(), offset, pageSize);
        List<RecentChangeResp> items = logs.stream().map(this::toRecentChange).toList();
        int totalInt = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        return new PermissionRecentChangesResp(
            items, totalInt, pageNum, pageSize, offset + items.size() < total);
    }

    // ===== 操作日志查询 =====

    /**
     * 查询操作日志列表
     * <p>
     * 查询系统的操作日志，支持按模块和操作类型过滤。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param module   模块名称，可选过滤条件
     * @param action   操作类型，可选过滤条件
     * @param offset   分页偏移量
     * @param limit    分页大小
     * @return 操作日志响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return operationLogMapper.selectByTenantModuleAction(tenantId, module, action, offset, limit)
            .stream().map(this::toOperationLogResp).collect(Collectors.toList());
    }

    /**
     * 统计操作日志数量
     * <p>
     * 统计符合条件的操作日志总数。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param module   模块名称，可选过滤条件
     * @param action   操作类型，可选过滤条件
     * @return 操作日志总数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public long countOperationLogs(Long tenantId, String module, String action) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return operationLogMapper.countByTenantModuleAction(tenantId, module, action);
    }

    // ===== 实体转换方法 =====

    /**
     * 将PermissionChangeLog实体转换为响应对象
     *
     * @param c 权限变更日志实体
     * @return 变更日志响应对象
     */
    private ChangeLogResp toChangeLogResp(PermissionChangeLog c) {
        return new ChangeLogResp(
            c.getId(), c.getTenantId(), c.getEntityType(),
            c.getEntityId(), c.getOperation(), c.getOldSnapshot(), c.getNewSnapshot(),
            c.getDiffSnapshot(), c.getAffectedAbstractUserIds(), c.getAffectedAbstractRoleIds(),
            c.getChangeReason(), c.getChangeSource(), c.getRequestId(), c.getCreatedAt()
        );
    }

    /**
     * 将OperationLog实体转换为响应对象
     *
     * @param l 操作日志实体
     * @return 操作日志响应对象
     */
    private OperationLogResp toOperationLogResp(OperationLog l) {
        return new OperationLogResp(
            l.getId(), l.getTenantId(), l.getModule(), l.getAction(),
            l.getTargetType(), l.getTargetId(), l.getSummary(), l.getOperatorId(),
            l.getOperatorName(), l.getIpAddress(), l.getRequestId(), l.getCreatedAt()
        );
    }

    /**
     * 将变更日志转换为最近变更响应
     *
     * @param log 变更日志响应
     * @return 最近变更响应对象
     */
    private RecentChangeResp toRecentChange(ChangeLogResp log) {
        return new RecentChangeResp(
            log.id(),
            parseText(log.diffSnapshot(), "eventType"),
            parseText(log.diffSnapshot(), "items[0].changeType"),
            "POSSIBLE",
            parseText(log.diffSnapshot(), "items[0].message"),
            new RecentChangeResp.PermissionKey(
                parseText(log.diffSnapshot(), "items[0].permission.domainCode"),
                parseText(log.diffSnapshot(), "items[0].permission.resourceTypeCode"),
                parseText(log.diffSnapshot(), "items[0].permission.resourceCode"),
                parseText(log.diffSnapshot(), "items[0].permission.codeType"),
                parseText(log.diffSnapshot(), "items[0].permission.operationCode"),
                parseBoolean(log.diffSnapshot(), "items[0].permission.scopeAll")
            ),
            new RecentChangeResp.SourceRole(
                parseText(log.diffSnapshot(), "items[0].role.roleTypeCode"),
                parseText(log.diffSnapshot(), "items[0].role.roleExternalId"),
                parseText(log.diffSnapshot(), "items[0].role.roleName")
            ),
            null,
            null,
            log.changeReason(),
            log.createdAt()
        );
    }

    /**
     * 从JSON中解析文本值
     *
     * @param json JSON字符串
     * @param path JSON路径表达式
     * @return 解析的文本值，不存在返回null
     */
    private String parseText(String json, String path) {
        JsonNode node = parsePath(json, path);
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 从JSON中解析布尔值
     *
     * @param json JSON字符串
     * @param path JSON路径表达式
     * @return 解析的布尔值，不存在返回null
     */
    private Boolean parseBoolean(String json, String path) {
        JsonNode node = parsePath(json, path);
        return node == null || node.isNull() ? null : node.asBoolean();
    }

    /**
     * 按路径解析JSON节点
     * <p>
     * 支持嵌套路径和数组索引访问，如 items[0].changeType
     * </p>
     *
     * @param json JSON字符串
     * @param path JSON路径表达式
     * @return JsonNode节点，不存在返回null
     */
    private JsonNode parsePath(String json, String path) {
        if (json == null || json.isBlank() || path == null || path.isBlank()) {
            return null;
        }
        try {
            JsonNode current = objectMapper.readTree(json);
            String[] segments = path.split("\\.");
            for (String segment : segments) {
                if (segment.endsWith("]") && segment.contains("[")) {
                    String field = segment.substring(0, segment.indexOf('['));
                    int idx = Integer.parseInt(segment.substring(segment.indexOf('[') + 1, segment.length() - 1));
                    current = current.path(field);
                    if (!current.isArray() || current.size() <= idx) {
                        return null;
                    }
                    current = current.get(idx);
                } else {
                    current = current.path(segment);
                }
                if (current.isMissingNode()) {
                    return null;
                }
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }
}
