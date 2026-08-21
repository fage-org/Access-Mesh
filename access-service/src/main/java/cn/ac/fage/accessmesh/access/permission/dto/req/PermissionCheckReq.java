package cn.ac.fage.accessmesh.access.permission.dto.req;

import java.util.Set;

/**
 * 权限检查请求体
 * <p>
 * 用于内部权限检查，验证操作者是否对目标拥有指定权限。
 * 用于access-service内部服务调用。
 * </p>
 *
 * @param operatorId    操作者ID
 * @param targetType    目标类型（USER/ROLE/RESOURCE等）
 * @param targetId      目标ID
 * @param operationCodes 操作编码集合（VIEW/CREATE/EDIT/DELETE/MANAGE等）
 */
public record PermissionCheckReq(
    Long operatorId,
    String targetType,
    Long targetId,
    Set<String> operationCodes
) {}