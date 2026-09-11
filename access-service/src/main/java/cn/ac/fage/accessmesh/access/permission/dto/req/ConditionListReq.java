package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 权限条件列表请求（T-PERM-048 双轨制）。
 *
 * @param includeInline 是否包含授权页内联条件：缺省/false 只返回 MANAGED 管理页条件
 *                      （权限条件页口径——内联条件在管理页查不到也不能管理）；
 *                      true 时含 INLINE（授权页回显内联条件名称/规则摘要用）。读取无门禁维持。
 */
public record ConditionListReq(Boolean includeInline) {}
