package cn.ac.fage.accessmesh.access.admin.security;

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
 *   <tr><th>orgType</th><th>含义</th><th>CREATE</th><th>UPDATE</th><th>DELETE</th><th>VIEW</th><th>成员关系</th></tr>
 *   <tr><td>null / "1" / "ORG"</td><td>普通组织</td><td>CREATE</td><td>UPDATE</td><td>DELETE</td><td>VIEW</td><td>MANAGE_MEMBER</td></tr>
 *   <tr><td>"2" / "POSITION"</td><td>岗位</td><td>CREATE_POSITION</td><td>UPDATE_POSITION</td><td>DELETE_POSITION</td><td>VIEW_POSITION</td><td>ASSIGN_POSITION_USER</td></tr>
 * </table>
 *
 * <p>
 * v1.4 起普通组织的成员关系从 UPDATE 拆出 MANAGE_MEMBER，与岗位的 ASSIGN_POSITION_USER 同构，
 * 实现「编辑组织节点」与「管理组织成员」的独立配权。
 * 同版本 VIEW 类操作码细化到资源类型并区分岗位（VIEW_POSITION），
 * 实现「看普通组织 ≠ 看岗位」独立配权。
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

    /**
     * 读类操作码映射：岗位 → VIEW_POSITION，普通组织 → VIEW（v1.4 VIEW 类细化到资源类型）。
     * 与 CREATE/UPDATE/DELETE 同构，实现「读普通组织 ≠ 读岗位」独立配权。
     */
    private static final Map<String, String> VIEW_MAP = Map.of(
        ORG_TYPE_POSITION_NUM, AdminOperationCode.VIEW_POSITION,
        ORG_TYPE_POSITION_LABEL, AdminOperationCode.VIEW_POSITION
    );

    // ===== 成员关系操作码映射（UserOrgServiceImpl 使用） =====

    /**
     * 成员关系操作码：普通组织 MANAGE_MEMBER，岗位 ASSIGN_POSITION_USER（与各自的 UPDATE 解耦）。
     * v1.4 起普通组织从 UPDATE 拆出 MANAGE_MEMBER，便于独立配权。
     */
    private static final Map<String, String> USER_ORG_UPDATE_MAP = Map.of(
        ORG_TYPE_REGULAR_NUM, AdminOperationCode.MANAGE_MEMBER,
        ORG_TYPE_REGULAR_LABEL, AdminOperationCode.MANAGE_MEMBER,
        ORG_TYPE_POSITION_NUM, AdminOperationCode.ASSIGN_POSITION_USER,
        ORG_TYPE_POSITION_LABEL, AdminOperationCode.ASSIGN_POSITION_USER
    );

    /**
     * 根据 orgType 解析组织 CRUD/VIEW 操作码。
     * <p>
     * 岗位（orgType="2" 或 "POSITION"）返回精化操作码（如 CREATE_POSITION / VIEW_POSITION），
     * 普通组织（orgType=null / "1" / "ORG" / 其他）返回标准操作码（如 CREATE / VIEW）。
     *
     * @param orgType     sys_org.orgType 值，可为 null
     * @param baseOperation 基础操作码（CREATE / UPDATE / DELETE / VIEW）
     * @return 对应 orgType 的操作码
     */
    public static String resolve(String orgType, String baseOperation) {
        Map<String, String> map = switch (baseOperation) {
            case AdminOperationCode.CREATE -> CREATE_MAP;
            case AdminOperationCode.UPDATE -> UPDATE_MAP;
            case AdminOperationCode.DELETE -> DELETE_MAP;
            case AdminOperationCode.VIEW -> VIEW_MAP;
            default -> Map.of();
        };
        return map.getOrDefault(normalize(orgType), baseOperation);
    }

    /**
     * 根据 orgType 解析成员关系操作码（UserOrgServiceImpl 使用）。
     * <p>
     * 与 {@link #resolve} 的区别：成员关系（添加/移除成员、设主）走独立操作码——
     * 普通组织 → MANAGE_MEMBER，岗位 → ASSIGN_POSITION_USER；
     * 与组织节点本身的 UPDATE（编辑名称/移动/启停）解耦，便于独立配权。
     *
     * @param orgType     sys_org.orgType 值，可为 null（按普通组织处理）
     * @param baseOperation 基础操作码（目前仅 UPDATE 有区分，其他透明回退到 {@link #resolve}）
     * @return 对应 orgType 的成员关系操作码
     */
    public static String resolveForUserOrg(String orgType, String baseOperation) {
        if (AdminOperationCode.UPDATE.equals(baseOperation)) {
            // null / 未匹配的 orgType 默认按普通组织处理 → MANAGE_MEMBER
            String mapped = USER_ORG_UPDATE_MAP.get(normalize(orgType));
            return mapped != null ? mapped : AdminOperationCode.MANAGE_MEMBER;
        }
        return resolve(orgType, baseOperation);
    }

    /**
     * 判断 orgType 是否为岗位类型。
     * <p>
     * 历史上 orgType 字段同时使用过数值字符串（"1"/"2"）和语义字符串（"ORG"/"POSITION"），
     * 组织/岗位角色类型映射与跨域查询服务（UserRoleQueryServiceImpl）保持一致。
     *
     * @param orgType sys_org.orgType 值
     * @return true 表示岗位
     */
    public static boolean isPositionOrg(String orgType) {
        String n = normalize(orgType);
        return ORG_TYPE_POSITION_NUM.equals(n) || ORG_TYPE_POSITION_LABEL.equalsIgnoreCase(n);
    }

    /**
     * 规范化 orgType：null / 空串 / "1" / "ORG" 均视为普通组织，返回 "1" 用于 Map lookup。
     * <p>
     * 此方法保证返回值永远不为 null，从而避免 {@code Map.of().get(null)} 抛出 NPE
     * （{@code Map.of()} 底层不可变实现在 {@code get(null)} 时调用 {@code key.hashCode()} 会 NPE）。
     * <p>
     * 规范化后的值参与 {@link #CREATE_MAP} / {@link #USER_ORG_UPDATE_MAP} 等的 lookup；
     * 未知值（非 null / "1" / "ORG" / "2" / "POSITION"）原样返回，自然 fallback 到基础操作码。
     */
    private static String normalize(String orgType) {
        if (orgType == null || orgType.isEmpty()) {
            return ORG_TYPE_REGULAR_NUM; // "1" — 普通组织
        }
        if (ORG_TYPE_REGULAR_LABEL.equalsIgnoreCase(orgType)) {
            return ORG_TYPE_REGULAR_NUM; // "ORG" → "1"，统一用数值键 lookup
        }
        return orgType;
    }

    private OrgOperationCodeMapper() {}
}
