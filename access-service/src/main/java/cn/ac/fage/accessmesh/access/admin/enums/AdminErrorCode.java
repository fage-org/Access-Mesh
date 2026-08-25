package cn.ac.fage.accessmesh.access.admin.enums;

import lombok.Getter;

/**
 * Admin服务错误码枚举
 * <p>
 * 定义管理域（access-service admin 域）的错误码，范围10001-19999。
 * 包括用户、组织、菜单、字典、通知、文件、任务、配置、OAuth2等业务错误码。
 * 错误码分配规则（参考PROJECT_RULES.md §1.2）：
 * <ul>
 *   <li>10001-10099：用户相关错误</li>
 *   <li>10101-10199：组织相关错误；10111 为角色/授权代理接口退役错误（T-ACCESS-006，见枚举注释）</li>
 *   <li>10201-10299：菜单相关错误</li>
 *   <li>10301-10399：字典相关错误</li>
 *   <li>10401-10499：通知相关错误</li>
 *   <li>10501-10599：文件相关错误</li>
 *   <li>10601-10699：定时任务相关错误</li>
 *   <li>10701-10799：系统配置相关错误</li>
 *   <li>10801-10899：OAuth2客户端相关错误</li>
 *   <li>10901-10999：认证/外部服务相关错误</li>
 *   <li>11001-11099：组织树配置相关错误</li>
 * </ul>
 * </p>
 */
@Getter
public enum AdminErrorCode {

    // ===== 用户相关错误（10001-10099） =====

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(10001, "用户不存在"),

    /**
     * 用户账号已存在
     */
    USER_ALREADY_EXISTS(10002, "用户账号已存在"),

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
    INVALID_PARAM(10008, "参数格式错误"),

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

    // ===== 系统配置相关错误（10701-10799） =====

    /**
     * 配置不存在
     */
    CONFIG_NOT_FOUND(10701, "配置不存在"),

    /**
     * 系统内置配置不可删除
     */
    CONFIG_SYSTEM_IMMUTABLE(10702, "系统内置配置不可删除"),

    // ===== OAuth2客户端相关错误（10801-10899） =====

    /**
     * 客户端标识已存在
     */
    CLIENT_ID_EXISTS(10801, "客户端标识已存在"),

    /**
     * OAuth2客户端不存在
     */
    CLIENT_NOT_FOUND(10802, "OAuth2客户端不存在"),

    // ===== 认证/外部服务相关错误（10901-10999） =====

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

    /**
     * admin 侧角色/授权代理接口退役（T-ACCESS-006）
     * <p>
     * 合并后角色与授权管理由 permission 域直接提供（/api/perm/abstract-role、
     * /api/perm/user-role、/api/perm/role-resource-permission），本接口保留映射
     * 仅为向后兼容，调用方不得依赖其成功。
     * </p>
     */
    ROLE_API_RETIRED(10111, "角色与授权管理已由权限域 /api/perm/** 提供，本接口退役");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造错误码枚举
     *
     * @param code    错误码
     * @param message 错误消息
     */
    AdminErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}