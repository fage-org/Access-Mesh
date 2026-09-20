package cn.ac.fage.accessmesh.access.infrastructure.enums;

import lombok.Getter;

/**
 * access-service 业务错误码册（单一错误码面）
 * <p>
 * T-ACCESS-038 合类不合号（2026-09-13）：原 AdminErrorCode / PermissionErrorCode 两枚举
 * 合一为本册，{@code 1xxxx}、{@code 2xxxx} 编号段原值保留、零重排（architecture §9
 * 分段与不重编号字面维持）。同名异号碰撞三组以段前缀消解（编号对与各自调用方语义
 * 原样保留）：ADMIN_USER_NOT_FOUND(10001)/PERM_USER_NOT_FOUND(20015)、
 * ADMIN_USER_ALREADY_EXISTS(10002)/PERM_USER_ALREADY_EXISTS(20016)、
 * ADMIN_INVALID_PARAM(10008)/PERM_INVALID_PARAM(20044)。
 * </p>
 * <p>
 * 编号段归属规则（新增码适用）：能力无专属段——同一能力包可同时承载两段语义的错误
 * （如 user 能力包管理轨 {@code 1xxxx}、同步轨 {@code 2xxxx}），新增码按错误业务语义
 * 选段；跨域编排错误按对外入口所属领域取码；与领域无关的公共技术失败使用
 * {@code 9xxxx}（common GlobalErrorCode）。禁止新增 {@code 4xxxx} 段，不得重编号。
 * </p>
 * <ul>
 *   <li>10001-10099：用户相关错误</li>
 *   <li>10101-10199：组织相关错误；10111 已退役（原角色/授权代理接口退役错误，
 *       端点删除后码值不复用，退役登记见 ErrorCodeContractTest）</li>
 *   <li>10201-10299：菜单相关错误；10202 已退役（原 perm_code 唯一性错误，
 *       唯一性由 10205/10206 承接，退役登记见 ErrorCodeContractTest）</li>
 *   <li>10301-10399：字典相关错误</li>
 *   <li>10401-10499：通知相关错误</li>
 *   <li>10501-10599：文件相关错误</li>
 *   <li>10601-10699：定时任务相关错误</li>
 *   <li>10701-10799：系统配置相关错误；10701/10702 已退役（原 admin /config 端点错误，
 *       入口整链删除后码值不复用，退役登记见 ErrorCodeContractTest）</li>
 *   <li>10801-10899：OAuth2客户端相关错误</li>
 *   <li>10901-10999：认证/外部服务相关错误</li>
 *   <li>11001-11099：组织树配置相关错误</li>
 *   <li>20001-29999：权限域（角色/授权/资源/类型/条件/域/同步投影等）错误</li>
 * </ul>
 */
@Getter
public enum AccessErrorCode {

    // ===== 管理段 1xxxx（原 AdminErrorCode 平移；三组碰撞常量带 ADMIN_ 前缀） =====

    /**
     * 用户不存在
     */
    ADMIN_USER_NOT_FOUND(10001, "用户不存在"),

    /**
     * 用户账号已存在
     */
    ADMIN_USER_ALREADY_EXISTS(10002, "用户账号已存在"),

    /**
     * 用户已停用
     */
    USER_DISABLED(10003, "用户已停用"),

    /**
     * 账号已锁定（登录失败次数过多）
     */
    USER_LOCKED(10004, "账号已锁定"),

    /**
     * 账号或密码错误
     */
    PASSWORD_INCORRECT(10005, "账号或密码错误"),

    /**
     * 手机号已存在
     */
    PHONE_ALREADY_EXISTS(10006, "手机号已存在"),

    /**
     * 不能删除当前登录用户
     */
    CANNOT_DELETE_SELF(10007, "不能删除当前登录用户"),

    /**
     * 参数格式错误
     */
    ADMIN_INVALID_PARAM(10008, "参数格式错误"),

    /**
     * 不能停用当前登录用户
     */
    CANNOT_DISABLE_SELF(10009, "不能停用当前登录用户"),

    // ===== 组织相关错误（10101-10199） =====

