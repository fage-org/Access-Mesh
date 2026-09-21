package cn.ac.fage.accessmesh.access.resource.dto.req;

/**
 * 依赖声明诊断请求（T-PERM-073，契约 §12.3 declaration-status）。
 *
 * @param sourceService 可选：按声明来源服务过滤（缺省=全部服务）
 */
public record DependencyDeclarationStatusReq(String sourceService) {}
