package cn.ac.fage.accessmesh.access.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;

import java.time.LocalDateTime;

/**
 * 角色权限条目响应体
 * <p>
 * 返回单个角色权限配置条目的详细信息。
 * 用于角色权限配置的响应。
 * </p>
 *
 * @param id               权限配置ID
 * @param resourceTypeCode 资源类型编码
 * @param resourceCode     资源编码
 * @param codeType         编码类型
 * @param resourceName     资源名称
 * @param operationCode    操作编码
 * @param canGrant         是否可授予他人
 * @param conditionCode    条件编码，无条件时为null
 * @param scopeMode        范围模式
 * @param dependOn         依赖的权限ID，无依赖时为null
 * @param grantSource      授权来源，MANUAL/AUTO_DEP
 * @param grantedBits      授予操作位的十进制字符串
 * @param createdAt        创建时间
 * @param childCount       直接子权限数量
 */
public record RolePermissionItemResp(
    Long id,
    String resourceTypeCode,
    String resourceCode,
    String codeType,
    String resourceName,
    String operationCode,
    Boolean canGrant,
    String conditionCode,
    ScopeMode scopeMode,
    Long dependOn,
    String grantSource,
    String grantedBits,
    LocalDateTime createdAt,
    long childCount
) {}