    /**
     * 组织不存在
     */
    ORG_NOT_FOUND(10101, "组织不存在"),

    /**
     * 组织编码已存在
     */
    ORG_CODE_EXISTS(10102, "组织编码已存在"),

    /**
     * 存在子节点，请先删除子节点
     */
    ORG_HAS_CHILDREN(10103, "存在子节点，请先删除子节点"),

    /**
     * 组织层级不能超过10层
     */
    ORG_LEVEL_EXCEEDED(10104, "组织层级不能超过10层"),

    /**
     * 不能跨树移动组织
     */
    ORG_CROSS_TREE_MOVE(10105, "不能跨树移动组织"),

    /**
     * 该树只允许单关联
     */
    ORG_SINGLE_ASSOC_VIOLATION(10106, "该树只允许单关联"),

    /**
     * 组织读接口必须显式指定 orgType
     * <p>
     * v1.4 起 /org/page、/org/tree 等读接口要求显式声明 orgType（1=普通组织 / 2=岗位），
     * 以便服务端按 orgType 分发独立 VIEW 操作码（VIEW / VIEW_POSITION）做权限门控。
     * 不传将拒绝请求，避免「混合查询绕过细粒度 VIEW 配权」。
     * </p>
     */
    ORG_TYPE_REQUIRED(10107, "请显式指定 orgType（1=普通组织 / 2=岗位）"),
    ORG_PARENT_CYCLE(10108, "不能将组织移动到自身的子孙节点下"),
    ORG_MOVE_TOP_LEVEL_FORBIDDEN(10109, "不能将组织移动到顶级（树根由组织树配置管理）"),

    /**
     * 岗位拓扑约束：岗位必须作为普通组织（orgType=1）的直接子节点，且自身不能拥有下级节点。
     * <p>
     * 对齐契约：组织+岗位一体树中岗位作为所属组织的子节点挂入同一树，岗位自身无下级。
     * 违规场景：创建顶级岗位、岗位挂岗位、在岗位下创建/移动节点。
     * </p>
     */
    ORG_POSITION_TOPOLOGY_INVALID(10110, "岗位必须作为普通组织的直接子节点，且不能拥有下级节点"),

    // ===== 菜单相关错误（10201-10299） =====
    // 10202（MENU_PERM_CODE_EXISTS）已随 v3.5 菜单零权限化退役（T-ACCESS-015）：
    // perm_code 列从权威 DDL 移除，唯一性校验由 uk_sys_menu_tenant_path /
    // uk_sys_menu_tenant_resource 及对应错误码 10205/10206 承接

    /**
     * 菜单不存在
     */
    MENU_NOT_FOUND(10201, "菜单不存在"),

    /**
     * 菜单层级深度超过限制
     */
    MENU_DEPTH_EXCEEDED(10203, "菜单层级深度超过限制"),

    /**
     * 存在子菜单，请先删除子菜单
     */
    MENU_HAS_CHILDREN(10204, "存在子菜单，请先删除子菜单"),

    /**
     * 路由路径已存在（uk_sys_menu_tenant_path 唯一索引）
     */
    MENU_PATH_EXISTS(10205, "路由路径已存在"),

    /**
     * 资源关联已被其他菜单占用（uk_sys_menu_tenant_resource 唯一索引）
     */
    MENU_RESOURCE_EXISTS(10206, "资源关联已被其他菜单占用"),

    /**
     * 父菜单非法：不能是菜单自身或其后代（防 parent 链成环）
     */
    MENU_PARENT_INVALID(10207, "父菜单不能是自身或当前菜单的后代"),

    // ===== 字典相关错误（10301-10399） =====

    /**
     * 字典类型不存在
     */
    DICT_TYPE_NOT_FOUND(10301, "字典类型不存在"),

    /**
     * 字典类型下存在字典数据
     */
    DICT_TYPE_HAS_DATA(10302, "字典类型下存在字典数据"),

    /**
     * 字典数据不存在
     */
    DICT_DATA_NOT_FOUND(10303, "字典数据不存在"),

    // ===== 通知相关错误（10401-10499） =====

