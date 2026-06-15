package cn.ac.fage.accessmesh.admin.support;

/**
 * 用户-组织关联同步的统一键拼装工具。
 * <p>
 * 将分散在 UserServiceImpl / UserOrgServiceImpl / SyncTaskBuilder 中的
 * relationKey 拼装逻辑收敛到一处，防止前缀硬编码（"ORG:" vs "POSITION:"）不一致。
 * <p>
 * 格式：{@code {roleTypeCode}:{orgId}}<br>
 * 示例：{@code ORG:1001}（普通组织）/ {@code POSITION:2001}（岗位）
 * <p>
 * 与 permission-center api-contract.md §6.2.2.4 (PERM_USER_ROLE_SYNC) 对齐。
 */
public final class UserOrgKeys {

    private UserOrgKeys() {}

    /**
     * 用户-组织关联同步的 relationKey 拼装
     *
     * @param roleTypeCode     角色类型编码（ORG / POSITION），不能为空
     * @param orgIdOrExternalId 组织 ID 或外部标识，不能为空
     * @return relationKey，格式 {roleTypeCode}:{orgIdOrExternalId}
     * @throws IllegalArgumentException 参数为空时抛出
     */
    public static String relationKey(String roleTypeCode, Object orgIdOrExternalId) {
        if (roleTypeCode == null || roleTypeCode.isBlank()) {
            throw new IllegalArgumentException("roleTypeCode 不能为空");
        }
        if (orgIdOrExternalId == null) {
            throw new IllegalArgumentException("orgIdOrExternalId 不能为空");
        }
        return roleTypeCode + ":" + orgIdOrExternalId;
    }
}
