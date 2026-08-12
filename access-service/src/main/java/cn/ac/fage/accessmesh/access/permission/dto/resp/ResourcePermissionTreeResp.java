package cn.ac.fage.accessmesh.access.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.List;

/**
 * 资源权限树响应体
 * <p>
 * 用于用户资源权限树视图的树节点。
 * 表示单个资源节点及其子节点的权限信息。
 * </p>
 *
 * @param resourceEntityId 资源实体ID
 * @param domainCode       业务域编码
 * @param resourceCode     资源编码
 * @param resourceName     资源名称
 * @param resourceTypeCode 资源类型编码
 * @param codeType         编码类型
 * @param scopeMode        范围模式
 * @param operationCodes   操作权限编码列表
 * @param children         子节点列表
 */
public record ResourcePermissionTreeResp(
    Long resourceEntityId,
    String domainCode,
    String resourceCode,
    String resourceName,
    String resourceTypeCode,
    String codeType,
    ScopeMode scopeMode,
    List<String> operationCodes,
    List<ResourcePermissionTreeResp> children
) {}
