package cn.ac.fage.accessmesh.admin.enums;

public enum AdminErrorCode {

    USER_NOT_FOUND(10001, "用户不存在"),
    USER_ALREADY_EXISTS(10002, "用户账号已存在"),
    USER_DISABLED(10003, "用户已停用"),
    USER_LOCKED(10004, "账号已锁定"),
    PASSWORD_INCORRECT(10005, "账号或密码错误"),
    PHONE_ALREADY_EXISTS(10006, "手机号已存在"),
    CANNOT_DELETE_SELF(10007, "不能删除当前登录用户"),
    ORG_NOT_FOUND(10101, "组织不存在"),
    ORG_CODE_EXISTS(10102, "组织编码已存在"),
    ORG_HAS_CHILDREN(10103, "存在子节点，请先删除子节点"),
    ORG_LEVEL_EXCEEDED(10104, "组织层级不能超过10层"),
    ORG_CROSS_TREE_MOVE(10105, "不能跨树移动组织"),
    ORG_SINGLE_ASSOC_VIOLATION(10106, "该树只允许单关联"),
    MENU_NOT_FOUND(10201, "菜单不存在"),
    MENU_PERM_CODE_EXISTS(10202, "权限标识已存在"),
    MENU_DEPTH_EXCEEDED(10203, "菜单层级深度超过限制"),
    MENU_HAS_CHILDREN(10204, "存在子菜单，请先删除子菜单"),
    DICT_TYPE_NOT_FOUND(10301, "字典类型不存在"),
    DICT_TYPE_HAS_DATA(10302, "字典类型下存在字典数据"),
    DICT_DATA_NOT_FOUND(10303, "字典数据不存在"),
    NOTICE_NOT_FOUND(10401, "通知不存在"),
    FILE_NOT_FOUND(10501, "文件不存在"),
    JOB_NOT_FOUND(10601, "定时任务不存在"),
    CONFIG_NOT_FOUND(10701, "配置不存在"),
    CONFIG_SYSTEM_IMMUTABLE(10702, "系统内置配置不可删除"),
    CLIENT_ID_EXISTS(10801, "客户端标识已存在"),
    CAPTCHA_INCORRECT(10901, "验证码错误"),
    OAUTH2_CLIENT_INVALID(10903, "OAuth2客户端无效"),
    OAUTH2_REDIRECT_MISMATCH(10904, "回调地址不匹配"),
    OAUTH2_CODE_INVALID(10905, "授权码无效或已过期"),
    OAUTH2_GRANT_TYPE_NOT_SUPPORTED(10906, "不支持的授权类型"),
    OAUTH2_CODE_VERIFIER_MISMATCH(10907, "PKCE code_verifier 不匹配"),
    OAUTH2_TOKEN_INVALID(10908, "刷新令牌无效或已过期"),
    OAUTH2_SCOPE_INVALID(10909, "请求的 scope 超出客户端配置范围"),
    OAUTH2_MISSING_CLIENT(10910, "缺少客户端标识"),
    OAUTH2_RESPONSE_TYPE_INVALID(10911, "不支持的 response_type"),
    ORG_TREE_CONFIG_NOT_FOUND(11001, "组织树配置不存在"),
    ;

    private final int code;
    private final String message;

    AdminErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
