package cn.ac.fage.accessmesh.access.permission.constant;


/**
 * AccessMesh 本地权限投影所有权与保留业务键。
 * <p>
 * 管理事实派生的 abstract_user / abstract_role / resource_entity / user_role
 * 统一标记 {@link #SERVICE_CODE}，只能经 LocalProjectionDomainService 写入——
 * 调用方为 {@code access.application}（用户/组织/菜单编排）与 permission 域
 * 管理入口（角色/主体编排，T-ACCESS-019），事务由调用方 AppService 声明。
 * </p>
 * <p>
 * 保留业务键终态（T-ACCESS-016 §4.3/§13.2，T-ACCESS-018 落地）：
 * subject 侧 {@link #SUBJECT_LOCAL_USER}（原 ADMIN_USER 更名，无兼容别名）；role 侧 ORG|POSITION
 * 不变；SYS_USER_ORG 不变。
 * </p>
 * <p>
 * resource 侧所有权（T-PERM-052，2026-09-05 内部来源统一；T-ADMIN-025 增 ADMIN_FILE）：事实链路
 * 类型（USER/ORG/MENU/ROLE/ADMIN_FILE）由种子声明 {@code type_definition.extra.managedMode=SYNC + syncSourceService=access-service}
 * ——外部 sync 一律拒绝（来源不匹配）、管理面资源 CRUD 一律 20055（原类型保留清单与
 * {@code LocalProjectionGuard.rejectIfLocalResource/rejectIfForeignResource} 行级防线均已收编删除）。
 * </p>
 */
public final class LocalProjectionOwner {

    private LocalProjectionOwner() {}

    /** 本地投影 owner_service_code */
    public static final String SERVICE_CODE = "access-service";

    /** 旧内部同步来源，外部 sync 不得再冒充 */
    public static final String LEGACY_ADMIN_SOURCE = "admin-service";

    /** subject 保留类型（user_type）：本地访问主体（原 ADMIN_USER，T-ACCESS-016 更名） */
    public static final String SUBJECT_LOCAL_USER = "LOCAL_USER";
    /** role 保留类型（role_type 未收敛，ORG/POSITION 不变） */
    public static final String ROLE_ORG = "ORG";
    public static final String ROLE_POSITION = "POSITION";
    /** user_role 保留来源类型 */
    public static final String SOURCE_TYPE_SYS_USER_ORG = "SYS_USER_ORG";

    public static boolean isLocalOwner(String ownerServiceCode) {
        return SERVICE_CODE.equals(ownerServiceCode);
    }

    public static boolean isReservedSubjectType(String subjectTypeCode) {
        return SUBJECT_LOCAL_USER.equals(subjectTypeCode);
    }

    public static boolean isReservedRoleType(String roleTypeCode) {
        return ROLE_ORG.equals(roleTypeCode) || ROLE_POSITION.equals(roleTypeCode);
    }

    public static boolean isReservedUserRoleSource(String sourceType) {
        return SOURCE_TYPE_SYS_USER_ORG.equals(sourceType);
    }

    public static boolean isInternalSourceService(String sourceService) {
        return SERVICE_CODE.equals(sourceService) || LEGACY_ADMIN_SOURCE.equals(sourceService);
    }
}
