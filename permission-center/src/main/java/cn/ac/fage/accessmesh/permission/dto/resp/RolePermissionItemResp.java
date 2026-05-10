package cn.ac.fage.accessmesh.permission.dto.resp;

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
 * @param scopeAll         是否范围全部
 * @param dependOn         依赖的权限ID，无依赖时为null
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
    boolean scopeAll,
    Long dependOn
) {}