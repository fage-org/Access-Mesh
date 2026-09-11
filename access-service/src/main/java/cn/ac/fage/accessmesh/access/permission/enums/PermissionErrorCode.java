package cn.ac.fage.accessmesh.access.permission.enums;

import lombok.Getter;

/**
 * Permission Center 错误码枚举
 * <p>
 * 定义 access-service 模块业务错误码，范围 20001-29999。
 * 当前先补齐权限授予相关错误码，后续按模块逐步扩展。
 * </p>
 */
@Getter
public enum PermissionErrorCode {

    /**
     * 角色不存在
     */
    ROLE_NOT_FOUND(20001, "角色不存在"),

    /**
     * 授权请求不能为空
     */
    GRANT_REQUEST_EMPTY(20002, "授权请求不能为空"),

    /**
     * 角色已禁用
     */
    ROLE_DISABLED(20003, "角色已禁用"),

    /**
     * 资源不存在
     */
    RESOURCE_NOT_FOUND(20004, "资源不存在"),

    /**
     * 操作权限不存在
     */
    OPERATION_NOT_FOUND(20005, "操作权限不存在"),

    /**
     * 权限条件不存在
     */
    CONDITION_NOT_FOUND(20006, "权限条件不存在"),

    /**
     * 资源类型不存在
     */
    RESOURCE_TYPE_NOT_FOUND(20007, "资源类型不存在"),

    /**
     * 资源类型与操作权限不匹配
     */
    RESOURCE_TYPE_OPERATION_MISMATCH(20008, "资源类型与操作权限不匹配"),

    /**
     * 父权限不存在
     */
    PARENT_PERMISSION_NOT_FOUND(20009, "父权限不存在"),

    /**
     * 父权限不是顶层权限
     */
    PARENT_PERMISSION_NOT_TOP_LEVEL(20010, "父权限必须是顶层权限"),

    /**
     * 子权限资源类型不被允许
     */
    SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED(20011, "子权限资源类型不在允许范围内"),

    /**
     * scopeMode=INSTANCE 时缺少 resourceCode
     */
    RESOURCE_CODE_REQUIRED(20012, "scopeMode=INSTANCE 时 resourceCode 不能为空"),

    /**
     * 子权限不存在
     */
    CHILD_PERMISSION_NOT_FOUND(20013, "子权限不存在"),

