package cn.ac.fage.accessmesh.access.permission.service.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理事实对应的本地权限投影。
 * <p>
 * 只写权限计算表，不写 sync_metadata。owner_service_code 固定为 access-service。
 * 事务由调用方 AppService 声明（access.application 用户/组织/菜单编排与
 * permission 域角色/主体管理编排，T-ACCESS-019）。
 * </p>
 */
public interface LocalProjectionDomainService {

    /**
     * 创建本地用户主体链（T-ORG-001，architecture §12.2）：预取主体 ID N 后显式写
     * abstract_user(id=N, external_id=N, LOCAL_USER) + resource_entity(USER, code=N)。
     * <p>
     * 调用方（access.application 用户创建编排）随后以同一 N 写 sys_user(id=N)——
     * 本方法只负责主体侧，不写 admin 域事实。与外部主体（仅 abstract_user 自增取号）
     * 共用同一 ID 生成源，任何创建顺序均不碰撞。
     * </p>
     *
     * @return 预取并落库的主体 ID（= 调用方 sys_user.id）
     */
    Long createLocalUserSubject(Long tenantId, String name, boolean enabled, String extraJson);

    /**
     * UPSERT abstract_user(LOCAL_USER) + resource_entity(USER)。
     *
     * @return abstract_user.id
     */
    Long upsertAdminUser(Long tenantId, Long sysUserId, String name, boolean enabled, String extraJson);

    /**
     * 停用 abstract_user 与 USER 资源。
     */
    void disableAdminUser(Long tenantId, Long sysUserId);

    /**
     * 软删除 abstract_user 与 USER 资源。
     */
    void deleteAdminUser(Long tenantId, Long sysUserId);

    /**
     * UPSERT abstract_role(ORG|POSITION) + resource_entity(ORG)。
     *
     * @param parentOrgType 父节点实际 orgType（POSITION 子节点的父通常为 ORG， 修复：
     *                      按父节点实际类型解析父角色，避免 POSITION 子节点查 ORG 父角色失败；
     *                      null 时回退用子节点 roleType，兼容历史调用）
     * @return abstract_role.id
     */
    Long upsertAdminOrg(Long tenantId, Long sysOrgId, String orgType, String name,
                        Long parentOrgId, String parentOrgType, Integer status, Integer sortOrder, String extraJson);

    /**
     * 软删除组织角色与 ORG 资源。
     */
    void deleteAdminOrg(Long tenantId, Long sysOrgId, String orgType);

    /**
     * UPSERT resource_entity(MENU)。DIR/MENU/EXTERNAL/IFRAME/HIDDEN 五值
     * 全量维护（v3.5 无 BUTTON 短路，T-ACCESS-015）。
     *
     * @return resource_entity.id
     */
    Long upsertAdminMenu(Long tenantId, Long sysMenuId, String name, Long parentMenuId,
                         Integer status, Integer sortOrder);

    /**
     * 软删除 MENU 资源。
     */
    void deleteAdminMenu(Long tenantId, Long sysMenuId);

    /**
     * 登记 ADMIN_FILE 文件夹资源投影（T-ADMIN-025，insert-if-absent 语义）。
     * <p>
     * 文件夹 = {@code sys_file.bucket_name} = {@code resource_entity(ADMIN_FILE).code}，
     * 单事实源为投影表（无文件夹管理界面）。两条产出链：bootstrap 预置
     * default/avatar/document/image 四文件夹（{@code name} 传展示标签）与上传新
     * bizType 惰性登记（首次出现即成为可授权实例，{@code name} 传 code 本身）。
     * 有效行已存在时<b>不回写</b>（名称以首建为准，避免惰性登记用 code 覆盖预置标签），
     * 返回既有行 id；软删墓碑不复活（部分唯一索引允许重新插入新行）。
     * 类型所有权：ADMIN_FILE 种子声明 SYNC+access-service（T-PERM-052 口径），
     * 本投影与 bootstrap 预置是该类型唯一合法 writer。
     * </p>
     *
     * @param folderCode 文件夹编码（= 上传 bizType，调用方保证格式白名单）
     * @param name      展示名（仅首次创建生效）
     * @return resource_entity.id
     */
    Long ensureAdminFileFolder(Long tenantId, String folderCode, String name);

