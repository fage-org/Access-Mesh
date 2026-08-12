package cn.ac.fage.accessmesh.access.admin.support;

/**
 * 用户-组织关联同步的统一键拼装工具。
 * <p>
 * 将分散在 UserServiceImpl / UserOrgServiceImpl / SyncTaskBuilder 中的
 * relationKey 拼装逻辑收敛到一处，防止前缀硬编码不一致。
 * <p>
 * <b>契约约束</b>：permission-center api-contract.md §6.2.2.4 明确规定
 * {@code PERM_USER_ROLE_SYNC} 的 relationKey 固定格式为 {@code ORG:{orgExternalId}}，
 * <b>无论角色类型是 ORG 还是 POSITION</b>。permission-center 通过
 * {@code roleTypeCode=ORG + roleExternalId=orgExternalId} 解析为所属组织角色 ID，
 * 写入 {@code user_role.relation_id}。因此 relationKey 前缀始终为 {@code ORG}。
 * <p>
 * {@code roleTypeCode}（ORG / POSITION）仅用于 sync envelope 的 {@code roleTypeCode} 字段，
 * 不参与 relationKey 拼装。
 */
public final class UserOrgKeys {

    private UserOrgKeys() {}

    /**
     * 用户-组织关联同步的 relationKey 拼装。
     * <p>
     * 固定格式 {@code ORG:{orgIdOrExternalId}}，与 permission-center
     * api-contract.md §6.2.2.4 (PERM_USER_ROLE_SYNC) 对齐。
     * 即使角色类型为 POSITION，relationKey 前缀也使用 ORG。
     *
     * @param orgIdOrExternalId 组织 ID 或外部标识，不能为空
     * @return relationKey，格式 ORG:{orgIdOrExternalId}
     * @throws IllegalArgumentException orgIdOrExternalId 为空时抛出
     */
    public static String relationKey(Object orgIdOrExternalId) {
        if (orgIdOrExternalId == null) {
            throw new IllegalArgumentException("orgIdOrExternalId 不能为空");
        }
        return "ORG:" + orgIdOrExternalId;
    }
}
