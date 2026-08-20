package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.oauth2.*;
import cn.ac.fage.accessmesh.access.infrastructure.OAuth2JwtSupport;
import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.service.OAuth2Service;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.common.exception.BizException;
import lombok.Getter;
import lombok.Setter;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * OAuth2认证服务实现类
 * <p>
 * 提供OAuth2授权码流程的授权、令牌交换、刷新、撤销等功能。
 * 支持第三方应用通过OAuth2协议接入系统，获取用户授权后的访问令牌。
 * 支持PKCE扩展（code_challenge/code_verifier），增强公开客户端安全性。
 * 使用Redis存储授权码和刷新令牌，使用Lua脚本确保原子性操作。
 * 访问令牌为JWT格式，撤销时添加到Redis黑名单。
 * </p>
 */
@Service
public class OAuth2ServiceImpl implements OAuth2Service {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ServiceImpl.class);

    private static final String AUTH_CODE_PREFIX = "oauth2:code:";
    private static final String REFRESH_TOKEN_PREFIX = "oauth2:refresh:";
    private static final int AUTH_CODE_TTL_SECONDS = 300;
    /** OAuth2 登录方式（sys_login_log.login_type 注释对齐） */
    private static final String LOGIN_TYPE_OAUTH2 = "OAUTH2";

    /**
     * Lua脚本：GET + DEL 合并为原子操作
     * <p>
     * 确保授权码和刷新令牌一次性使用，防止重复兑换。
     * </p>
     */
    private static final String LUA_GET_AND_DELETE =
        "local value = redis.call('GET', KEYS[1]) " +
        "if value then " +
        "    redis.call('DEL', KEYS[1]) " +
        "end " +
        "return value";

    private final OAuth2ClientDomainService oauth2ClientDomainService;
    private final UserDomainService userDomainService;
    private final LoginLogDomainService loginLogDomainService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sa-token.jwt-secret-key}")
    private String jwtSecretKey;

    /**
     * 构造函数注入依赖
     *
     * @param oauth2ClientDomainService OAuth2客户端领域服务
     * @param userDomainService 用户领域服务
     * @param loginLogDomainService 登录日志领域服务（OAUTH2 令牌签发审计）
     * @param redisTemplate Redis操作模板，用于存储授权码和刷新令牌
     * @param objectMapper JSON序列化工具
     */
    public OAuth2ServiceImpl(OAuth2ClientDomainService oauth2ClientDomainService,
                             UserDomainService userDomainService,
                             LoginLogDomainService loginLogDomainService,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.oauth2ClientDomainService = oauth2ClientDomainService;
        this.userDomainService = userDomainService;
        this.loginLogDomainService = loginLogDomainService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * OAuth2授权接口
     * <p>
     * 用户登录后调用此接口授权第三方应用访问。
     * 校验客户端、授权类型、回调地址、授权范围、PKCE参数。
     * 生成授权码并存储到Redis（5分钟有效期）。
     * </p>
     *
     * @param req 授权请求，包含客户端ID、重定向URI、授权范围、PKCE参数
     * @return 授权响应，包含授权码和状态
     * @throws BizException 客户端无效、授权类型不支持、回调地址不匹配等
     */
    @Override
    @OperationLog(module = "ADMIN", action = "OAUTH2_AUTHORIZE", targetType = "sys_oauth2_client",
        targetId = "#req.clientId()", summary = "'oauth2 authorize client ' + #req.clientId()")
    public AuthorizeResp authorize(AuthorizeReq req) {
        // 1. Validate client
        SysOauth2Client client = getValidClient(req.clientId());

        // 2. Validate grant type
        if (!containsGrantType(client.getGrantTypes(), "authorization_code")) {
            throw new BizException(AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getCode(),
                AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getMessage());
        }

        // 3. Validate response_type
        if (!"code".equals(req.responseType())) {
            throw new BizException(AdminErrorCode.OAUTH2_RESPONSE_TYPE_INVALID.getCode(),
                AdminErrorCode.OAUTH2_RESPONSE_TYPE_INVALID.getMessage());
        }

        // 4. Validate redirectUri
        validateRedirectUri(client, req.redirectUri());

        // 5. Validate scope if provided
        if (req.scope() != null && !req.scope().isBlank()) {
            validateScope(client, req.scope());
        }

        // 6. Validate PKCE code_challenge if provided
        String codeChallengeMethod = req.codeChallengeMethod() != null ? req.codeChallengeMethod() : "S256";
        if (req.codeChallenge() != null && !req.codeChallenge().isBlank()) {
            if (!"S256".equals(codeChallengeMethod) && !"plain".equals(codeChallengeMethod)) {
                throw new BizException(AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getCode(),
                    "不支持的 code_challenge_method: " + codeChallengeMethod);
            }
        }

        // 7. Get current user
        long userId = StpUtil.getLoginIdAsLong();

        // 8. Generate authorization code
        String code = UUID.randomUUID().toString().replace("-", "");

        // 9. Store code in Redis
        AuthCodeData codeData = new AuthCodeData();
        codeData.setClientId(req.clientId());
        codeData.setUserId(userId);
        codeData.setTenantId(TenantContextHolder.getTenantId());
        codeData.setRedirectUri(req.redirectUri());
        codeData.setCodeChallenge(req.codeChallenge());
        codeData.setCodeChallengeMethod(codeChallengeMethod);
        codeData.setScope(req.scope());
        try {
            String json = objectMapper.writeValueAsString(codeData);
            redisTemplate.opsForValue().set(AUTH_CODE_PREFIX + code, json, AUTH_CODE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize auth code data", e);
            throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(), "授权码生成失败");
        }

        return new AuthorizeResp(code, req.state());
    }

    /**
     * OAuth2令牌接口
     * <p>
     * 第三方应用使用授权码换取访问令牌。
     * 目前仅支持authorization_code授权类型。
     * 执行完成后清除租户上下文。
     * </p>
     *
     * @param req 令牌请求，包含授权码、客户端ID、客户端密钥、重定向URI、PKCE验证器
     * @return 令牌响应，包含访问令牌、刷新令牌、过期时间
     * @throws BizException 授权类型不支持
     */
    @Override
    @OperationLog(module = "ADMIN", action = "OAUTH2_TOKEN_ISSUE", targetType = "oauth2_token",
        targetId = "#req.clientId()", summary = "'oauth2 token issued by client ' + #req.clientId()")
    public TokenResp token(TokenReq req) {
        // 授权码（TokenReq.code）为短期凭证：SensitiveDataUtils 全局精确集合不含 code
        // （与组织/资源编码同名会误掩码），此处按调用作用域登记使切面脱敏本接口请求体时精确掩码 code。
        OperationLogRuntimeContext.markSensitiveField("code");
        try {
            if ("authorization_code".equals(req.grantType())) {
                return tokenByAuthorizationCode(req);
            } else {
                throw new BizException(AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getCode(),
                    AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getMessage());
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * OAuth2刷新令牌接口
     * <p>
     * 使用刷新令牌获取新的访问令牌。
     * 刷新令牌使用后立即删除（一次性），生成新的刷新令牌。
     * PKCE公开客户端只需客户端ID和刷新令牌，无需客户端密钥。
     * </p>
     *
     * @param refreshToken 刷新令牌
     * @param clientId 客户端ID
     * @return 令牌响应，包含新的访问令牌和刷新令牌
     * @throws BizException 刷新令牌无效、客户端不匹配等
     */
    @Override
    @OperationLog(module = "ADMIN", action = "OAUTH2_TOKEN_REFRESH", targetType = "oauth2_token",
        targetId = "#clientId", summary = "'oauth2 token refresh by client ' + #clientId")
    public TokenResp refreshToken(String refreshToken, String clientId) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                recordOauth2Failure(clientId, "refresh token blank");
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            // 验证客户端存在且有效
            SysOauth2Client client = getValidClient(clientId);

            // 使用 Lua 脚本原子性地获取并删除 refresh token，防止重复使用
            String refreshTokenDataJson = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_GET_AND_DELETE, String.class),
                Collections.singletonList(REFRESH_TOKEN_PREFIX + refreshToken)
            );

            if (refreshTokenDataJson == null) {
                recordOauth2Failure(clientId, "refresh token invalid or expired");
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            RefreshTokenData refreshTokenData;
            try {
                refreshTokenData = objectMapper.readValue(refreshTokenDataJson, RefreshTokenData.class);
            } catch (JsonProcessingException e) {
                recordOauth2Failure(clientId, "refresh token malformed");
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            // 从 refresh token 设置租户上下文
            TenantContextHolder.setTenantId(refreshTokenData.getTenantId());
            // 审计租户登记：匿名端点 finally 会 clear holder，运行时 override 供 @OperationLog 切面解析
            OperationLogRuntimeContext.setTenantId(refreshTokenData.getTenantId());

            // 验证 client_id 匹配（refreshToken 绑定特定客户端）
            if (!refreshTokenData.getClientId().equals(clientId)) {
                recordOauth2Failure(clientId, "refresh token client mismatch");
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            // 生成新的访问令牌
            int accessTokenTtl = client.getAccessTokenTtl() != null ? client.getAccessTokenTtl() : 86400;
            String accessToken = generateAccessToken(refreshTokenData.getUserId(), clientId,
                refreshTokenData.getScope(), accessTokenTtl);
            int refreshTokenTtl = client.getRefreshTokenTtl() != null ? client.getRefreshTokenTtl() : 604800;

            // 生成新的刷新令牌
            String newRefreshToken = UUID.randomUUID().toString().replace("-", "");
            RefreshTokenData newRefreshTokenData = new RefreshTokenData();
            newRefreshTokenData.setUserId(refreshTokenData.getUserId());
            newRefreshTokenData.setTenantId(refreshTokenData.getTenantId());
            newRefreshTokenData.setClientId(clientId);
            newRefreshTokenData.setScope(refreshTokenData.getScope());
            try {
                redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + newRefreshToken,
                    objectMapper.writeValueAsString(newRefreshTokenData),
                    refreshTokenTtl, TimeUnit.SECONDS);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize refresh token data", e);
                recordOauth2Failure(clientId, "refresh token generation failed");
                throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(), "刷新令牌生成失败");
            }

            // OAuth2 刷新令牌成功：记录 OAUTH2 登录日志（匿名端点，审计由 sys_login_log 承载）
            recordOauth2Login(refreshTokenData.getTenantId(), refreshTokenData.getUserId(), clientId, 1, null);

            return new TokenResp(accessToken, "Bearer", accessTokenTtl, newRefreshToken, refreshTokenData.getScope());
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * OAuth2撤销令牌接口
     * <p>
     * 撤销访问令牌，使令牌失效。
     * 将JWT的jti添加到Redis黑名单，剩余有效期后自动过期。
     * </p>
     *
     * @param accessToken 访问令牌
     */
    @Override
    @OperationLog(module = "ADMIN", action = "OAUTH2_TOKEN_REVOKE", targetType = "oauth2_token",
        targetId = "", summary = "'oauth2 revoke token'")
    public void revokeToken(String accessToken) {
        // 撤销前必须验签（签名 + loginType + 有效期），
        // 非法令牌不得写 Redis——原实现对任意字符串直接写 oauth2:blacklist:* 键
        // （extractJti 失败返回原 token 作键、getTokenRemainingTtl 读不存在的 exp 恒回退 86400），
        // 匿名调用者可制造任意黑名单键造成 Redis 内存型 DoS。
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        Map<String, Object> payloads;
        try {
            // getPayloads 校验签名 + loginType + 有效期（isCheckTimeout=true）
            payloads = SaJwtUtil.getPayloads(accessToken, OAuth2JwtSupport.LOGIN_TYPE, jwtSecretKey);
        } catch (SaTokenException e) {
            log.warn("revoke ignored invalid oauth2 jwt (signature/loginType/expired)");
            return;
        }
        Object jti = payloads.get(OAuth2JwtSupport.JTI_CLAIM);
        if (jti == null || jti.toString().isBlank()) {
            log.warn("revoke ignored oauth2 jwt without jti");
            return;
        }
        // 审计租户登记：revoke 为匿名端点（拦截器仅绑 ANONYMOUS），从 JWT 载荷解析 tenant_id
        // 供 @OperationLog 切面租户解析（否则切面因租户为空跳过该条撤销审计）。
        Long tenantId = parseTenantId(payloads.get("tenant_id"));
        if (tenantId != null) {
            OperationLogRuntimeContext.setTenantId(tenantId);
        }
        // 实际剩余有效期（SaJwtTemplate 以 eff claim 计算，签发侧 6 参 createToken 写入）
        long ttl = SaJwtUtil.getTimeout(accessToken, OAuth2JwtSupport.LOGIN_TYPE, jwtSecretKey);
        if (ttl > 0) {
            redisTemplate.opsForValue().set(
                OAuth2JwtSupport.BLACKLIST_KEY_PREFIX + jti, "1", ttl, TimeUnit.SECONDS);
        }
    }

    /**
     * OAuth2用户信息接口
     * <p>
     * 第三方应用使用访问令牌获取用户基本信息。
     * 返回用户ID、用户名、姓名、手机号、邮箱等信息。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息响应
     * @throws BizException 用户不存在
     */
    @Override
    public OAuth2UserInfoResp getClientUserInfo(Long userId) {
        // 原硬编码 null 租户（tenant_id = null 恒查不到，
        // 端点从未可用）。改为读可信上下文租户（拦截器 OAuth2 JWT / 会话认证后绑定）。
        Long tenantId = TenantContextHolder.getTenantId();
        SysUser user = userDomainService.selectValidById(tenantId, userId);
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(),
                AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        return new OAuth2UserInfoResp(
            String.valueOf(user.getId()),
            user.getUsername(),
            user.getName(),
            user.getPhone(),
            user.getEmail()
        );
    }

    /**
     * 通过授权码换取令牌
     * <p>
     * 校验客户端密钥，使用Lua脚本原子性地获取并删除授权码。
     * 校验回调地址和PKCE验证器。
     * 生成JWT访问令牌和刷新令牌。
     * </p>
     *
     * @param req 令牌请求
     * @return 令牌响应
     * @throws BizException 授权码无效、客户端密钥错误、回调地址不匹配、PKCE验证失败等
     */
    private TokenResp tokenByAuthorizationCode(TokenReq req) {
        // 1. Validate client
        if (req.clientId() == null || req.clientId().isBlank()) {
            recordOauth2Failure(req.clientId(), "missing client id");
            throw new BizException(AdminErrorCode.OAUTH2_MISSING_CLIENT.getCode(),
                AdminErrorCode.OAUTH2_MISSING_CLIENT.getMessage());
        }
        SysOauth2Client client = getValidClient(req.clientId());

        // 2. Validate client secret
        if (req.clientSecret() == null || !BCrypt.checkpw(req.clientSecret(), client.getClientSecret())) {
            recordOauth2Failure(req.clientId(), "client secret mismatch");
            throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CLIENT_INVALID.getMessage());
        }

        // 3. Validate grant type
        if (!containsGrantType(client.getGrantTypes(), "authorization_code")) {
            recordOauth2Failure(req.clientId(), "grant type not supported");
            throw new BizException(AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getCode(),
                AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getMessage());
        }

        // 4. 使用 Lua 脚本原子性地获取并删除 authorization code，确保一次性使用
        String codeKey = AUTH_CODE_PREFIX + req.code();
        String codeDataJson = redisTemplate.execute(
            new DefaultRedisScript<>(LUA_GET_AND_DELETE, String.class),
            Collections.singletonList(codeKey)
        );

        if (codeDataJson == null) {
            recordOauth2Failure(req.clientId(), "authorization code invalid or expired");
            throw new BizException(AdminErrorCode.OAUTH2_CODE_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CODE_INVALID.getMessage());
        }

        AuthCodeData codeData;
        try {
            codeData = objectMapper.readValue(codeDataJson, AuthCodeData.class);
        } catch (JsonProcessingException e) {
            recordOauth2Failure(req.clientId(), "authorization code malformed");
            throw new BizException(AdminErrorCode.OAUTH2_CODE_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CODE_INVALID.getMessage());
        }

        // 从授权码设置租户上下文
        TenantContextHolder.setTenantId(codeData.getTenantId());
        // 审计租户登记：token 匿名端点 finally 会 clear holder，运行时 override 供 @OperationLog 切面解析
        OperationLogRuntimeContext.setTenantId(codeData.getTenantId());

        // 5. Validate redirect_uri matches
        if (!codeData.getRedirectUri().equals(req.redirectUri())) {
            recordOauth2Failure(req.clientId(), "redirect uri mismatch");
            throw new BizException(AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode(),
                AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getMessage());
        }

        // 6. Validate PKCE if used
        if (codeData.getCodeChallenge() != null && !codeData.getCodeChallenge().isBlank()) {
            if (req.codeVerifier() == null || req.codeVerifier().isBlank()) {
                recordOauth2Failure(req.clientId(), "missing code verifier");
                throw new BizException(AdminErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getCode(),
                    "缺少 code_verifier");
            }
            if (!verifyPkce(codeData.getCodeChallenge(), req.codeVerifier(), codeData.getCodeChallengeMethod())) {
                recordOauth2Failure(req.clientId(), "PKCE verification failed");
                throw new BizException(AdminErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getCode(),
                    AdminErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getMessage());
            }
        }

        // 7. Generate tokens
        int accessTokenTtl = client.getAccessTokenTtl() != null ? client.getAccessTokenTtl() : 86400;
        int refreshTokenTtl = client.getRefreshTokenTtl() != null ? client.getRefreshTokenTtl() : 604800;
        String scope = codeData.getScope();

        String accessToken = generateAccessToken(codeData.getUserId(), req.clientId(), scope, accessTokenTtl);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 存储刷新令牌
        RefreshTokenData refreshData = new RefreshTokenData();
        refreshData.setUserId(codeData.getUserId());
        refreshData.setTenantId(codeData.getTenantId());
        refreshData.setClientId(req.clientId());
        refreshData.setScope(scope);
        try {
            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken,
                objectMapper.writeValueAsString(refreshData),
                refreshTokenTtl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize refresh token data", e);
            recordOauth2Failure(req.clientId(), "refresh token generation failed");
            throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(), "刷新令牌生成失败");
        }

        // OAuth2 授权码换取令牌成功：记录 OAUTH2 登录日志（匿名端点，审计由 sys_login_log 承载）
        recordOauth2Login(codeData.getTenantId(), codeData.getUserId(), req.clientId(), 1, null);

        return new TokenResp(accessToken, "Bearer", accessTokenTtl, refreshToken, scope);
    }

    /**
     * 生成JWT访问令牌
     * <p>
     * 使用SaJwtUtil创建JWT令牌，包含用户ID、客户端ID、租户ID、授权范围、jti等额外数据。
     * </p>
     *
     * @param userId 用户ID
     * @param clientId 客户端ID
     * @param scope 授权范围
     * @param accessTokenTtl 访问令牌有效期（秒，客户端配置；拦截器验签校验 EFF）
     * @return JWT访问令牌字符串
     */
    private String generateAccessToken(long userId, String clientId, String scope, int accessTokenTtl) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("client_id", clientId);
        // 从 TenantContextHolder 获取实际的 tenantId
        Long tenantId = TenantContextHolder.getTenantId();
        extraData.put("tenant_id", tenantId != null ? String.valueOf(tenantId) : "0");
        if (scope != null && !scope.isBlank()) {
            extraData.put("scope", scope);
        }
        extraData.put("jti", UUID.randomUUID().toString().replace("-", ""));

        // 历史缺陷（createToken 参数错位，任何持有者可伪造 OAuth2 token，存量安全漏洞）：
        // ① 原参数错位——createToken 签名为 (loginType, loginId, extraData, keyt)，存量把
        //    jwtSecretKey 当 loginType、字面量 "Bearer" 当签名密钥（任何持有者可伪造 OAuth2 token，
        //    存量安全漏洞）。修复：loginType=oauth2（与 RequestContextInterceptor 验签一致）、
        //    keyt=jwt-secret-key。
        // ② 4 参 createToken 不设置有效期（EFF），拦截器验签（isCheckTimeout=true）必抛"已过期"；
        //    改 6 参（带 timeout）由 SaJwtTemplate 写入 EFF=now+ttl。
        // 项目未部署（空库），无存量 token 兼容负担；载荷不变（loginId/tenant_id/jti/scope）。
        return SaJwtUtil.createToken(OAuth2JwtSupport.LOGIN_TYPE, userId, DEVICE,
            accessTokenTtl, extraData, jwtSecretKey);
    }

    /** JWT device claim（不影响验签，固定标识 OAuth2 流程）。 */
    private static final String DEVICE = "oauth2";

    /**
     * 验证PKCE码挑战
     * <p>
     * 支持plain和S256两种方法。
     * S256方法使用SHA-256哈希后Base64 URL安全编码。
     * </p>
     *
     * @param codeChallenge 码挑战（授权请求中的值）
     * @param codeVerifier 码验证器（令牌请求中的值）
     * @param method 方法（plain或S256）
     * @return 是否验证通过
     */
    private boolean verifyPkce(String codeChallenge, String codeVerifier, String method) {
        if ("plain".equals(method)) {
            return codeChallenge.equals(codeVerifier);
        }
        // S256
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return codeChallenge.equals(computed);
        } catch (Exception e) {
            log.error("PKCE verification failed", e);
            return false;
        }
    }

    /**
     * 从 JWT 载荷解析租户ID（generateAccessToken 以 {@code String.valueOf(tenantId)} 写入，
     * 无租户时为 "0"）。
     *
     * @param value 载荷中的 tenant_id 值（可为 null 或 "0"）
     * @return 正租户ID；不可解析或非正数返回 null
     */
    private static Long parseTenantId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            long tenantId = Long.parseLong(value.toString().trim());
            return tenantId > 0 ? tenantId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 记录 OAuth2 令牌流程/刷新失败的安全审计（status=0，login_type=OAUTH2）。
     * <p>
     * 失败原因（非法客户端/凭据错误/授权码无效/回调或 PKCE 不匹配/刷新令牌无效等）均为
     * 可被对端操控的尝试，需持久化以支撑安全审计；与成功路径统一落 sys_login_log。
     * 租户解析优先已登记的运行时 override / 可信上下文（授权码或刷新令牌加载后即已知），
     * 否则回退按 clientId 查启用客户端取其租户；解析不出租户（如完全未知的 clientId、
     * 或早期缺参）时跳过并告警——不写 tenant_id=null（NOT NULL）。
     * </p>
     *
     * @param clientId   OAuth2客户端ID（可 null）
     * @param failReason 失败原因（写 fail_reason，对齐列上限由落库层截断）
     */
    private void recordOauth2Failure(String clientId, String failReason) {
        Long resolvedTenant = TenantContextHolder.getTenantId();
        if (resolvedTenant == null && clientId != null && !clientId.isBlank()) {
            try {
                SysOauth2Client client = oauth2ClientDomainService.findActiveByClientId(clientId);
                if (client != null) {
                    resolvedTenant = client.getTenantId();
                }
            } catch (Exception e) {
                log.debug("OAuth2 失败租户解析异常（按未知客户端跳过审计）: clientId={}", clientId);
                resolvedTenant = null;
            }
        }
        if (resolvedTenant == null) {
            log.warn("OAuth2 失败尝试无可解析租户，跳过登录日志（不写 tenant_id=null）: clientId={}, reason={}",
                clientId, failReason);
            return;
        }
        recordOauth2Login(resolvedTenant, null, clientId, 0, failReason);
    }

    /**
     * 记录 OAUTH2 登录日志成功（调用方兜底，失败仅告警不影响令牌流程）。
     * <p>
     * OAuth2 令牌签发/刷新端点为匿名公开端点，无操作者身份；其审计由
     * sys_login_log 承载（login_type=OAUTH2），用户名回填自用户库避免外键 ID 入库。
     * </p>
     *
     * @param tenantId 租户ID（可能为 null，同匿名端点语义）
     * @param userId   登录用户ID
     * @param clientId OAuth2客户端ID
     * @param status   登录状态（1=成功）
     * @param failReason 失败原因（成功为 null）
     */
    private void recordOauth2Login(Long tenantId, Long userId, String clientId,
                                   Integer status, String failReason) {
        Long resolvedTenant = tenantId;
        String username = null;
        try {
            // 用户名回填查询置于 try 内：DB 故障时仅告警降级，不得中断令牌签发/刷新主流程
            if (resolvedTenant != null && userId != null) {
                SysUser user = userDomainService.selectValidById(resolvedTenant, userId);
                if (user != null) {
                    username = user.getUsername();
                }
            }
            loginLogDomainService.recordLoginLog(new LoginLogDomainService.LoginLogEntry(
                resolvedTenant, userId, username, LOGIN_TYPE_OAUTH2, clientId,
                HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest()),
                HttpRequestUtils.getUserAgent(HttpRequestUtils.currentRequest()),
                status, failReason));
        } catch (Exception e) {
            log.warn("记录 OAUTH2 登录日志失败（已隔离，不影响令牌流程）: tenantId={}, userId={}, clientId={}, error={}",
                resolvedTenant, userId, clientId, e.getMessage());
        }
    }

    /**
     * 获取有效的OAuth2客户端
     * <p>
     * 查询客户端并验证是否存在且激活状态。
     * </p>
     *
     * @param clientId 客户端ID
     * @return 客户端实体
     * @throws BizException 客户端无效或不存在
     */
    private SysOauth2Client getValidClient(String clientId) {
        SysOauth2Client client = oauth2ClientDomainService.findActiveByClientId(clientId);
        if (client == null) {
            throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CLIENT_INVALID.getMessage());
        }
        return client;
    }

    /**
     * 验证回调地址
     * <p>
     * 检查请求的回调地址是否在客户端注册的回调地址列表中。
     * 按RFC 8252规范验证：scheme + authority必须完全一致，路径需满足段匹配规则。
     * </p>
     *
     * @param client OAuth2客户端实体
     * @param redirectUri 请求的回调地址
     * @throws BizException 回调地址不匹配
     */
    private void validateRedirectUri(SysOauth2Client client, String redirectUri) {
        // 添加 redirectUri 的 null/空校验
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new BizException(AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode(),
                AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getMessage());
        }
        if (client.getRedirectUris() == null || client.getRedirectUris().isBlank()) {
            throw new BizException(AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode(),
                AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getMessage());
        }
        String[] uris = client.getRedirectUris().split(",");
        boolean matched = false;
        for (String uri : uris) {
            String trimmed = uri.trim();
            try {
                URI registered = new URI(trimmed);
                URI requested = new URI(redirectUri);
                // RFC 8252: scheme + authority 必须完全一致，路径需满足段匹配规则
                if (registered.getScheme().equals(requested.getScheme()) &&
                    registered.getAuthority().equals(requested.getAuthority()) &&
                    isPathAllowed(registered.getPath(), requested.getPath())) {
                    matched = true;
                    break;
                }
            } catch (URISyntaxException e) {
                // 记录警告日志（可能是攻击行为）
                log.warn("Invalid URI syntax in redirect_uri validation: registered={}, requested={}",
                         trimmed, redirectUri);
                continue;
            }
        }
        if (!matched) {
            throw new BizException(AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode(),
                AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getMessage());
        }
    }

    /**
     * 验证请求路径是否允许
     * <p>
     * RFC 8252路径匹配规则：请求路径必须以注册路径开头，且必须是完整路径段匹配。
     * 例如：注册路径/app，允许/app/callback，但拒绝/app-evil。
     * </p>
     *
     * @param registeredPath 注册路径
     * @param requestedPath 请求路径
     * @return 是否允许
     */
    private boolean isPathAllowed(String registeredPath, String requestedPath) {
        if (requestedPath == null || requestedPath.isEmpty()) {
            return false;
        }

        // 注册路径为空或根路径，允许任何请求路径
        if (registeredPath == null || registeredPath.isEmpty() || "/".equals(registeredPath)) {
            return true;
        }

        // 完全匹配
        if (registeredPath.equals(requestedPath)) {
            return true;
        }

        // 前缀匹配：必须确保是完整的路径段
        if (requestedPath.startsWith(registeredPath)) {
            // 完全匹配
            if (registeredPath.length() == requestedPath.length()) {
                return true;
            }
            // 检查注册路径是否以 / 结尾
            if (registeredPath.endsWith("/")) {
                return true; // 例如 /app/ 允许 /app/callback
            }
            // 检查请求路径在注册路径后是否紧跟着 /、? 或 #
            char nextChar = requestedPath.charAt(registeredPath.length());
            return nextChar == '/' || nextChar == '?' || nextChar == '#';
        }

        return false;
    }

    /**
     * 验证授权范围
     * <p>
     * 检查请求的授权范围是否在客户端注册的授权范围内。
     * 授权范围以空格分隔。
     * </p>
     *
     * @param client OAuth2客户端实体
     * @param scope 请求的授权范围（空格分隔）
     * @throws BizException 授权范围无效
     */
    private void validateScope(SysOauth2Client client, String scope) {
        if (client.getScopes() == null || client.getScopes().isBlank()) {
            return; // 无授权范围限制
        }
        String[] allowedScopes = client.getScopes().split(",");
        Set<String> allowedSet = new HashSet<>();
        for (String s : allowedScopes) {
            allowedSet.add(s.trim());
        }
        String[] requestedScopes = scope.split(" ");
        for (String s : requestedScopes) {
            if (!s.isBlank() && !allowedSet.contains(s.trim())) {
                throw new BizException(AdminErrorCode.OAUTH2_SCOPE_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_SCOPE_INVALID.getMessage());
            }
        }
    }

    /**
     * 检查授权类型列表是否包含目标类型
     * <p>
     * 解析逗号分隔的授权类型字符串，检查是否包含指定类型。
     * </p>
     *
     * @param grantTypes 授权类型字符串（逗号分隔）
     * @param targetType 目标授权类型
     * @return 是否包含目标类型
     */
    private boolean containsGrantType(String grantTypes, String targetType) {
        if (grantTypes == null || grantTypes.isBlank()) return false;
        for (String gt : grantTypes.split(",")) {
            if (gt.trim().equals(targetType)) return true;
        }
        return false;
    }

    /**
     * 授权码数据类
     * <p>
     * 存储授权码关联的用户ID、租户ID、回调地址、PKCE参数等信息。
     * </p>
     */
    @Getter
    @Setter
    public static class AuthCodeData {
        private String clientId;
        private long userId;
        private long tenantId;
        private String redirectUri;
        private String codeChallenge;
        private String codeChallengeMethod;
        private String scope;
    }

    /**
     * 刷新令牌数据类
     * <p>
     * 存储刷新令牌关联的用户ID、租户ID、客户端ID、授权范围等信息。
     * </p>
     */
    @Getter
    @Setter
    public static class RefreshTokenData {
        private long userId;
        private long tenantId;
        private String clientId;
        private String scope;
    }
}