    /**
     * BIND sys_user_org 对应的 user_role。
     *
     * @param relationSysOrgId POSITION 时为其所属组织 sys_org.id（ 修复：
     *                         relation 指向岗位所属组织角色，不再回退到岗位角色自身）；
     *                         ORG 或 null 时沿用自身语义
     * @return user_role.id（投影主键，供变更日志 entityId 使用）
     */
    Long bindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode, Long relationSysOrgId);

    /**
     * UNBIND sys_user_org 对应的 user_role。
     *
     * @param relationSysOrgId 同 {@link #bindUserOrg} 的 relation 语义
     * @return user_role.id（软删前取得）；用户/角色投影缺失抛 USER_ROLE_RELATION_NOT_FOUND
     *         （fail-closed，与 bind 对称），仅依赖投影完整但目标三元组不存在时返回 null（幂等 no-op）
     */
    Long unbindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode, Long relationSysOrgId);

    /**
     * 批量 BIND（批量成员分配不再循环单条 bindUserOrg 的 N+1）。
     * 一次批量加载 abstract_user / abstract_role / relationRole / user_role 候选，
     * 按 (sysUserId, sysOrgId, roleTypeCode, relationSysOrgId) 匹配后批量 insert/update。
     * 任一 key 缺少 abstract_user / abstract_role / POSITION 所属组织角色投影 → 抛 BizException
     * （强事务投影 fail-closed，整体回滚）。
     *
     * @return key → user_role.id（同批重复三元组的后续 key 为 null——JDBC batch 无法回填，
     *         新插入行经一次批量回查取得；同批重复仅首个 key 有 id）
     */
    Map<UserOrgBindKey, Long> batchBindUserOrg(Long tenantId, List<UserOrgBindKey> keys);

    /**
     * POSITION 移动后迁移成员 user_role.relation_id（旧所属组织角色 → 新所属组织角色）。
     * <p>
     * 岗位在同一树内移动后，已有成员的 relation 仍指向旧组织，
     * 后续解绑按新三元组匹配不到旧记录导致投影残留；同一事务内批量迁移并返回受影响用户。
     * </p>
     *
     * @return 受影响 abstract_user.id 集合（供缓存失效）
     */
    Set<Long> migratePositionRelation(Long tenantId, Long sysPositionId,
                                      Long oldRelationOrgId, Long newRelationOrgId);

    /**
     * 批量软删 abstract_user 与 USER 资源（按 sys_user.id 定位）。
     * 一次批量加载 + 一次批量软删，消除删除路径循环单条 deleteAdminUser 的 N+1。
     */
    void batchDeleteAdminUsers(Long tenantId, Set<Long> sysUserIds);

    /**
     * 批量停用 abstract_user 与 USER 资源（按 sys_user.id 定位）。
     * 批量加载 + 批量状态更新（2 条 SQL），消除启停路径循环单条 disable 的 N+1。
     */
    void batchDisableAdminUsers(Long tenantId, Set<Long> sysUserIds);

    /**
     * 批量 UPSERT abstract_user(LOCAL_USER) + resource_entity(USER)。
     * 批量加载已有投影，新行 insertBatch（插入后批量回查主键），已有行更新；
     * 消除启用路径循环单条 upsert 的 N+1。
     *
     * @return sysUserId → abstractUserId 映射（供缓存失效与变更日志）
     */
    Map<Long, Long> batchUpsertAdminUsers(Long tenantId, List<UpsertUserKey> keys);

    /**
     * 批量 upsert 键：管理事实侧 (sys_user.id, name, enabled, extraJson)。
     */
    record UpsertUserKey(Long sysUserId, String name, boolean enabled, String extraJson) {}

    /**
     * 批量 UNBIND（删除路径循环单条 unbind 的 N+1）。
     * 一次批量加载 abstract_user / abstract_role / relationRole / user_role，
     * 按 (sysUserId, sysOrgId, roleTypeCode, relationSysOrgId) 内存匹配后批量软删。
     */
    void batchUnbindUserOrg(Long tenantId, List<UserOrgBindKey> keys);

    /**
     * 批量解绑/绑定键：管理事实侧 (sys_user.id, sys_org.id, 角色类型码, relation 所属组织 id)。
     */
    record UserOrgBindKey(Long sysUserId, Long sysOrgId, String roleTypeCode, Long relationSysOrgId) {}

    /**
     * 按 sys_user.id 定位 abstract_user.id，供缓存失效使用。
     */
    Long findAdminUserId(Long tenantId, Long sysUserId);

    /**
     * 批量按 sys_user.id 定位 abstract_user.id（消除删除/启停路径循环 find 的 N+1）。
     *
     * @return sysUserId → abstractUserId 映射（无投影的 sysUserId 不在结果中）
     */
    Map<Long, Long> batchFindAdminUserIds(Long tenantId, Set<Long> sysUserIds);

    /**
     * 按 sys_org.id 定位 abstract_role.id，供缓存失效使用。
     */
    Long findAdminOrgRoleId(Long tenantId, Long sysOrgId, String orgType);

    /**
     * 按 sys_menu.id 定位 resource_entity.id（MENU 投影主键），供变更日志 entityId 使用。
     */
    Long findAdminMenuResourceId(Long tenantId, Long sysMenuId);

    /**
     * UPSERT resource_entity(ROLE)——permission 域功能角色管理写路径投影（T-ACCESS-019）。
     * <p>
     * abstract_role 事实由调用方（RoleManageAppService 编排）维护，本方法只写资源投影：
     * code = roleId.toString()（architecture §12.3）。parent 镜像角色树，父角色资源投影缺失
     * 抛 LOCAL_PROJECTION_DEPENDENCY_MISSING 整体回滚（fail-closed，2026-08-23 用户决策）。
     * </p>
     */
    void upsertRoleResource(Long tenantId, Long roleId, String name, Integer status, Long parentRoleId);

    /**
     * 批量软删 resource_entity(ROLE)（按 roleId 定位；仅 owner=access-service 行，
     * 外部行跳过不阻断——本地生命周期不触碰外部同步资源）。
     */
    void softDeleteRoleResources(Long tenantId, Set<Long> roleIds);

    /**
     * UPSERT resource_entity(USER)——permission 域主体管理写路径投影（T-ACCESS-019）。
     * <p>
     * abstract_user 事实由调用方（UserManageAppService 编排）维护，本方法只写资源投影：
     * code = subjectId.toString()（architecture §12.3）。LOCAL_USER 主体的 USER 投影归
     * admin 域写链路（{@link #upsertAdminUser}），本方法仅供外部主体管理入口使用。
     * </p>
     */
    void upsertUserResource(Long tenantId, Long subjectId, String name, boolean enabled);

    /**
     * 批量软删 resource_entity(USER)（按 subjectId 定位；仅 owner=access-service 行，
     * 外部行跳过不阻断）。
     */
    void softDeleteUserResources(Long tenantId, Set<Long> subjectIds);

    /**
     * UPSERT resource_entity(TYPE_DEFINITION)——type-definition 写路径投影（T-PERM-051）。
     * <p>
     * 类型定义事实由调用方（TypeDefinitionAppService 编排）维护，本方法只写资源投影：
     * code = {@code {typeKey}:{typeCode}} 复合业务键（typeCode 仅 tenant+type_key 内唯一，
     * 种子 user_type 与 resource_type 均有 USER/SERVICE 同名行，裸 code 跨族撞
     * uk_resource_entity；格式经 BusinessKeys.typeInstanceBusinessKey 构造，2026-09-05 定案）。
     * 无树形语义：parent 恒 null、status 恒启用，name 随类型定义名称同步。
     * </p>
     */
    void upsertTypeDefinitionResource(Long tenantId, String typeKey, String typeCode, String name);

    /**
     * 按复合业务键批量定位 TYPE_DEFINITION 投影行 id（类型删除路径级联处置授权行用）。
     * 一次批量查询；限定 code_type=default 与 {@link #upsertTypeDefinitionResource} 定位对称。
     */
    List<Long> findTypeDefinitionResourceIds(Long tenantId, Set<String> compositeKeys);

    /**
     * TYPE_DEFINITION 实例投影自愈补种（T-PERM-051，bootstrap 启动调用，幂等可重跑）：
     * 为全部有效类型定义行中缺少投影的行 insert-if-absent（2026-09-07 用户定案：启动自愈
     * 对齐 ADMIN_FILE 文件夹投影先例 T-ADMIN-025，部署即生效免手工）。
     * <p>
     * 覆盖特性上线前已存在的存量种子行；软删类型行不在有效行内不补种，软删投影墓碑
     * 不复活（部分唯一索引允许重插新行）。仅覆盖 bootstrap 租户（tenant 1，现网唯一），
     * 其余租户/异常场景由 rebuild-runbook 订正语句兜底。
     * </p>
     *
     * @return 本次补种行数（0=已齐备）
     */
    int backfillTypeDefinitionProjections(Long tenantId);

    /**
     * UPSERT resource_entity(CONDITION)——管理页条件写路径投影（T-PERM-048）。
     * <p>
     * 条件事实由调用方（ConditionAppService 编排）维护，本方法只写资源投影：
     * code = 条件 code（租户内唯一，直传无需复合键，architecture §12.3 口径）。
     * 无树形语义：parent 恒 null；status 镜像条件 enabled（停用条件自动隐出
     * 授权资源树 selectResourceTree status=1 过滤）。仅 MANAGED 来源条件调用
     * （INLINE 内联条件不投影——无资源身份消费者，2026-09-11 定案⑤）。
     * </p>
     */
    void upsertConditionResource(Long tenantId, String code, String name, boolean enabled);

    /**
     * 批量软删 resource_entity(CONDITION)（按条件 code 定位；调用方删除条件行后同事务调用，
     * 仅 code_type=default 与 upsert 定位对称）。
     */
    void softDeleteConditionResources(Long tenantId, Set<String> codes);

    /**
     * CONDITION 实例投影自愈补种（T-PERM-048，bootstrap 启动调用，幂等可重跑，对齐
     * {@link #backfillTypeDefinitionProjections} 先例）：为全部有效 MANAGED 条件行中
     * 缺少投影的行 insert-if-absent。INLINE 条件不投影（定案⑤）。
     * <p>
     * 同款豁免口径——缺失只可能是库先于本特性存在（存量条件行无写路径联动可补），
     * 无运营意图可保护。附带野行告警：CONDITION 类型下 code 不匹配任何有效 MANAGED
     * 条件的存量资源行（特性上线前管理面手工可建，现为 SYNC 族 20055 只读僵尸）WARN 日志，
     * 清理语句见 rebuild-runbook FAQ。
     * </p>
     *
     * @return 本次补种行数（0=已齐备）
     */
    int backfillConditionProjections(Long tenantId);
}
