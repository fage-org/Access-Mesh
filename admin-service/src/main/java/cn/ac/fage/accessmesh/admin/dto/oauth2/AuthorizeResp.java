package cn.ac.fage.accessmesh.admin.dto.oauth2;

/**
 * OAuth2授权响应记录类
 * <p>
 * OAuth2授权码流程的授权响应。
 * 包含授权码和状态参数。
 * </p>
 *
 * @param code  授权码（用于换取令牌）
 * @param state 状态参数（与请求中的state一致）
 */
public record AuthorizeResp(
    /**
     * 授权码（用于换取访问令牌）
     */
    String code,

    /**
     * 状态参数（与请求中的state一致，用于防CSRF）
     */
    String state
) {}