    /**
     * 通知不存在
     */
    NOTICE_NOT_FOUND(10401, "通知不存在"),

    // ===== 文件相关错误（10501-10599） =====

    /**
     * 文件不存在
     */
    FILE_NOT_FOUND(10501, "文件不存在"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_FAILED(10502, "文件上传失败"),

    /**
     * 文件大小超出限制
     */
    FILE_TOO_LARGE(10503, "文件大小超出限制"),

    /**
     * 文件类型不允许
     */
    FILE_TYPE_NOT_ALLOWED(10504, "文件类型不允许"),

    /**
     * 文件删除失败
     */
    FILE_DELETE_FAILED(10505, "文件删除失败"),

    /**
     * 文件路径非法（T-ADMIN-023：规范化后越出存储根目录，路径穿越纵深防御统一拒绝码）
     */
    FILE_PATH_ILLEGAL(10506, "文件路径非法"),

    /**
     * 文件读取失败（T-ADMIN-023：下载物理文件读取 IO 失败）
     */
    FILE_READ_FAILED(10507, "文件读取失败"),

    // ===== 定时任务相关错误（10601-10699） =====

    /**
     * 定时任务不存在
     */
    JOB_NOT_FOUND(10601, "定时任务不存在"),

    // ===== OAuth2客户端相关错误（10801-10899） =====

    /**
     * 客户端标识已存在
     */
    CLIENT_ID_EXISTS(10801, "客户端标识已存在"),

    /**
     * OAuth2客户端不存在
     */
    CLIENT_NOT_FOUND(10802, "OAuth2客户端不存在"),

    // ===== 认证/外部服务相关错误（10900-10999） =====

    /**
     * 外部服务调用失败
     */
    EXTERNAL_SERVICE_ERROR(10900, "外部服务调用失败"),

    /**
     * 验证码错误
     */
    CAPTCHA_INCORRECT(10901, "验证码错误"),

    /**
     * OAuth2客户端无效
     */
    OAUTH2_CLIENT_INVALID(10903, "OAuth2客户端无效"),

    /**
     * 回调地址不匹配
     */
    OAUTH2_REDIRECT_MISMATCH(10904, "回调地址不匹配"),

    /**
     * 授权码无效或已过期
     */
    OAUTH2_CODE_INVALID(10905, "授权码无效或已过期"),

    /**
     * 不支持的授权类型
     */
    OAUTH2_GRANT_TYPE_NOT_SUPPORTED(10906, "不支持的授权类型"),

    /**
     * PKCE code_verifier 不匹配
     */
    OAUTH2_CODE_VERIFIER_MISMATCH(10907, "PKCE code_verifier 不匹配"),

    /**
     * 刷新令牌无效或已过期
     */
    OAUTH2_TOKEN_INVALID(10908, "刷新令牌无效或已过期"),

    /**
     * 请求的 scope 超出客户端配置范围
     */
    OAUTH2_SCOPE_INVALID(10909, "请求的 scope 超出客户端配置范围"),

    /**
     * 缺少客户端标识
     */
    OAUTH2_MISSING_CLIENT(10910, "缺少客户端标识"),

    /**
     * 不支持的 response_type
     */
    OAUTH2_RESPONSE_TYPE_INVALID(10911, "不支持的 response_type"),

    // ===== 组织树配置相关错误（11001-11099） =====

    /**
     * 组织树配置不存在
     */
    ORG_TREE_CONFIG_NOT_FOUND(11001, "组织树配置不存在"),

    /**
     * 组织树根无法解析（org 不属于任何已配置的组织树，游离 org）
     */
    ORG_TREE_ROOT_NOT_RESOLVED(11002, "组织树根无法解析"),

    // ===== 默认组织树边界（11011-11029） =====

    /**
     * 指定组织不属于默认组织树（身份目录边界校验失败）
     */
    ORG_NOT_IN_DEFAULT_TREE(11011, "指定组织不属于默认组织树"),

    /**
     * 用户不在默认组织树可见范围内（身份目录边界校验失败）
     */
    USER_NOT_IN_DEFAULT_TREE_SCOPE(11012, "用户不在默认组织树可见范围内"),

    /**
     * 移除后用户在默认组织树无归属关系（身份目录高危保护）
     */
    USER_LOSE_DEFAULT_TREE_HOME(11013, "移除后用户在默认组织树无归属关系"),

    /**
     * 主组织必须属于默认组织树
     */
    PRIMARY_MUST_BE_IN_DEFAULT_TREE(11014, "主组织必须属于默认组织树"),

    /**
     * 用户与目标组织不存在关联关系
     */
    USER_ORG_RELATION_NOT_FOUND(11015, "用户与目标组织不存在关联关系"),

    /**
     * 用户不在操作者可见范围内（权限边界校验失败）
     */
    USER_NOT_IN_OPERATOR_VISIBLE_SCOPE(11016, "用户不在操作者可见范围内"),

    // ===== 权限段 2xxxx（原 PermissionErrorCode 平移；三组碰撞常量带 PERM_ 前缀） =====

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
    PERM_USER_NOT_FOUND(20015, "用户不存在"),

    /**
     * 用户已存在
     */
    PERM_USER_ALREADY_EXISTS(20016, "用户已存在"),

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
     * 条件启用状态不变量（2026-08-08 产品确认，总册 §11.4，T-PERM-041）：
     * 主权限 conditionCode 新写入或变更时目标条件必须 enabled=true——停用条件不得
     * 新建绑定或改绑；存量绑定（update 未变更 conditionCode，含同 id 重写）允许保留。
     * 仅判主权限（子权限带条件由 20043 先行拒绝）。
     */
    CONDITION_DISABLED(20042, "权限条件已停用"),

    /**
     * 子权限属性系统不变量（2026-08-08 复审产品确认，总册 §11.4）：
     * 子权限不承载条件与再授予——create 的 conditionCode 非 null / canGrant 非 false、
     * 或 update 目标为子权限，一律拒绝（先于主权限 20041/20042 判定）。
     */
    SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED(20043, "子权限不承载条件与再授予（系统不变量）"),

    /**
     * 参数格式错误
     */
    PERM_INVALID_PARAM(20044, "参数格式错误"),

    /**
     * 禁止通过权限管理入口或外部同步直接修改 access-service 本地投影。
     * <p>
     * 编号 20045/20046 段：20042 已由 CONDITION_DISABLED 承载（总册 §11.4，
     * T-PERM-041 落地），统一响应只暴露数字码，同一编号不得承载两种业务含义。
     * </p>
     */
    LOCAL_PROJECTION_IMMUTABLE(20045, "禁止直接修改 access-service 本地权限投影"),

    /**
     * 本地投影依赖缺失（父组织角色/所属组织角色等不存在），fail-closed 整体回滚。
     */
    LOCAL_PROJECTION_DEPENDENCY_MISSING(20046, "本地投影依赖缺失（父组织角色/所属组织角色不存在）"),

    /**
     * 配置键必须使用 admin./permission./access. 命名空间前缀（T-ACCESS-007 §5.2）。
     */
    CONFIG_KEY_NAMESPACE_INVALID(20047, "配置键必须使用 admin./permission./access. 命名空间前缀"),

    // 20048 已退役：原 autoGrant 开关随 MANIFEST 独占写入移除，编号不复用。


    /**
     * 类型编码在 tenant+typeKey 内已存在（uk_type_definition_code）。
     */
    TYPE_DEFINITION_CODE_DUPLICATE(20049, "类型编码已存在（tenant+typeKey 内唯一）"),

    /**
     * 父角色非法：不能是被移动角色自身或其子孙（parent 链成环后环节点从树构建中静默消失、
     * 祖先/子孙查询语义受损；对齐管理段同场景先例 ORG_PARENT_CYCLE/MENU_PARENT_INVALID）。
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
    CONDITION_INLINE_NOT_MANAGEABLE(20060, "内联条件不可在管理面管理/不可被显式引用（只能在授权页随记录更改）"),

    /**
     * 授权根种子行只读（T-PERM-062）：grant_source=AUTHORITY_ROOT 的行经 apply-grant-plan
     * updates/removes（含向其挂子权限）一律拒绝——对齐 AUTO_DEP 只读（20034）先例。
     * 种子行由类型生命周期维护：创建类型/追加操作自动补种、所有者变更同事务迁移
     * （先清后种重整化）、类型删除级联清理（T-PERM-050）。
     */
    AUTHORITY_ROOT_READONLY(20061, "授权根种子行只读（经类型生命周期维护：类型创建/追加操作补种、所有者变更迁移、类型删除清理）"),

    /**
     * 角色互斥授予冲突（T-PERM-063）：user-role/assign、batch-assign 写路径事务内校验
     * 「授予后有效角色集（现有效 ∪ 本批新增，仅计启用角色）」命中 ROLE_MUTEX 对即整批原子
     * 拒绝（对齐本入口既有逐项收集 errors 整批抛风格），message 列出冲突用户与角色对。
     * 规则读取走 DB 直查不经 ROLE_MUTEX_RULE 缓存，新建规则即刻生效。
     */
    ROLE_MUTEX_ASSIGN_CONFLICT(20062, "授予后用户将同时持有互斥角色，已整批拒绝（冲突用户与角色对见 message）"),

    /**
     * 角色互斥规则存量持有守卫（T-PERM-063）：conflict-rule/create、update 的 ROLE_MUTEX
     * 分支在写入前检查存量——存在同时持有两角色的用户即拒绝立规（message 含冲突用户 id
     * 清单，截断上限 20），管理员先解绑再立规；立规后系统内无违规持有，运行时双删不再是
     * 常态兜底。PERM_MUTEX 分支与 remove 不适用。
     */
    ROLE_MUTEX_EXISTING_HOLDERS(20063, "存在同时持有互斥角色对的用户，不可创建/更新该规则（请先解绑，用户清单见 message）"),

    /**
     * 系统内置配置不可经保存入口覆盖（T-ACCESS-037 外评存量观察修正，2026-09-13 用户拍板）：
     * system-config/save 的 update 分支命中 is_system=true 种子行拒绝——契约「系统内置仅走种子」
     * 的运行时强制（原 admin /config 侧 CONFIG_SYSTEM_IMMUTABLE(10702) 随僵尸端点退役，perm 侧
     * 自落地起无守卫；本码不复用 10702 退役码值）。
     */
    CONFIG_KEY_SYSTEM_IMMUTABLE(20064, "系统内置配置不可修改（仅经种子维护，租户自定义键请换新键）"),

    /**
     * 服务凭证无效（T-PERM-070，2026-09-20 拍板三码细分）：凭证定位失败（credential_id
     * 不存在/已删除）或 secret 比对失败、凭证头半传。仲裁器 403 禁止降级回落旧密钥。
     */
    SERVICE_CREDENTIAL_INVALID(20065, "服务凭证无效（credential_id 不存在或 secret 错误）"),

    /**
     * 服务凭证已过期（T-PERM-070 三态细分之一）：expires_at 已过，凭证立即失效；
     * 处置=签发新凭证轮换。
     */
    SERVICE_CREDENTIAL_EXPIRED(20066, "服务凭证已过期（请签发新凭证轮换）"),

    /**
     * 服务凭证已停用（T-PERM-070 三态细分之一）：status=0，通常为轮换收尾停旧或
     * 管理员主动吊销；处置=联系平台管理员。
     */
    SERVICE_CREDENTIAL_DISABLED(20067, "服务凭证已停用（轮换收尾或管理员吊销，请联系平台管理员）"),

    /**
     * 凭证绑定的服务未注册或已停用（T-PERM-070）：凭证本身有效，但 service_config
     * 无该服务的有效启用行——对齐 20055 门禁的服务注册段（设计稿 §3.2 ③前置校验；
     * 服务停用/注销即同步通道一起断，同 sync 通道白名单语义）。
     */
    SERVICE_CREDENTIAL_SERVICE_INACTIVE(20068, "凭证绑定的服务未注册或已停用");

    private final int code;
    private final String message;

    AccessErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