    /**
     * 权限不是子权限
     */
    PERMISSION_NOT_CHILD(20014, "权限不是子权限"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(20015, "用户不存在"),

    /**
     * 用户已存在
     */
    USER_ALREADY_EXISTS(20016, "用户已存在"),

    /**
     * 业务域不存在
     */
    DOMAIN_NOT_FOUND(20017, "业务域不存在"),

    /**
     * 域配置不存在
     */
    DOMAIN_CONFIG_NOT_FOUND(20018, "域配置不存在"),

    /**
     * 资源依赖不存在
     */
    DEPENDENCY_NOT_FOUND(20019, "资源依赖不存在"),

    /**
     * 冲突规则不存在
     */
    CONFLICT_RULE_NOT_FOUND(20020, "冲突规则不存在"),

    /**
     * 类型编码不存在
     */
    TYPE_CODE_NOT_FOUND(20021, "类型编码不存在"),

    /**
     * 角色类型不匹配
     */
    ROLE_TYPE_MISMATCH(20022, "角色类型不匹配"),

    /**
     * 请求参数不能为空
     */
    REQUEST_ITEMS_EMPTY(20023, "请求参数不能为空"),

    /**
     * 同步资源不存在
     */
    SYNC_RESOURCE_NOT_FOUND(20024, "同步资源不存在"),

    /**
     * 资源状态冲突
     */
    RESOURCE_STATE_CONFLICT(20025, "资源状态冲突"),

    /**
     * 系统初始化失败
     */
    SYSTEM_INIT_FAILED(20026, "系统初始化失败"),

    /**
     * 参数校验失败
     */
    VALIDATION_FAILED(20027, "参数校验失败"),

    /**
     * 类型定义不存在
     */
    TYPE_DEFINITION_NOT_FOUND(20028, "类型定义不存在"),

    /**
     * 用户角色关系不存在
     */
    USER_ROLE_RELATION_NOT_FOUND(20029, "用户角色关系不存在"),

    /**
     * 同步元数据 targetStatus 非法
     */
    SYNC_TARGET_STATUS_INVALID(20030, "同步 targetStatus 不在 entityKind 允许范围内"),

    /**
     * 条件规则不可下发 Gateway 评估
     * <p>
     * gatewayEvaluable=true 时，conditionRules.items[].type 必须全部在
     * {@code ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES} 白名单内。
     * </p>
     */
    CONDITION_RULES_INVALID(20031, "条件规则不可下发 Gateway 评估"),

    /**
     * 等价冲突规则已存在
     */
    CONFLICT_RULE_DUPLICATE(20032, "等价冲突规则已存在"),

    /**
     * 同一角色的同一资源、操作、范围及父权限下已存在 MANUAL 直接授权
     */
    DIRECT_PERMISSION_CONFLICT(20033, "同一资源与操作已存在直接授权"),

    /**
     * 自动补全授权记录只读
     */
    AUTO_DEP_READONLY(20034, "自动补全授权记录只读"),

    /**
     * 权限记录不存在、已变化或不属于目标角色
     */
    PERMISSION_NOT_FOUND(20036, "权限记录不存在或已发生变化"),

    /**
     * 操作者不具备授权传递能力
     */
    GRANT_CANNOT_DELEGATE(20040, "当前操作者无权转授该权限"),

    /**
     * 条件权限不可设置为可转授
     */
    CONDITIONAL_PERMISSION_CANNOT_DELEGATE(20041, "条件权限不可转授"),

    /**
     * 条件启用状态不变量（2026-08-08 产品确认，api-contract §6.5.1，T-PERM-041）：
     * 主权限 conditionCode 新写入或变更时目标条件必须 enabled=true——停用条件不得
     * 新建绑定或改绑；存量绑定（update 未变更 conditionCode，含同 id 重写）允许保留。
     * 仅判主权限（子权限带条件由 20043 先行拒绝）。
     */
    CONDITION_DISABLED(20042, "权限条件已停用"),

    /**
     * 子权限属性系统不变量（2026-08-08 复审产品确认，api-contract §6.5.1）：
     * 子权限不承载条件与再授予——create 的 conditionCode 非 null / canGrant 非 false、
     * 或 update 目标为子权限，一律拒绝（先于主权限 20041/20042 判定）。
     */
    SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED(20043, "子权限不承载条件与再授予（系统不变量）"),

    /**
     * 禁止通过权限管理入口或外部同步直接修改 access-service 本地投影。
     * <p>
     * 编号 20045/20046 段：20042 已由 CONDITION_DISABLED 承载（api-contract §6.5.1，
     * T-PERM-041 落地），统一响应只暴露数字码，同一编号不得承载两种业务含义。
     * </p>
     */
    LOCAL_PROJECTION_IMMUTABLE(20045, "禁止直接修改 access-service 本地权限投影"),

    /**
     * 本地投影依赖缺失（父组织角色/所属组织角色等不存在），fail-closed 整体回滚。
     */
    LOCAL_PROJECTION_DEPENDENCY_MISSING(20046, "本地投影依赖缺失（父组织角色/所属组织角色不存在）"),
    INVALID_PARAM(20044, "参数格式错误"),

    /**
     * 配置键必须使用 admin./permission./access. 命名空间前缀（T-ACCESS-007 §5.2）。
     */
    CONFIG_KEY_NAMESPACE_INVALID(20047, "配置键必须使用 admin./permission./access. 命名空间前缀"),

    /**
     * autoGrant=true 不支持：自动授权未实现（T-PERM-035 暂缓，design-review §11 E4），
     * resource_dependency.auto_grant 为预留字段，实现前所有写入口仅接受 false（2026-08-27 设计定案）。
     */
    AUTO_GRANT_NOT_SUPPORTED(20048, "autoGrant=true 不支持：自动授权未实现（预留字段），仅接受 false"),

    /**
     * 类型编码在 tenant+typeKey 内已存在（uk_type_definition_code）。
     */
    TYPE_DEFINITION_CODE_DUPLICATE(20049, "类型编码已存在（tenant+typeKey 内唯一）"),

    /**
     * 父角色非法：不能是被移动角色自身或其子孙（parent 链成环后环节点从树构建中静默消失、
     * 祖先/子孙查询语义受损；对齐 admin 域同场景先例 ORG_PARENT_CYCLE/MENU_PARENT_INVALID）。
     */
    ROLE_PARENT_INVALID(20050, "父角色不能是自身或该角色的子孙"),

    /**
     * 业务域删除冲突：目标域为全局域（global=true，每租户唯一，范围=CLASSIFY 声明或动态补集），
     * 或域下仍存在有效域配置（domain_config 引用检查拒删，schema 表注释约定）。
     * 同一删除被拒语义承载两类原因，message 区分具体原因（T-PERM-026 设计定案）。
     */
    DOMAIN_DELETE_CONFLICT(20051, "业务域不可删除：全局域或域下存在域配置"),

    /**
     * 业务域编码在租户内已存在（uk_biz_domain，软删行不占用）。
     */
    DOMAIN_CODE_DUPLICATE(20052, "业务域编码已存在（租户内唯一）"),

    /**
     * 资源移动目标父非法：跨资源类型，或是被移动资源自身/其子孙（parent 链成环后树构建不收敛，
     * 对齐 ROLE_PARENT_INVALID / DOMAIN_DELETE_CONFLICT 先例：同一码承载两类原因，message 区分）。
     */
    RESOURCE_PARENT_INVALID(20053, "目标父资源非法：跨资源类型或为自身/子孙节点"),

    /**
     * 等价依赖规则已存在（uk_resource_dependency：tenant + 源资源 + 目标资源 + COALESCE(source_operation_bits,0)）。
     * 业务层预查命中返回（对齐 CONFLICT_RULE_DUPLICATE 先例），并发窗口由 DB 唯一索引兜底转同码。
     */
    DEPENDENCY_DUPLICATE(20054, "等价依赖规则已存在（同源/目标资源对 + 同触发操作位）"),

    /**
     * 资源由外部来源维护（类型级所有权，T-PERM-052 定案 2026-09-05）：目标资源类型声明为
     * SYNC（extra.managedMode=SYNC）时，管理面 create/update/move/remove（含级联删除的后代全集）
     * 一律拒绝——资源事实归声明来源服务维护，请到来源系统操作。读路径不受限。
     */
    RESOURCE_EXTERNALLY_MAINTAINED(20055, "资源由外部来源维护，请到来源系统操作"),

    /**
     * 类型所有权声明不可变更（T-PERM-052）：三种冲突面共用——①系统预置类型（is_system）
     * 所有权声明钉死（空类型翻转后事实链路照旧写入即双 writer，codex 二轮复评定案）；
     * ②自定义类型下仍存在有效资源行时 extra.managedMode/syncSourceService 有效值变更
     * （含删除键隐式切回 MANAGED）被拒绝（无有效行才可改——防止人工行切成 SYNC 变只读
     * 孤岛、SYNC 行切成 MANAGED 被管理面误删）；③类型下存在有效资源行时类型删除被拒绝
     * （软删类型会让其行成永久孤儿）。读路径不受限。
     * T-PERM-056（2026-09-09 用户定案删除保护）：③扩展至主体类型——user_type/role_type
     * 下存在有效用户/角色行时类型删除同拒绝（用户/角色是业务主体数据不级联）。
     */
    TYPE_OWNERSHIP_CHANGE_CONFLICT(20056, "类型所有权声明不可变更（系统预置类型钉死，或类型下存在有效引用行——资源行/用户行/角色行）"),

    /**
     * 全局域已存在（uk_biz_domain_global：每租户至多一个 global=true 有效域，T-PERM-046）。
     * 预查命中返回；并发创建窗口由唯一索引兜底同映射。
     */
    DOMAIN_GLOBAL_EXISTS(20057, "全局域已存在（每租户仅一个）"),

    /**
     * 域配置并发保存冲突（uk_domain_config：tenant+biz_domain+configType 有效行唯一，T-PERM-046）。
     * save 的 check-then-insert 并发窗口由唯一索引兜底转本码，提示重试（后到者重试即转为 update）。
     */
    DOMAIN_CONFIG_CONCURRENT_CONFLICT(20058, "域配置并发冲突，请重试"),

    /**
     * 条件被授权引用不可删除（T-PERM-048 定案③，2026-09-11 引用守卫）：两类引用任一命中即整批拒绝——
     * ① role_resource_permission.condition_id 挂靠引用（挂该条件的授权行评估将 fail-close 拒绝，
     * 静默删除会让授权「静默失效」）；② 投影行下实例授权引用（CONDITION:UPDATE/DELETE@code 等实例级
     * 授权行，删除后悬空）。零引用才放行删除（对齐 T-PERM-056 删除保护先例，弃级联软删——
     * 授权资产被删条件连带消失比要求显式解绑更危险）。message 携带冲突条件 code 清单。
     */
    CONDITION_REFERENCED_BY_GRANTS(20059, "条件被授权引用，不可删除：请先解绑/换条件（零引用才可删）"),

    /**
     * 内联条件不可管理面管理（T-PERM-048 双轨制，2026-09-11 定案①）：三面共用——
     * ①权限条件页 update/remove 遇 source=INLINE 行拒绝（内联条件只能在授权页随记录更改，
     * 管理页查不到也不能管理）；②管理面 list/detail 读面拒绝（list 默认只回 MANAGED）；
     * ③apply-grant-plan 的 conditionCode 引用轨遇 INLINE 行拒绝（内联条件 1:1 属于创建它的
     * 授权记录，不可被显式 code 引用或共享——引用轨值域=MANAGED）。
     */
    CONDITION_INLINE_NOT_MANAGEABLE(20060, "内联条件不可在管理面管理/不可被显式引用（只能在授权页随记录更改）");

    private final int code;
    private final String message;

    PermissionErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
