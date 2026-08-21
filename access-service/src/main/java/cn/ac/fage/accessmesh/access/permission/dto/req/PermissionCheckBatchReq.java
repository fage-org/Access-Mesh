package cn.ac.fage.accessmesh.access.permission.dto.req;

import java.util.Set;

/**
 * 批量权限检查请求体
 * <p>
 * 用于内部批量权限检查，同时检查多个目标ID的权限。
 * 用于access-service内部服务调用。
 * </p>
 *
 * @param operatorId    操作者ID
 * @param targetType    目标类型（USER/ROLE/RESOURCE等）
 * @param targetIds     目标ID集合，批量检查多个目标
 * @param operationCodes 操作编码集合（VIEW/CREATE/EDIT/DELETE/MANAGE等）
 */
public record PermissionCheckBatchReq(
    Long operatorId,
    String targetType,
    Set<Long> targetIds,
    Set<String> operationCodes
) {}