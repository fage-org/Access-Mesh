package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 操作权限响应体
 * <p>
 * 返回操作权限的详细信息，包括编码、名称、二进制位等。
 * 用于操作权限查询接口的响应。
 * </p>
 *
 * @param id               操作权限ID
 * @param tenantId         租户ID
 * @param resourceTypeCode 资源类型编码
 * @param resourceTypeName 资源类型名称
 * @param code             操作权限编码，唯一标识
 * @param name             操作权限名称，用于显示
 * @param binaryBit        二进制位，用于位运算权限匹配
 * @param inheritMask      继承掩码，用于权限继承计算
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record OperationPermissionResp(
    Long id,
    Long tenantId,
    String resourceTypeCode,
    String resourceTypeName,
    String code,
    String name,
    Long binaryBit,
    Long inheritMask,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}