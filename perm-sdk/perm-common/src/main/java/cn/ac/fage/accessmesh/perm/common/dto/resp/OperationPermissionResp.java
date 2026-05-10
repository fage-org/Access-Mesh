package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.time.LocalDateTime;

/**
 * 操作权限详情响应
 * <p>
 * 用于返回操作权限的详细信息。
 * </p>
 */
public record OperationPermissionResp(
    /**
     * 权限ID
     */
    Long id,
    /**
     * 租户ID
     */
    Long tenantId,
    /**
     * 资源类型码
     */
    String resourceTypeCode,
    /**
     * 资源类型名称
     */
    String resourceTypeName,
    /**
     * 资源编码
     */
    String code,
    /**
     * 资源名称
     */
    String name,
    /**
     * 二进制位，用于权限位运算
     */
    Long binaryBit,
    /**
     * 继承掩码
     */
    Long inheritMask,
    /**
     * 创建时间
     */
    LocalDateTime createdAt,
    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {}
