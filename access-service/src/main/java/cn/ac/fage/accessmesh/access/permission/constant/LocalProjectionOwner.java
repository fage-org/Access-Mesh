package cn.ac.fage.accessmesh.access.permission.constant;

import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;

/**
 * AccessMesh 本地权限投影所有权与保留业务键。
 * <p>
 * 管理事实派生的 abstract_user / abstract_role / resource_entity / user_role
 * 统一标记 {@link #SERVICE_CODE}，只能由 {@code access.application} 经
 * LocalProjectionDomainService 写入。
 * </p>
 * <p>
 * 保留业务键终态（T-ACCESS-016 §4.3/§13.2，T-ACCESS-018 落地；管理入口保留清单随 T-ACCESS-019 增补 ROLE）：
 * subject 侧 {@link #SUBJECT_LOCAL_USER}（原 ADMIN_USER 更名，无兼容别名）；role 侧 ORG|POSITION
 * 不变；SYS_USER_ORG 不变。resource 侧已取消类型级保留——USER/MENU 是公共基础类型，
 * 本地 resource_entity 投影行改按所有权保护（外部 sync mutation 前置
 * {@code LocalProjectionGuard.rejectIfLocalResource}，owner=access-service 即 20045），
 * 仅管理入口（人工建资源）保留 {@link #isReservedResourceType(String)} 清单
 * {USER, ORG, MENU, ROLE}：人工不得绕过管理事实链路直接建本地业务资源投影
 * （ROLE 资源由角色管理写路径同事务产出，T-ACCESS-019）。
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

    /**
     * 管理入口（resource-entity create/update 等人工建资源）的类型保留清单。
     * <p>
     * 仅约束人工入口：USER/ORG/MENU/ROLE 四类本地业务资源投影必须经管理事实链路
     * （用户/组织/菜单/角色管理）创建，其余类型不受限。ROLE 随 T-ACCESS-019 加入——
     * resource_entity(ROLE).code=roleId 由角色管理写路径同事务产出，人工入口建 ROLE
     * 会产生无 abstract_role 对应的孤儿资源。外部 sync 入口不使用本清单
     * （resource 侧已取消类型级保留，改为所有权检查，见类注释）。
     * </p>
     */
    public static boolean isReservedResourceType(String resourceTypeCode) {
        return ResourceTypeCode.USER.equals(resourceTypeCode)
            || ResourceTypeCode.ORG.equals(resourceTypeCode)
            || ResourceTypeCode.MENU.equals(resourceTypeCode)
            || ResourceTypeCode.ROLE.equals(resourceTypeCode);
    }

    public static boolean isReservedUserRoleSource(String sourceType) {
        return SOURCE_TYPE_SYS_USER_ORG.equals(sourceType);
    }

    public static boolean isInternalSourceService(String sourceService) {
        return SERVICE_CODE.equals(sourceService) || LEGACY_ADMIN_SOURCE.equals(sourceService);
    }
}
