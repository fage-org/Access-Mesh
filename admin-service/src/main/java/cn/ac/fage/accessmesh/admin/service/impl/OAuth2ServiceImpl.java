package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.oauth2.*;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.table.SysOauth2ClientTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef;

import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.admin.service.OAuth2Service;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.exception.BizException;

import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Service
public class OAuth2ServiceImpl implements OAuth2Service {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ServiceImpl.class);

    private static final String AUTH_CODE_PREFIX = "oauth2:code:";
    private static final String REFRESH_TOKEN_PREFIX = "oauth2:refresh:";
    private static final int AUTH_CODE_TTL_SECONDS = 300;

    // Lua脚本：GET + DEL 合并为原子操作，确保授权码/刷新令牌一次性使用
    private static final String LUA_GET_AND_DELETE =
        "local value = redis.call('GET', KEYS[1]) " +
        "if value then " +
        "    redis.call('DEL', KEYS[1]) " +
        "end " +
        "return value";

    private final SysOauth2ClientMapper oauth2ClientMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sa-token.jwt-secret-key}")
    private String jwtSecretKey;

    public OAuth2ServiceImpl(SysOauth2ClientMapper oauth2ClientMapper,
                             SysUserMapper userMapper,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.oauth2ClientMapper = oauth2ClientMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void validateJwtSecretKey() {
        if (jwtSecretKey == null || jwtSecretKey.isBlank()) {
            throw new IllegalStateException(
                "JWT secret key must be configured via JWT_SECRET_KEY environment variable");
        }
        if (jwtSecretKey.length() < 32) {
            throw new IllegalStateException(
                "JWT secret key must be at least 32 characters for security. Current length: " 
                + jwtSecretKey.length());
        }
        log.info("JWT secret key validated successfully, length: {}", jwtSecretKey.length());
    }

    @Override
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

    @Override
    public TokenResp token(TokenReq req) {
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

    @Override
    public TokenResp refreshToken(String refreshToken, String clientId, String clientSecret) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            // Validate client
            SysOauth2Client client = getValidClient(clientId);
            if (!BCrypt.checkpw(clientSecret, client.getClientSecret())) {
                throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_CLIENT_INVALID.getMessage());
            }

            // 使用 Lua 脚本原子性地获取并删除 refresh token，防止重复使用
            String refreshTokenDataJson = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_GET_AND_DELETE, String.class),
                Collections.singletonList(REFRESH_TOKEN_PREFIX + refreshToken)
            );

            if (refreshTokenDataJson == null) {
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            RefreshTokenData refreshTokenData;
            try {
                refreshTokenData = objectMapper.readValue(refreshTokenDataJson, RefreshTokenData.class);
            } catch (JsonProcessingException e) {
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            // Set tenant context from refresh token
            TenantContextHolder.setTenantId(refreshTokenData.getTenantId());

            // Verify client_id matches
            if (!refreshTokenData.getClientId().equals(clientId)) {
                throw new BizException(AdminErrorCode.OAUTH2_TOKEN_INVALID.getCode(),
                    AdminErrorCode.OAUTH2_TOKEN_INVALID.getMessage());
            }

            // Generate new access token
            String accessToken = generateAccessToken(refreshTokenData.getUserId(), clientId, refreshTokenData.getScope());
            int accessTokenTtl = client.getAccessTokenTtl() != null ? client.getAccessTokenTtl() : 86400;
            int refreshTokenTtl = client.getRefreshTokenTtl() != null ? client.getRefreshTokenTtl() : 604800;

            // Generate new refresh token
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
                throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(), "刷新令牌生成失败");
            }

            return new TokenResp(accessToken, "Bearer", accessTokenTtl, newRefreshToken, refreshTokenData.getScope());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Override
    public void revokeToken(String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            // For JWT tokens, we add to a blacklist in Redis
            String blackKey = "oauth2:blacklist:" + extractJti(accessToken);
            long ttl = getTokenRemainingTtl(accessToken);
            if (ttl > 0) {
                redisTemplate.opsForValue().set(blackKey, "1", ttl, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public OAuth2UserInfoResp getClientUserInfo(Long userId) {
        SysUser user = userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysUserTableDef.SYS_USER.ID.eq(userId))
                .and(SysUserTableDef.SYS_USER.DELETE_FLAG.eq(0))
        );
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

    private TokenResp tokenByAuthorizationCode(TokenReq req) {
        // 1. Validate client
        if (req.clientId() == null || req.clientId().isBlank()) {
            throw new BizException(AdminErrorCode.OAUTH2_MISSING_CLIENT.getCode(),
                AdminErrorCode.OAUTH2_MISSING_CLIENT.getMessage());
        }
        SysOauth2Client client = getValidClient(req.clientId());

        // 2. Validate client secret
        if (req.clientSecret() == null || !BCrypt.checkpw(req.clientSecret(), client.getClientSecret())) {
            throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CLIENT_INVALID.getMessage());
        }

        // 3. Validate grant type
        if (!containsGrantType(client.getGrantTypes(), "authorization_code")) {
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
            throw new BizException(AdminErrorCode.OAUTH2_CODE_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CODE_INVALID.getMessage());
        }

        AuthCodeData codeData;
        try {
            codeData = objectMapper.readValue(codeDataJson, AuthCodeData.class);
        } catch (JsonProcessingException e) {
            throw new BizException(AdminErrorCode.OAUTH2_CODE_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CODE_INVALID.getMessage());
        }

        // Set tenant context from auth code
        TenantContextHolder.setTenantId(codeData.getTenantId());

        // 5. Validate redirect_uri matches
        if (!codeData.getRedirectUri().equals(req.redirectUri())) {
            throw new BizException(AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode(),
                AdminErrorCode.OAUTH2_REDIRECT_MISMATCH.getMessage());
        }

        // 6. Validate PKCE if used
        if (codeData.getCodeChallenge() != null && !codeData.getCodeChallenge().isBlank()) {
            if (req.codeVerifier() == null || req.codeVerifier().isBlank()) {
                throw new BizException(AdminErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getCode(),
                    "缺少 code_verifier");
            }
            if (!verifyPkce(codeData.getCodeChallenge(), req.codeVerifier(), codeData.getCodeChallengeMethod())) {
                throw new BizException(AdminErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getCode(),
                    AdminErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getMessage());
            }
        }

        // 7. Generate tokens
        int accessTokenTtl = client.getAccessTokenTtl() != null ? client.getAccessTokenTtl() : 86400;
        int refreshTokenTtl = client.getRefreshTokenTtl() != null ? client.getRefreshTokenTtl() : 604800;
        String scope = codeData.getScope();

        String accessToken = generateAccessToken(codeData.getUserId(), req.clientId(), scope);
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // Store refresh token
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
            throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(), "刷新令牌生成失败");
        }

        return new TokenResp(accessToken, "Bearer", accessTokenTtl, refreshToken, scope);
    }

    private String generateAccessToken(long userId, String clientId, String scope) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("client_id", clientId);
        // 从 TenantContextHolder 获取实际的 tenantId
        Long tenantId = TenantContextHolder.getTenantId();
        extraData.put("tenant_id", tenantId != null ? String.valueOf(tenantId) : "0");
        if (scope != null && !scope.isBlank()) {
            extraData.put("scope", scope);
        }
        extraData.put("jti", UUID.randomUUID().toString().replace("-", ""));

        // SaJwtUtil.createToken(key, loginId, extraData, tokenType)
        return SaJwtUtil.createToken(jwtSecretKey, userId, extraData, "Bearer");
    }

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

    private SysOauth2Client getValidClient(String clientId) {
        SysOauth2Client client = oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.CLIENT_ID.eq(clientId))
                .and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.STATUS.eq(1))
                .and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
        if (client == null) {
            throw new BizException(AdminErrorCode.OAUTH2_CLIENT_INVALID.getCode(),
                AdminErrorCode.OAUTH2_CLIENT_INVALID.getMessage());
        }
        return client;
    }

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
     * 验证请求路径是否允许（RFC 8252 路径匹配规则）
     * 规则：请求路径必须以注册路径开头，且必须是完整路径段匹配
     * 例如：注册路径 /app，允许 /app/callback，但拒绝 /app-evil
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

    private void validateScope(SysOauth2Client client, String scope) {
        if (client.getScopes() == null || client.getScopes().isBlank()) {
            return; // no scope restriction
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

    private boolean containsGrantType(String grantTypes, String targetType) {
        if (grantTypes == null || grantTypes.isBlank()) return false;
        for (String gt : grantTypes.split(",")) {
            if (gt.trim().equals(targetType)) return true;
        }
        return false;
    }

    private String extractJti(String token) {
        try {
            // JWT format: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) return token;
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
            );
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            Object jti = payload.get("jti");
            return jti != null ? jti.toString() : token;
        } catch (Exception e) {
            return token;
        }
    }

    private long getTokenRemainingTtl(String token) {
        try {
            // JWT format: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length < 2) return 86400;
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
            );
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            Object exp = payload.get("exp");
            if (exp == null) return 86400;
            long expTime = exp instanceof Number ? ((Number) exp).longValue() : Long.parseLong(exp.toString());
            long remaining = expTime - System.currentTimeMillis() / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 86400;
        }
    }

    // Internal classes for serialization
    public static class AuthCodeData {
        private String clientId;
        private long userId;
        private long tenantId;
        private String redirectUri;
        private String codeChallenge;
        private String codeChallengeMethod;
        private String scope;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public long getTenantId() { return tenantId; }
        public void setTenantId(long tenantId) { this.tenantId = tenantId; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
        public String getCodeChallenge() { return codeChallenge; }
        public void setCodeChallenge(String codeChallenge) { this.codeChallenge = codeChallenge; }
        public String getCodeChallengeMethod() { return codeChallengeMethod; }
        public void setCodeChallengeMethod(String codeChallengeMethod) { this.codeChallengeMethod = codeChallengeMethod; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }

    public static class RefreshTokenData {
        private long userId;
        private long tenantId;
        private String clientId;
        private String scope;

        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public long getTenantId() { return tenantId; }
        public void setTenantId(long tenantId) { this.tenantId = tenantId; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }
}
