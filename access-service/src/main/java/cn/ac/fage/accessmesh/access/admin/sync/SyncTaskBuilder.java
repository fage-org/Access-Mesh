package cn.ac.fage.accessmesh.access.admin.sync;

import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.support.UserOrgKeys;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskEnvelope;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步任务信封构造器
 * <p>
 * S4 任务生产器的核心工具：根据 admin-service 的实体快照（{@link SysUser} /
 * {@link SysOrg} / {@link SysMenu}）和操作类型，构造出符合
 * {@code docs/design/permission-center/api-contract.md §6.2.2} 契约的
 * {@link SyncTaskEnvelope}。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>{@code businessKey} 严格遵循 §6.2.2.4，inline 实现 percent-encoding，
 *       与 permission-center {@code SyncKeyCodec} 镜像。</li>
 *   <li>{@code payload} 字段对齐 §6.2.2.3 各 sync 接口请求体；
 *       使用注入的 {@link ObjectMapper} 序列化。</li>
 *   <li>{@code displayAttrs} 仅放 {@code entityType / externalId / operationType}，
 *       严禁参与执行路由。</li>
 *   <li>组织变更生成 2 条 envelope（ABSTRACT_ROLE + RESOURCE_ENTITY）；
 *       用户变更生成 2 条 envelope（ABSTRACT_USER + RESOURCE_ENTITY for ADMIN_USER）。</li>
 * </ul>
 * </p>
 */
@Component
public final class SyncTaskBuilder {

    /** syncAction 常量 — abstract_user 同步 */
    public static final String ACTION_ABSTRACT_USER_SYNC = "PERM_ABSTRACT_USER_SYNC";
    /** syncAction 常量 — abstract_role 同步 */
    public static final String ACTION_ABSTRACT_ROLE_SYNC = "PERM_ABSTRACT_ROLE_SYNC";
    /** syncAction 常量 — user_role 同步 */
    public static final String ACTION_USER_ROLE_SYNC = "PERM_USER_ROLE_SYNC";
    /** syncAction 常量 — resource_entity 同步 */
    public static final String ACTION_RESOURCE_ENTITY_SYNC = "PERM_RESOURCE_ENTITY_SYNC";

    /** 当前 payload 契约版本 */
    public static final int PAYLOAD_VERSION = 1;

    private static final String SOURCE_SERVICE = "admin-service";
    private static final String CODE_TYPE_DEFAULT = "default";

    private final ObjectMapper objectMapper;
    private final SyncSequenceProvider sequenceProvider;

    /**
     * 构造同步任务信封构造器
     *
     * @param objectMapper     JSON 序列化工具
     * @param sequenceProvider 序号生成器
     */
    public SyncTaskBuilder(ObjectMapper objectMapper, SyncSequenceProvider sequenceProvider) {
        this.objectMapper = objectMapper;
        this.sequenceProvider = sequenceProvider;
    }

    // =====================================================================
    // 用户相关：abstract_user + resource_entity(ADMIN_USER) 双 envelope
    // =====================================================================

    /**
     * 用户创建/更新事件 → abstract_user UPSERT + resource_entity(ADMIN_USER) UPSERT。
     *
     * @param user 用户快照
     * @return 2 条 envelope
     */
    public List<SyncTaskEnvelope> userUpsert(SysUser user) {
        return List.of(
            buildAbstractUser(user, "UPSERT", "upsert"),
            buildUserResource(user, "UPSERT", "upsert")
        );
    }

    /**
     * 用户停用事件 → abstract_user DISABLE + resource_entity(ADMIN_USER) DISABLE。
     *
     * @param user 用户快照（status 已置为 0）
     * @return 2 条 envelope
     */
    public List<SyncTaskEnvelope> userDisable(SysUser user) {
        return List.of(
            buildAbstractUser(user, "DISABLE", "disable"),
            buildUserResource(user, "DISABLE", "disable")
        );
    }

    /**
     * 用户启用事件 → abstract_user UPSERT + resource_entity(ADMIN_USER) UPSERT。
     * <p>启用通过 enabled=true 的 UPSERT 表达，与 §6.2.2.4 targetStatus 规范一致。</p>
     *
     * @param user 用户快照（status 已置为 1）
     * @return 2 条 envelope
     */
    public List<SyncTaskEnvelope> userEnable(SysUser user) {
        return List.of(
            buildAbstractUser(user, "UPSERT", "enable"),
            buildUserResource(user, "UPSERT", "enable")
        );
    }

