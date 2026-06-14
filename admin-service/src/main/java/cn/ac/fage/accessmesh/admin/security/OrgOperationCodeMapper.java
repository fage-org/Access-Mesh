package cn.ac.fage.accessmesh.admin.security;

import java.util.Map;

/**
 * orgType → 操作码声明式映射（单一事实源）。
 * <p>
 * 将 {@code SysOrg.orgType} 到 {@link AdminOperationCode} 的分发关系集中到一处，
 * 避免在 OrgServiceImpl / UserOrgServiceImpl 中散落 if-else 分支。
 * 新增 orgType 子类型时只需扩展本映射表。
 *
 * <h3>映射总览</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>orgType</th><th>含义</th><th>CREATE</th><th>UPDATE</th><th>DELETE</th><th>成员关系(UPDATE)</th></tr>
 *   <tr><td>null / "1" / "ORG"</td><td>普通组织</td><td>CREATE</td><td>UPDATE</td><td>DELETE</td><td>UPDATE</td></tr>
 *   <tr><td>"2" / "POSITION"</td><td>岗位</td><td>CREATE_POSITION</td><td>UPDATE_POSITION</td><td>DELETE_POSITION</td><td>ASSIGN_POSITION_USER</td></tr>
 * </table>
 *
 * <p>
 * 详见 docs/design/org-user-permission-contract.md §4 D 区与 §5 备注⁴。
 */
public final class OrgOperationCodeMapper {

    // ===== orgType 语义常量 =====

    /** 普通组织 orgType 值（历史数值形式） */
    private static final String ORG_TYPE_REGULAR_NUM = "1";
    /** 岗位 orgType 值（历史数值形式） */
    private static final String ORG_TYPE_POSITION_NUM = "2";
    /** 普通组织 orgType 值（语义字符串形式） */
    private static final String ORG_TYPE_REGULAR_LABEL = "ORG";
    /** 岗位 orgType 值（语义字符串形式） */
    private static final String ORG_TYPE_POSITION_LABEL = "POSITION";

    // ===== CRUD 操作码映射（OrgServiceImpl 使用） =====

    private static final Map<String, String> CREATE_MAP = Map.of(
        ORG_TYPE_POSITION_NUM, AdminOperationCode.CREATE_POSITION,
        ORG_TYPE_POSITION_LABEL, AdminOperationCode.CREATE_POSITION
    );

    private static final Map<String, String> UPDATE_MAP = Map.of(
        ORG_TYPE_POSITION_NUM, AdminOperationCode.UPDATE_POSITION,
        ORG_TYPE_POSITION_LABEL, AdminOperationCode.UPDATE_POSITION
    );

    private static final Map<String, String> DELETE_MAP = Map.of(
        ORG_TYPE_POSITION_NUM, AdminOperationCode.DELETE_POSITION,
        ORG_TYPE_POSITION_LABEL, AdminOperationCode.DELETE_POSITION
    );

    // ===== 成员关系操作码映射（UserOrgServiceImpl 使用） =====

    /**
     * 岗位的成员关系操作码：ASSIGN_POSITION_USER（与普通组织 UPDATE 解耦）。
     * 普通组织成员增删/设主仍走 UPDATE。
     */
    private static final Map<String, String> USER_ORG_UPDATE_MAP = Map.of(
        ORG_TYPE_POSITION_NUM, AdminOperationCode.ASSIGN_POSITION_USER,
        ORG_TYPE_POSITION_LABEL, AdminOperationCode.ASSIGN_POSITION_USER
    );

    /**
     * 根据 orgType 解析组织 CRUD 操作码。
     * <p>
     * 岗位（orgType="2" 或 "POSITION"）返回精化操作码（如 CREATE_POSITION），
     * 普通组织（orgType=null / "1" / "ORG" / 其他）返回标准操作码（如 CREATE）。
     *
     * @param orgType     sys_org.orgType 值，可为 null
     * @param baseOperation 基础操作码（CREATE / UPDATE / DELETE）
     * @return 对应 orgType 的操作码
     */
    public static String resolve(String orgType, String baseOperation) {
        Map<String, String> map = switch (baseOperation) {
            case AdminOperationCode.CREATE -> CREATE_MAP;
            case AdminOperationCode.UPDATE -> UPDATE_MAP;
            case AdminOperationCode.DELETE -> DELETE_MAP;
            default -> Map.of();
        };
        return map.getOrDefault(normalize(orgType), baseOperation);
    }

    /**
     * 根据 orgType 解析成员关系操作码（UserOrgServiceImpl 使用）。
     * <p>
     * 与 {@link #resolve} 的区别：岗位的成员关系走 ASSIGN_POSITION_USER 而非 UPDATE_POSITION，
     * 普通组织的成员关系仍走 UPDATE。
     *
     * @param orgType     sys_org.orgType 值，可为 null
     * @param baseOperation 基础操作码（目前仅 UPDATE 有区分）
     * @return 对应 orgType 的成员关系操作码
     */
    public static String resolveForUserOrg(String orgType, String baseOperation) {
        if (AdminOperationCode.UPDATE.equals(baseOperation)) {
            return USER_ORG_UPDATE_MAP.getOrDefault(normalize(orgType), baseOperation);
        }
        return resolve(orgType, baseOperation);
    }

    /**
     * 判断 orgType 是否为岗位类型。
     * <p>
     * 历史上 orgType 字段同时使用过数值字符串（"1"/"2"）和语义字符串（"ORG"/"POSITION"），
     * 与 RoleProxyServiceImpl#mapOrgTypeToRoleType 保持兼容。
     *
     * @param orgType sys_org.orgType 值
     * @return true 表示岗位
     */
    public static boolean isPositionOrg(String orgType) {
        String n = normalize(orgType);
        return ORG_TYPE_POSITION_NUM.equals(n) || ORG_TYPE_POSITION_LABEL.equalsIgnoreCase(n);
    }

    /**
     * 规范化 orgType：null / 空串 / "1" / "ORG" 均视为普通组织，返回原值用于 Map lookup。
     * 当前实现直接返回原值，因为 Map 已包含所有已知的岗位 orgType 形式，
     * 未知值自然 fallback 到基础操作码。
     */
    private static String normalize(String orgType) {
        return orgType;
    }

    private OrgOperationCodeMapper() {}
}