    /**
     * 用户删除事件 → abstract_user DELETE + resource_entity(ADMIN_USER) DELETE。
     *
     * @param userId     用户 ID
     * @param externalId 外部 ID（通常等于 userId.toString()）
     * @return 2 条 envelope
     */
    public List<SyncTaskEnvelope> userDelete(Long userId, String externalId) {
        return List.of(
            buildAbstractUserDelete(userId, externalId),
            buildUserResourceDelete(userId, externalId)
        );
    }

    private SyncTaskEnvelope buildAbstractUser(SysUser user, String operation, String auditOpType) {
        String externalId = String.valueOf(user.getId());
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", operation);
        payloadMap.put("subjectTypeCode", AdminResourceType.USER);
        payloadMap.put("subjectExternalId", externalId);
        payloadMap.put("name", user.getName());
        payloadMap.put("enabled", user.getStatus() != null && user.getStatus() == 1);
        payloadMap.put("extra", Map.of("username", nullToEmpty(user.getUsername())));
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_user");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = abstractUserBusinessKey(AdminResourceType.USER, externalId);
        return new SyncTaskEnvelope(
            ACTION_ABSTRACT_USER_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("abstract_user", externalId, auditOpType),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    private SyncTaskEnvelope buildAbstractUserDelete(Long userId, String externalId) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", "DELETE");
        payloadMap.put("subjectTypeCode", AdminResourceType.USER);
        payloadMap.put("subjectExternalId", externalId);
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_user");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = abstractUserBusinessKey(AdminResourceType.USER, externalId);
        return new SyncTaskEnvelope(
            ACTION_ABSTRACT_USER_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("abstract_user", externalId, "delete"),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    private SyncTaskEnvelope buildUserResource(SysUser user, String operation, String auditOpType) {
        String externalId = String.valueOf(user.getId());
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", operation);
        payloadMap.put("resourceTypeCode", AdminResourceType.USER);
        payloadMap.put("resourceCode", externalId);
        payloadMap.put("codeType", CODE_TYPE_DEFAULT);
        payloadMap.put("name", user.getName());
        payloadMap.put("status", user.getStatus() != null ? user.getStatus() : 1);
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_user");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = resourceEntityBusinessKey(AdminResourceType.USER, externalId, CODE_TYPE_DEFAULT);
        return new SyncTaskEnvelope(
            ACTION_RESOURCE_ENTITY_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("resource_entity:" + AdminResourceType.USER, externalId, auditOpType),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    private SyncTaskEnvelope buildUserResourceDelete(Long userId, String externalId) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", "DELETE");
        payloadMap.put("resourceTypeCode", AdminResourceType.USER);
        payloadMap.put("resourceCode", externalId);
        payloadMap.put("codeType", CODE_TYPE_DEFAULT);
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_user");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = resourceEntityBusinessKey(AdminResourceType.USER, externalId, CODE_TYPE_DEFAULT);
        return new SyncTaskEnvelope(
            ACTION_RESOURCE_ENTITY_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("resource_entity:" + AdminResourceType.USER, externalId, "delete"),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    // =====================================================================
    // 组织相关：abstract_role + resource_entity(ADMIN_ORG) 双 envelope
    // =====================================================================

    /**
     * 组织创建/更新事件 → abstract_role UPSERT + resource_entity(ADMIN_ORG) UPSERT。
     *
     * @param org 组织快照
     * @return 2 条 envelope
     */
    public List<SyncTaskEnvelope> orgUpsert(SysOrg org) {
        return List.of(
            buildAbstractRole(org, "UPSERT", "upsert"),
            buildOrgResource(org, "UPSERT", "upsert")
        );
    }

    /**
     * 组织停用事件 → abstract_role DISABLE + resource_entity(ADMIN_ORG) DISABLE。
     *
     * @param org 组织快照
     * @return 2 条 envelope
     */
    public List<SyncTaskEnvelope> orgDisable(SysOrg org) {
        return List.of(
            buildAbstractRole(org, "DISABLE", "disable"),
            buildOrgResource(org, "DISABLE", "disable")
        );
    }

    /**
     * 组织删除事件 → abstract_role DELETE + resource_entity(ADMIN_ORG) DELETE。
     *
     * @param orgId      组织 ID
     * @param externalId 外部 ID（通常等于 orgId.toString()）
     * @param orgType    sys_org.org_type 删除前快照（{@code ORG} 或 {@code POSITION}）；
     *                   abstract_role business_key 必须使用与创建时一致的 roleTypeCode，否则
     *                   permission-center 找不到对应 metadata 而漏删
     * @return 2 条 envelope
     */
    public List<SyncTaskEnvelope> orgDelete(Long orgId, String externalId, String orgType) {
        String roleTypeCode = "POSITION".equalsIgnoreCase(orgType) ? "POSITION" : "ORG";
        return List.of(
            buildAbstractRoleDelete(externalId, roleTypeCode),
            buildOrgResourceDelete(externalId)
        );
    }

    private SyncTaskEnvelope buildAbstractRole(SysOrg org, String operation, String auditOpType) {
        String externalId = String.valueOf(org.getId());
        // roleTypeCode 取 ORG（默认）；POSITION 由 orgType 推导
        String roleTypeCode = "POSITION".equalsIgnoreCase(org.getOrgType()) ? "POSITION" : "ORG";
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", operation);
        payloadMap.put("roleTypeCode", roleTypeCode);
        payloadMap.put("roleExternalId", externalId);
        payloadMap.put("name", org.getName());
        payloadMap.put("parentRoleTypeCode", roleTypeCode);
        payloadMap.put("parentRoleExternalId",
            org.getParentId() != null && org.getParentId() != 0L ? String.valueOf(org.getParentId()) : null);
        payloadMap.put("status", org.getStatus() != null ? org.getStatus() : 1);
        payloadMap.put("sortOrder", org.getSortOrder());
        payloadMap.put("extra", Map.of("orgType", nullToEmpty(org.getOrgType())));
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_org");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = abstractRoleBusinessKey(roleTypeCode, externalId);
        return new SyncTaskEnvelope(
            ACTION_ABSTRACT_ROLE_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("abstract_role", externalId, auditOpType),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    private SyncTaskEnvelope buildAbstractRoleDelete(String externalId, String roleTypeCode) {
        if (!"ORG".equals(roleTypeCode) && !"POSITION".equals(roleTypeCode)) {
            throw new IllegalArgumentException(
                "buildAbstractRoleDelete.roleTypeCode must be ORG or POSITION, got: " + roleTypeCode);
        }
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", "DELETE");
        payloadMap.put("roleTypeCode", roleTypeCode);
        payloadMap.put("roleExternalId", externalId);
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_org");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = abstractRoleBusinessKey(roleTypeCode, externalId);
        return new SyncTaskEnvelope(
            ACTION_ABSTRACT_ROLE_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("abstract_role", externalId, "delete"),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    private SyncTaskEnvelope buildOrgResource(SysOrg org, String operation, String auditOpType) {
        String externalId = String.valueOf(org.getId());
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", operation);
        payloadMap.put("resourceTypeCode", AdminResourceType.ORG);
        payloadMap.put("resourceCode", externalId);
        payloadMap.put("codeType", CODE_TYPE_DEFAULT);
        payloadMap.put("name", org.getName());
        payloadMap.put("parentResourceTypeCode", AdminResourceType.ORG);
        payloadMap.put("parentResourceCode",
            org.getParentId() != null && org.getParentId() != 0L ? String.valueOf(org.getParentId()) : null);
        payloadMap.put("status", org.getStatus() != null ? org.getStatus() : 1);
        payloadMap.put("sortOrder", org.getSortOrder());
        payloadMap.put("extra", Map.of("orgType", nullToEmpty(org.getOrgType())));
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_org");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = resourceEntityBusinessKey(AdminResourceType.ORG, externalId, CODE_TYPE_DEFAULT);
        return new SyncTaskEnvelope(
            ACTION_RESOURCE_ENTITY_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("resource_entity:" + AdminResourceType.ORG, externalId, auditOpType),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    private SyncTaskEnvelope buildOrgResourceDelete(String externalId) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", "DELETE");
        payloadMap.put("resourceTypeCode", AdminResourceType.ORG);
        payloadMap.put("resourceCode", externalId);
        payloadMap.put("codeType", CODE_TYPE_DEFAULT);
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_org");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = resourceEntityBusinessKey(AdminResourceType.ORG, externalId, CODE_TYPE_DEFAULT);
        return new SyncTaskEnvelope(
            ACTION_RESOURCE_ENTITY_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("resource_entity:" + AdminResourceType.ORG, externalId, "delete"),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    // =====================================================================
    // 用户-组织关系：user_role BIND / UNBIND
    // =====================================================================

    /**
     * 用户加入组织事件 → user_role BIND。
     *
     * @param userId             用户 ID
     * @param orgId              组织 ID
     * @param roleTypeCode       目标角色类型（{@code ORG} 或 {@code POSITION}），由调用方根据
     *                           sys_org.org_type 解析；本方法不做二次推断，避免错配 business_key
     * @param relationKey        业务原文（如 {@code ORG:2001}）
     * @param treeRootExternalId 组织树根 externalId（用于与 full-sync scopeKey 对账）
     * @return 单条 envelope
     */
    public SyncTaskEnvelope userOrgBind(Long userId, Long orgId, String roleTypeCode,
                                         String relationKey, String treeRootExternalId) {
        return buildUserRole(userId, orgId, roleTypeCode, relationKey, treeRootExternalId, "BIND", "bind");
    }

    /**
     * 用户退出组织事件 → user_role UNBIND。
     *
     * @param userId             用户 ID
     * @param orgId              组织 ID
     * @param roleTypeCode       目标角色类型（{@code ORG} 或 {@code POSITION}），由调用方根据
     *                           sys_org.org_type 解析；本方法不做二次推断
     * @param relationKey        业务原文（如 {@code ORG:2001}）
     * @param treeRootExternalId 组织树根 externalId（用于与 full-sync scopeKey 对账）
     * @return 单条 envelope
     */
    public SyncTaskEnvelope userOrgUnbind(Long userId, Long orgId, String roleTypeCode,
                                           String relationKey, String treeRootExternalId) {
        return buildUserRole(userId, orgId, roleTypeCode, relationKey, treeRootExternalId, "UNBIND", "unbind");
    }

    private SyncTaskEnvelope buildUserRole(Long userId, Long orgId, String roleTypeCode,
                                           String relationKey, String treeRootExternalId,
                                           String operation, String auditOpType) {
        if (!"ORG".equals(roleTypeCode) && !"POSITION".equals(roleTypeCode)) {
            throw new IllegalArgumentException(
                "buildUserRole.roleTypeCode must be ORG or POSITION, got: " + roleTypeCode);
        }
        String userExternalId = String.valueOf(userId);
        String orgExternalId = String.valueOf(orgId);
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", operation);
        payloadMap.put("sourceType", "SYS_USER_ORG");
        payloadMap.put("subjectTypeCode", AdminResourceType.USER);
        payloadMap.put("subjectExternalId", userExternalId);
        payloadMap.put("roleTypeCode", roleTypeCode);
        payloadMap.put("treeRootExternalId", treeRootExternalId);
        payloadMap.put("roleExternalId", orgExternalId);
        payloadMap.put("relationKey", relationKey);
        payloadMap.put("validFrom", null);
        payloadMap.put("validTo", null);
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_user_org");
        payloadMap.put("sourceEntityId", userExternalId + ":" + orgExternalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = userRoleBusinessKey(AdminResourceType.USER, userExternalId,
            roleTypeCode, orgExternalId, relationKey);
        return new SyncTaskEnvelope(
            ACTION_USER_ROLE_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("user_role", userExternalId + ":" + orgExternalId, auditOpType),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    // =====================================================================
    // 菜单：单 envelope (resource_entity for ADMIN_MENU)
    //
    // v1.4「双轨并行」：sys_menu 仅承载 DIR/MENU 行（菜单可见性轨道），BUTTON 行不再
    // 同步到 permission-center —— 按钮权限改为通过真实资源类型 × 操作码下发，
    // 由前端 hasPerms("ADMIN_ORG:CREATE") 直接校验。
    // 因此本类的 menuUpsert/menuDisable/menuDelete 在 BUTTON 行（menuType="3"）时返回 null，
    // 调用方需 null-skip。
    // =====================================================================

    /** menu_type 字典码：3 = BUTTON。DIR/MENU 走资源同步，BUTTON 不再同步。 */
    private static final String MENU_TYPE_BUTTON = "3";

    /**
     * 菜单创建/更新事件 → resource_entity(ADMIN_MENU) UPSERT。
     * <p>
     * v1.4：BUTTON 行（menuType="3"）返回 null，不再同步到 permission-center。
     *
     * @param menu 菜单快照
     * @return 单条 envelope；BUTTON 行返回 null
     */
    public SyncTaskEnvelope menuUpsert(SysMenu menu) {
        if (isButtonMenu(menu)) {
            return null;
        }
        return buildMenuResource(menu, "UPSERT", "upsert");
    }

    /**
     * 菜单停用事件 → resource_entity(ADMIN_MENU) DISABLE。
     * <p>
     * v1.4：BUTTON 行返回 null。
     *
     * @param menu 菜单快照
     * @return 单条 envelope；BUTTON 行返回 null
     */
    public SyncTaskEnvelope menuDisable(SysMenu menu) {
        if (isButtonMenu(menu)) {
            return null;
        }
        return buildMenuResource(menu, "DISABLE", "disable");
    }

    /**
     * 菜单删除事件 → resource_entity(ADMIN_MENU) DELETE。
     * <p>
     * v1.4：传入 menuType 用于 BUTTON 行短路（避免回查 sys_menu）。
     * 调用方若已知 menuType 应使用 {@link #menuDelete(Long, String, String)} 重载。
     *
     * @param menuId     菜单 ID
     * @param externalId 外部 ID（通常等于 menuId.toString()）
     * @return 单条 envelope
     */
    public SyncTaskEnvelope menuDelete(Long menuId, String externalId) {
        return menuDelete(menuId, externalId, null);
    }

    /**
     * 菜单删除事件（v1.4 重载，提供 menuType 用于 BUTTON 行短路）。
     *
     * @param menuId     菜单 ID
     * @param externalId 外部 ID
     * @param menuType   被删菜单的 menu_type；为 BUTTON 时返回 null（不同步）
     * @return 单条 envelope；BUTTON 行返回 null
     */
    public SyncTaskEnvelope menuDelete(Long menuId, String externalId, String menuType) {
        if (MENU_TYPE_BUTTON.equals(menuType)) {
            return null;
        }
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", "DELETE");
        payloadMap.put("resourceTypeCode", AdminResourceType.MENU);
        payloadMap.put("resourceCode", externalId);
        payloadMap.put("codeType", CODE_TYPE_DEFAULT);
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_menu");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = resourceEntityBusinessKey(AdminResourceType.MENU, externalId, CODE_TYPE_DEFAULT);
        return new SyncTaskEnvelope(
            ACTION_RESOURCE_ENTITY_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("resource_entity:" + AdminResourceType.MENU, externalId, "delete"),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    /** BUTTON 类型菜单不再同步到权限中心（v1.4 双轨并行）。 */
    private static boolean isButtonMenu(SysMenu menu) {
        return menu != null && MENU_TYPE_BUTTON.equals(menu.getMenuType());
    }

    private SyncTaskEnvelope buildMenuResource(SysMenu menu, String operation, String auditOpType) {
        String externalId = String.valueOf(menu.getId());
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("operation", operation);
        payloadMap.put("resourceTypeCode", AdminResourceType.MENU);
        payloadMap.put("resourceCode", externalId);
        payloadMap.put("codeType", CODE_TYPE_DEFAULT);
        payloadMap.put("name", menu.getName());
        payloadMap.put("parentResourceTypeCode", AdminResourceType.MENU);
        payloadMap.put("parentResourceCode",
            menu.getParentId() != null && menu.getParentId() != 0L ? String.valueOf(menu.getParentId()) : null);
        payloadMap.put("status", menu.getStatus() != null ? menu.getStatus() : 1);
        payloadMap.put("sortOrder", menu.getSortOrder());
        payloadMap.put("extra", Map.of(
            "menuType", nullToEmpty(menu.getMenuType()),
            "permCode", nullToEmpty(menu.getPermCode()),
            "path", nullToEmpty(menu.getPath()),
            "component", nullToEmpty(menu.getComponent()),
            "icon", nullToEmpty(menu.getIcon()),
            "visible", menu.getVisible() != null && menu.getVisible()
        ));
        payloadMap.put("sourceService", SOURCE_SERVICE);
        payloadMap.put("sourceEntityType", "sys_menu");
        payloadMap.put("sourceEntityId", externalId);
        payloadMap.put("syncVersion", versionMap());

        String businessKey = resourceEntityBusinessKey(AdminResourceType.MENU, externalId, CODE_TYPE_DEFAULT);
        return new SyncTaskEnvelope(
            ACTION_RESOURCE_ENTITY_SYNC,
            businessKey,
            null,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs("resource_entity:" + AdminResourceType.MENU, externalId, auditOpType),
            LocalDateTime.now(),
            sequenceProvider.next(),
            null,
            null
        );
    }

    // =====================================================================
    // helpers — businessKey 构造（§6.2.2.4 镜像）
    // =====================================================================

    /**
     * 构造 abstract_user businessKey：
     * {@code subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}}
     */
    public static String abstractUserBusinessKey(String subjectTypeCode, String subjectExternalId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("subjectTypeCode", subjectTypeCode);
        map.put("subjectExternalId", subjectExternalId);
        return encode(map);
    }

    /**
     * 构造 abstract_role businessKey：
     * {@code roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}}
     */
    public static String abstractRoleBusinessKey(String roleTypeCode, String roleExternalId) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("roleTypeCode", roleTypeCode);
        map.put("roleExternalId", roleExternalId);
        return encode(map);
    }

    /**
     * 构造 resource_entity businessKey：
     * {@code resourceTypeCode={resourceTypeCode}&resourceCode={resourceCode}&codeType={codeType}}
     */
    public static String resourceEntityBusinessKey(String resourceTypeCode, String resourceCode, String codeType) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("resourceTypeCode", resourceTypeCode);
        map.put("resourceCode", resourceCode);
        map.put("codeType", codeType);
        return encode(map);
    }

    /**
     * 构造 user_role businessKey：
     * {@code subjectTypeCode={...}&subjectExternalId={...}&roleTypeCode={...}&roleExternalId={...}&relationKey={...}}
     * <p>{@code relationKey} 入参为业务原文（如 {@code ORG:2001}），percent-encode 后变为 {@code ORG%3A2001}。</p>
     */
    public static String userRoleBusinessKey(String subjectTypeCode, String subjectExternalId,
                                             String roleTypeCode, String roleExternalId,
                                             String relationKey) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("subjectTypeCode", subjectTypeCode);
        map.put("subjectExternalId", subjectExternalId);
        map.put("roleTypeCode", roleTypeCode);
        map.put("roleExternalId", roleExternalId);
        map.put("relationKey", relationKey);
        return encode(map);
    }

    private static String encode(LinkedHashMap<String, String> map) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : map.entrySet()) {
            if (e.getValue() == null) {
                throw new IllegalArgumentException("businessKey field value must not be null: " + e.getKey());
            }
            if (!first) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(percentEncode(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private static String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // =====================================================================
    // helpers — payload 序列化与展示属性
    // =====================================================================

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "serialize sync envelope payload failed", e);
        }
    }

    private static Map<String, Object> displayAttrs(String entityType, String externalId, String operationType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entityType", entityType);
        m.put("externalId", externalId);
        m.put("operationType", operationType);
        return m;
    }

    private Map<String, Object> versionMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("occurredAt", LocalDateTime.now().toString());
        m.put("sequenceNo", sequenceProvider.next());
        return m;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // =====================================================================
    // 全量校准 envelope 工厂（S6）
    // 每个 phase 一条任务；payload 顶层 scope + items；handler 通过 batchKey 非空走 full-sync 分支。
    // =====================================================================

    /**
     * 主体（abstract_user）全量校准 envelope。
     * <p>scopeKey: {@code subjectTypeCode={subjectTypeCode}}</p>
     */
    public SyncTaskEnvelope userFullSync(Long tenantId, List<SysUser> users, String batchKey, String phase) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceService", SOURCE_SERVICE);
        scope.put("subjectTypeCode", AdminResourceType.USER);

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (users != null) {
            for (SysUser user : users) {
                String externalId = String.valueOf(user.getId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("subjectExternalId", externalId);
                item.put("name", user.getName());
                item.put("enabled", user.getStatus() != null && user.getStatus() == 1);
                item.put("extra", Map.of("username", nullToEmpty(user.getUsername())));
                item.put("sourceEntityType", "sys_user");
                item.put("sourceEntityId", externalId);
                item.put("syncVersion", versionMap());
                items.add(item);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("items", items);

        String businessKey = fullSyncBusinessKey(phase, Map.of(
            "subjectTypeCode", AdminResourceType.USER));
        return buildFullSyncEnvelope(ACTION_ABSTRACT_USER_SYNC, businessKey, batchKey, phase,
            payload, "sys_user_full_sync", items.size());
    }

    /**
     * 用户资源（resource_entity ADMIN_USER）全量校准 envelope。
     * <p>scopeKey: {@code resourceTypeCode=ADMIN_USER}</p>
     */
    public SyncTaskEnvelope userResourceFullSync(Long tenantId, List<SysUser> users, String batchKey, String phase) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceService", SOURCE_SERVICE);
        scope.put("resourceTypeCode", AdminResourceType.USER);

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (users != null) {
            for (SysUser user : users) {
                String externalId = String.valueOf(user.getId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("resourceCode", externalId);
                item.put("codeType", CODE_TYPE_DEFAULT);
                item.put("name", user.getName());
                item.put("status", user.getStatus() != null ? user.getStatus() : 1);
                item.put("sourceEntityType", "sys_user");
                item.put("sourceEntityId", externalId);
                item.put("syncVersion", versionMap());
                items.add(item);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("items", items);

        String businessKey = fullSyncBusinessKey(phase, Map.of(
            "resourceTypeCode", AdminResourceType.USER));
        return buildFullSyncEnvelope(ACTION_RESOURCE_ENTITY_SYNC, businessKey, batchKey, phase,
            payload, "sys_user_resource_full_sync", items.size());
    }

    /**
     * 组织角色（abstract_role）全量校准 envelope。
     * <p>scopeKey: {@code roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}}</p>
     *
     * @param roleTypeCode      ORG / POSITION
     * @param treeRootExternalId 组织树根 externalId
     */
    public SyncTaskEnvelope orgRoleFullSync(Long tenantId, List<SysOrg> orgs, String batchKey, String phase,
                                            String roleTypeCode, String treeRootExternalId) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceService", SOURCE_SERVICE);
        scope.put("roleTypeCode", roleTypeCode);
        scope.put("treeRootExternalId", treeRootExternalId);

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (orgs != null) {
            for (SysOrg org : orgs) {
                String externalId = String.valueOf(org.getId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("roleExternalId", externalId);
                item.put("name", org.getName());
                item.put("parentRoleExternalId",
                    org.getParentId() != null && org.getParentId() != 0L ? String.valueOf(org.getParentId()) : null);
                item.put("status", org.getStatus() != null ? org.getStatus() : 1);
                item.put("sortOrder", org.getSortOrder());
                item.put("extra", Map.of("orgType", nullToEmpty(org.getOrgType())));
                item.put("sourceEntityType", "sys_org");
                item.put("sourceEntityId", externalId);
                item.put("syncVersion", versionMap());
                items.add(item);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("items", items);

        LinkedHashMap<String, String> scopeFields = new LinkedHashMap<>();
        scopeFields.put("roleTypeCode", roleTypeCode);
        scopeFields.put("treeRootExternalId", nullToEmpty(treeRootExternalId));
        String businessKey = fullSyncBusinessKey(phase, scopeFields);
        return buildFullSyncEnvelope(ACTION_ABSTRACT_ROLE_SYNC, businessKey, batchKey, phase,
            payload, "sys_org_role_full_sync", items.size());
    }

    /**
     * 组织资源（resource_entity ADMIN_ORG）全量校准 envelope。
     * <p>scopeKey: {@code resourceTypeCode=ADMIN_ORG}</p>
     */
    public SyncTaskEnvelope orgResourceFullSync(Long tenantId, List<SysOrg> orgs, String batchKey, String phase) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceService", SOURCE_SERVICE);
        scope.put("resourceTypeCode", AdminResourceType.ORG);

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (orgs != null) {
            for (SysOrg org : orgs) {
                String externalId = String.valueOf(org.getId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("resourceCode", externalId);
                item.put("codeType", CODE_TYPE_DEFAULT);
                item.put("name", org.getName());
                item.put("parentResourceTypeCode", AdminResourceType.ORG);
                item.put("parentResourceCode",
                    org.getParentId() != null && org.getParentId() != 0L ? String.valueOf(org.getParentId()) : null);
                item.put("parentCodeType", CODE_TYPE_DEFAULT);
                item.put("status", org.getStatus() != null ? org.getStatus() : 1);
                item.put("sortOrder", org.getSortOrder());
                item.put("extra", Map.of("orgType", nullToEmpty(org.getOrgType())));
                item.put("sourceEntityType", "sys_org");
                item.put("sourceEntityId", externalId);
                item.put("syncVersion", versionMap());
                items.add(item);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("items", items);

        String businessKey = fullSyncBusinessKey(phase, Map.of(
            "resourceTypeCode", AdminResourceType.ORG));
        return buildFullSyncEnvelope(ACTION_RESOURCE_ENTITY_SYNC, businessKey, batchKey, phase,
            payload, "sys_org_resource_full_sync", items.size());
    }

    /**
     * 用户-组织关系（user_role）全量校准 envelope。
     * <p>scopeKey: {@code sourceType=SYS_USER_ORG&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}}</p>
     * <p>
     * 调用方负责按 (treeRoot, roleTypeCode) 分桶；item 的 roleTypeCode 直接来自方法参数，
     * 不再做"未知 orgType → ORG"的二级 fallback 推断。
     * </p>
     *
     * @param roleTypeCode       桶维度 — ORG 或 POSITION（fail-fast 校验）
     * @param treeRootExternalId 桶维度 — 组织树根 externalId
     */
    public SyncTaskEnvelope userOrgFullSync(Long tenantId,
                                            List<cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg> bindings,
                                            String batchKey, String phase,
                                            String roleTypeCode, String treeRootExternalId) {
        // fail-fast：roleTypeCode 必须 in (ORG, POSITION)
        if (!"ORG".equals(roleTypeCode) && !"POSITION".equals(roleTypeCode)) {
            throw new IllegalArgumentException(
                "userOrgFullSync: roleTypeCode must be ORG or POSITION but was " + roleTypeCode);
        }
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceService", SOURCE_SERVICE);
        scope.put("sourceType", "SYS_USER_ORG");
        scope.put("roleTypeCode", roleTypeCode);
        scope.put("treeRootExternalId", treeRootExternalId);

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (bindings != null) {
            for (cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg b : bindings) {
                String userExternalId = String.valueOf(b.getUserId());
                String orgExternalId = String.valueOf(b.getOrgId());
                // 调用方已按桶分组：item.roleTypeCode 直接信任 scope.roleTypeCode，不再二级推断
                String relationKey = UserOrgKeys.relationKey(orgExternalId);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("subjectTypeCode", AdminResourceType.USER);
                item.put("subjectExternalId", userExternalId);
                item.put("roleTypeCode", roleTypeCode);
                item.put("roleExternalId", orgExternalId);
                item.put("relationKey", relationKey);
                item.put("validFrom", null);
                item.put("validTo", null);
                item.put("sourceEntityType", "sys_user_org");
                item.put("sourceEntityId", userExternalId + ":" + orgExternalId);
                item.put("syncVersion", versionMap());
                items.add(item);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("items", items);

        LinkedHashMap<String, String> scopeFields = new LinkedHashMap<>();
        scopeFields.put("sourceType", "SYS_USER_ORG");
        scopeFields.put("roleTypeCode", roleTypeCode);
        scopeFields.put("treeRootExternalId", nullToEmpty(treeRootExternalId));
        String businessKey = fullSyncBusinessKey(phase, scopeFields);
        return buildFullSyncEnvelope(ACTION_USER_ROLE_SYNC, businessKey, batchKey, phase,
            payload, "sys_user_org_full_sync", items.size());
    }

    /**
     * 菜单（resource_entity ADMIN_MENU）全量校准 envelope。
     * <p>scopeKey: {@code resourceTypeCode=ADMIN_MENU}</p>
     */
    public SyncTaskEnvelope menuFullSync(Long tenantId, List<SysMenu> menus, String batchKey, String phase) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceService", SOURCE_SERVICE);
        scope.put("resourceTypeCode", AdminResourceType.MENU);

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        if (menus != null) {
            for (SysMenu menu : menus) {
                String externalId = String.valueOf(menu.getId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("resourceCode", externalId);
                item.put("codeType", CODE_TYPE_DEFAULT);
                item.put("name", menu.getName());
                item.put("parentResourceTypeCode", AdminResourceType.MENU);
                item.put("parentResourceCode",
                    menu.getParentId() != null && menu.getParentId() != 0L ? String.valueOf(menu.getParentId()) : null);
                item.put("parentCodeType", CODE_TYPE_DEFAULT);
                item.put("status", menu.getStatus() != null ? menu.getStatus() : 1);
                item.put("sortOrder", menu.getSortOrder());
                item.put("extra", Map.of(
                    "menuType", nullToEmpty(menu.getMenuType()),
                    "permCode", nullToEmpty(menu.getPermCode()),
                    "path", nullToEmpty(menu.getPath()),
                    "component", nullToEmpty(menu.getComponent()),
                    "icon", nullToEmpty(menu.getIcon()),
                    "visible", menu.getVisible() != null && menu.getVisible()
                ));
                item.put("sourceEntityType", "sys_menu");
                item.put("sourceEntityId", externalId);
                item.put("syncVersion", versionMap());
                items.add(item);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("items", items);

        String businessKey = fullSyncBusinessKey(phase, Map.of(
            "resourceTypeCode", AdminResourceType.MENU));
        return buildFullSyncEnvelope(ACTION_RESOURCE_ENTITY_SYNC, businessKey, batchKey, phase,
            payload, "sys_menu_full_sync", items.size());
    }

    /**
     * 共用 envelope 构造器（full-sync 分支）。
     */
    private SyncTaskEnvelope buildFullSyncEnvelope(String syncAction, String businessKey,
                                                   String batchKey, String phase,
                                                   Map<String, Object> payloadMap,
                                                   String entityType, int itemCount) {
        Map<String, Object> displayAttrs = new LinkedHashMap<>();
        displayAttrs.put("entityType", entityType);
        displayAttrs.put("operationType", "FULL_SYNC");
        displayAttrs.put("itemCount", itemCount);
        if (phase != null) {
            displayAttrs.put("phase", phase);
        }
        return new SyncTaskEnvelope(
            syncAction,
            businessKey,
            batchKey,
            toJson(payloadMap),
            PAYLOAD_VERSION,
            displayAttrs,
            LocalDateTime.now(),
            sequenceProvider.next(),
            phase,
            null
        );
    }

    /**
     * 构造 full-sync 任务的 businessKey：
     * {@code phase={phase}&{scopeFields}}（按字段插入顺序，URL percent-encoding）。
     */
    private static String fullSyncBusinessKey(String phase, Map<String, String> scopeFields) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("phase", phase == null ? "" : phase);
        if (scopeFields != null) {
            map.putAll(scopeFields);
        }
        return encode(map);
    }
}
