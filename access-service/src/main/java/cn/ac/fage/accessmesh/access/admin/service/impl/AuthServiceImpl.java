package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.auth.CaptchaResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.access.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.SmsLoginReq;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.AuthService;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService.LoginLogEntry;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 认证服务实现类
 * <p>
 * 提供用户登录认证相关的核心功能，包括验证码生成、密码登录、短信登录、
 * 用户信息获取、用户菜单获取等。
 * 实现了登录失败计数、临时锁定（计数键即锁，键过期自动恢复，不落库）、
 * 验证码一次性使用等安全机制（T-ADMIN-022）。
 * 使用Redis Lua脚本确保原子性操作，避免竞态条件。
 * 用户菜单和权限通过 UserMenuQueryService 跨域聚合查询获取（T-ACCESS-006）。
 * </p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_MINUTES = 30;

    /** 登录方式（对齐 sys_login_log.login_type 列注释：PASSWORD/SMS/OAUTH2） */
    private static final String LOGIN_TYPE_PASSWORD = "PASSWORD";
    private static final String LOGIN_TYPE_SMS = "SMS";

    /**
     * Lua脚本：INCR + EXPIRE 合并为原子操作
     * <p>
     * 避免INCR和EXPIRE之间的竞态条件，确保计数器正确设置过期时间。
     * </p>
     */
    private static final String LUA_INCREMENT_WITH_EXPIRE =
        "local count = redis.call('INCR', KEYS[1]) " +
        "if count == 1 then " +
        "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
        "end " +
        "return count";

    /**
     * Lua脚本：GET + DEL 合并为原子操作
     * <p>
     * 确保验证码一次性使用，获取后立即删除，防止重复验证。
     * </p>
     */
    private static final String LUA_GET_AND_DELETE =
        "local value = redis.call('GET', KEYS[1]) " +
        "if value then " +
        "    redis.call('DEL', KEYS[1]) " +
        "end " +
        "return value";

    private final UserDomainService userDomainService;
    private final UserOrgDomainService userOrgDomainService;
    private final OAuth2ClientDomainService oauth2ClientDomainService;
    private final LoginLogDomainService loginLogDomainService;
    private final StringRedisTemplate redisTemplate;
    private final UserMenuQueryService userMenuQueryService;

    /**
     * 平台用户会话过期展示口径（秒）。
     * <p>
     * 单一权威来源 = {@link SaManager#getConfig()}
     * 的 sa-token.timeout（真实会话 TTL，配置驱动）。原独立配置键
     * access.session.expires-in-seconds 与 sa-token.timeout 双源耦合，Nacos 只覆盖
     * 其中一项时 LoginResp.expiresIn 与实际会话漂移。OAuth2 /oauth2/token 的
     * access_token 有效期继续用客户端注册 TTL（架构 §6.1，不套用本口径）。
     * </p>
     */
    private long expiresInSeconds() {
        return SaManager.getConfig().getTimeout();
    }

    // 本地登录主体类型码：单一事实源 LocalProjectionOwner.SUBJECT_LOCAL_USER（评审 P3，不另设字面量）

    /**
     * 构造函数注入依赖
     *
     * @param userDomainService 用户领域服务，处理用户数据访问
     * @param userOrgDomainService 用户组织关联领域服务
     * @param oauth2ClientDomainService OAuth2客户端领域服务
     * @param loginLogDomainService 登录日志领域服务，记录登录成功/失败
     * @param redisTemplate Redis操作模板，用于验证码和登录失败计数
     * @param userMenuQueryService 跨域用户菜单聚合查询服务（/auth/user-menu）
     */
    public AuthServiceImpl(UserDomainService userDomainService,
                           UserOrgDomainService userOrgDomainService,
                           OAuth2ClientDomainService oauth2ClientDomainService,
                           LoginLogDomainService loginLogDomainService,
                           StringRedisTemplate redisTemplate,
                           UserMenuQueryService userMenuQueryService) {
        this.userDomainService = userDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.oauth2ClientDomainService = oauth2ClientDomainService;
        this.loginLogDomainService = loginLogDomainService;
        this.redisTemplate = redisTemplate;
        this.userMenuQueryService = userMenuQueryService;
    }

    /**
     * 生成图形验证码
     * <p>
     * 生成随机4位数字验证码，存储到Redis中5分钟有效期。
     * 返回验证码ID和Base64编码的PNG图片，前端通过图片展示验证码。
     * </p>
     *
     * @return 验证码响应，包含验证码ID和图片Base64字符串
     */
    @Override
    public CaptchaResp generateCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String code = generateRandomCode(4);
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, code, 5, TimeUnit.MINUTES);
        String image = generateCaptchaImage(code);
        return new CaptchaResp(captchaId, image);
    }

    /**
     * 用户密码登录
     * <p>
     * 执行完整的密码登录流程：验证码校验、客户端校验、用户查询、
     * 停用检查（管理员手工启停，优先于临时锁定提示）、临时锁定检查
     * （失败计数键）、密码校验、登录失败记录、Sa-Token会话创建。
     * 登录成功后清除失败计数；失败时累加计数，达到阈值后凭键剩余 TTL
     * 临时锁定，键过期自动恢复（T-ADMIN-022）。
     * </p>
     *
     * @param req 登录请求，包含租户ID、用户名、密码、验证码等
     * @return 登录响应，包含令牌、用户信息、是否强制重置密码等
     * @throws BizException 验证码错误、用户不存在、用户已停用、账号临时锁定、密码错误等
     */
    @Override
    public LoginResp login(LoginReq req) {
        validateCaptcha(req.captchaId(), req.captchaCode());
        validateClient(req.clientId());

        Long tenantId = Long.parseLong(req.tenantId());
        SysUser user = userDomainService.findByUsername(tenantId, req.username());
        if (user == null) {
            recordLoginFail(tenantId, req.username());
            safeRecordLoginLog(tenantId, null, req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 0, "用户不存在");
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        // 停用检查用 status != 1 fail-closed：仅 0/1 收口后任何未定义值
        // 都不应进入会话（与投影 isEnabled(status)==1 对齐，防止认证放行+主体停用分裂）
        if (user.getStatus() == null || user.getStatus() != 1) {
            safeRecordLoginLog(tenantId, user.getId(), req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 0, "用户已停用");
            throw new BizException(AdminErrorCode.USER_DISABLED.getCode(), AdminErrorCode.USER_DISABLED.getMessage());
        }
        if (isAccountLocked(tenantId, req.username())) {
            // T-ADMIN-022：计数键即锁（临时，键过期自动恢复），拒绝时补记登录日志留审计痕迹；
            // 停用（管理员事实）优先于临时锁定提示，避免重叠时误导「30分钟后重试」
            safeRecordLoginLog(tenantId, user.getId(), req.username(),
                LOGIN_TYPE_PASSWORD, req.clientId(), 0, "登录失败次数过多，账号临时锁定");
            throw new BizException(AdminErrorCode.USER_LOCKED.getCode(),
                "登录失败次数过多，账号已临时锁定，请" + LOCK_DURATION_MINUTES + "分钟后重试");
        }
        if (user.getPassword() == null || !BCrypt.checkpw(req.password(), user.getPassword())) {
            recordLoginFail(tenantId, req.username());
            safeRecordLoginLog(tenantId, user.getId(), req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 0, "密码错误");
            throw new BizException(AdminErrorCode.PASSWORD_INCORRECT.getCode(), AdminErrorCode.PASSWORD_INCORRECT.getMessage());
        }

        clearLoginFail(tenantId, req.username());
        StpUtil.login(user.getId());
        // FIX #1: Store tenantId in session for security validation
        SaSession session = StpUtil.getSession();
        session.set("tenantId", user.getTenantId());
        session.set("subjectTypeCode", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        // 操作者名称供 @OperationLog AOP 会话回填（未登录/无会话调用为 null）
        session.set("operatorName", user.getUsername());
        String token = StpUtil.getTokenValue();

        safeRecordLoginLog(tenantId, user.getId(), req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 1, null);

        return new LoginResp(
            token,
            null,
            expiresInSeconds(),
            "Bearer",
            user.getId(),
            user.getUsername(),
            user.getTenantId(),
            user.getForceResetPwd() != null && user.getForceResetPwd()
        );
    }

    /**
     * 记录登录日志（失败隔离，调用方兜底）
     * <p>
     * 日志写入经 REQUIRES_NEW 独立短事务；方法体不吞异常，异常（含 Spring 代理层
     * commit 阶段的连接中断/rollback-only）自然传播到本方法，由本方法 try-catch 兜底：
     * 日志失败仅告警，不阻断登录主流程（失败分支的 BizException 不被日志异常覆盖）。
     * 客户端 IP/User-Agent 从当前请求上下文提取（无请求上下文时为空）。
     * </p>
     */
    private void safeRecordLoginLog(Long tenantId, Long userId, String username, String loginType,
                                    String clientId, Integer status, String failReason) {
        HttpServletRequest request = HttpRequestUtils.currentRequest();
        try {
            loginLogDomainService.recordLoginLog(new LoginLogEntry(
                tenantId, userId, username, loginType, clientId,
                HttpRequestUtils.getClientIp(request),
                HttpRequestUtils.getUserAgent(request),
                status, failReason));
        } catch (Exception e) {
            log.warn("记录登录日志失败（已隔离，不影响登录流程）: tenantId={}, username={}, status={}, error={}",
                tenantId, username, status, e.getMessage());
        }
    }

    /**
     * 用户短信验证码登录
     * <p>
     * 通过手机号和短信验证码登录，无需密码。
     * 验证短信验证码后查询用户，创建Sa-Token会话。
     * </p>
     *
     * @param req 短信登录请求，包含租户ID、手机号、短信验证码等
     * @return 登录响应，包含令牌、用户信息等
     * @throws BizException 短信验证码错误、用户不存在、用户已停用等
     */
    @Override
    public LoginResp smsLogin(SmsLoginReq req) {
        validateClient(req.clientId());
        Long tenantId = Long.parseLong(req.tenantId());

        validateSmsCode(req.phone(), req.smsCode());

        SysUser user = userDomainService.findByPhone(tenantId, req.phone());
        if (user == null) {
            // 手机号为 PII：登录失败不落完整明文，仅存掩码（避免 sys_login_log.username 明文泄漏）
            safeRecordLoginLog(tenantId, null, maskPhone(req.phone()), LOGIN_TYPE_SMS, req.clientId(), 0, "用户不存在");
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            safeRecordLoginLog(tenantId, user.getId(), user.getUsername(), LOGIN_TYPE_SMS, req.clientId(), 0, "用户已停用");
            throw new BizException(AdminErrorCode.USER_DISABLED.getCode(), AdminErrorCode.USER_DISABLED.getMessage());
        }

        StpUtil.login(user.getId());
        // FIX #1: Store tenantId in session for security validation (sms login)
        SaSession session = StpUtil.getSession();
        session.set("tenantId", user.getTenantId());
        session.set("subjectTypeCode", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        // 操作者名称供 @OperationLog AOP 会话回填（未登录/无会话调用为 null）
        session.set("operatorName", user.getUsername());
        String token = StpUtil.getTokenValue();

        safeRecordLoginLog(tenantId, user.getId(), user.getUsername(), LOGIN_TYPE_SMS, req.clientId(), 1, null);

        return new LoginResp(
            token,
            null,
            expiresInSeconds(),
            "Bearer",
            user.getId(),
            user.getUsername(),
            user.getTenantId(),
            user.getForceResetPwd() != null && user.getForceResetPwd()
        );
    }

    /**
     * 用户登出
     * <p>
     * 清除当前用户的Sa-Token会话，使令牌失效。
     * </p>
     */
    @Override
    public void logout() {
        StpUtil.logout();
    }

    /**
     * 获取用户信息
     * <p>
     * 根据用户ID获取用户基本信息，包括用户名、姓名、手机号、邮箱、头像等。
     * 同时获取用户关联的组织信息列表。
     * 验证用户ID属于当前租户。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息响应
     * @throws BizException 用户不存在
     */
    @Override
    public UserInfoResp getUserInfo(Long userId) {
        // FIX #13: Validate userId belongs to current tenant
        Long currentTenantId = TenantContextHolder.getTenantId();
        SysUser user = userDomainService.selectValidById(currentTenantId, userId);
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }

        List<SysUserOrg> userOrgs = userOrgDomainService.findByUserId(currentTenantId, userId);

        List<UserInfoResp.OrgInfo> orgInfos = userOrgs.stream()
            .map(uo -> new UserInfoResp.OrgInfo(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());

        return new UserInfoResp(
            user.getId(),
            user.getTenantId(),
            user.getUsername(),
            user.getName(),
            user.getPhone(),
            user.getEmail(),
            user.getAvatar(),
            List.of(),
            List.of(),
            orgInfos
        );
    }

    /**
     * 验证图形验证码
     * <p>
     * 使用Lua脚本原子性地获取并删除验证码，确保一次性使用。
     * 验证码过期或错误时抛出异常。
     * </p>
     *
     * @param captchaId 验证码ID
     * @param captchaCode 用户输入的验证码
     * @throws BizException 验证码参数缺失、验证码错误或已过期
     */
    private void validateCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            throw new BizException(AdminErrorCode.CAPTCHA_INCORRECT.getCode(), "验证码参数缺失");
        }

        String key = CAPTCHA_KEY_PREFIX + captchaId;
        // 使用 Lua 脚本原子性地获取并删除验证码，确保一次性使用
        String stored = redisTemplate.execute(
            new DefaultRedisScript<>(LUA_GET_AND_DELETE, String.class),
            Collections.singletonList(key)
        );

        if (stored == null || !stored.equalsIgnoreCase(captchaCode)) {
            throw new BizException(AdminErrorCode.CAPTCHA_INCORRECT.getCode(), AdminErrorCode.CAPTCHA_INCORRECT.getMessage());
        }
    }

    /**
     * 验证OAuth2客户端
     * <p>
     * 检查客户端是否存在且支持密码授权类型。
     * 如果客户端ID为空则跳过验证。
     * </p>
     *
     * @param clientId 客户端ID
     * @return 客户端实体，如果不存在或不支持密码授权则返回null
     * @throws BizException 客户端不支持密码授权类型
     */
    private SysOauth2Client validateClient(String clientId) {
        if (clientId == null) return null;
        SysOauth2Client client = oauth2ClientDomainService.findActiveByClientId(clientId);
        if (client == null) return null;
        if (!containsGrantType(client.getGrantTypes(), "password")) {
            throw new BizException(AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getCode(),
                AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getMessage());
        }
        return client;
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
     * 检查账号是否处于临时锁定
     * <p>
     * 读取现有失败计数键（GET 只读，不创建键——旧实现 increment(key, 0) 会为
     * 不存在用户创建无 TTL 的零值键）。计数达到上限即锁定，键的剩余 TTL 即
     * 剩余锁定时长，键过期自动恢复可登录（T-ADMIN-022：计数键即锁，不落库、
     * 不新增第二个锁键）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return true 表示锁定中，应拒绝登录
     */
    private boolean isAccountLocked(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        String failCount = redisTemplate.opsForValue().get(key);
        if (failCount == null) {
            return false;
        }
        // 计数键由本服务 Lua INCR 写入，正常必为数字；脏值按未锁定处理并告警
        try {
            return Long.parseLong(failCount) >= MAX_LOGIN_FAIL_COUNT;
        } catch (NumberFormatException e) {
            log.warn("登录失败计数键存在非数字值，按未锁定处理: key={}, value={}", key, failCount);
            return false;
        }
    }

    /**
     * 记录登录失败
     * <p>
     * 使用Lua脚本原子性地累加失败计数并设置过期时间。
     * 计数键即锁：达到上限后的拒绝由 {@link #isAccountLocked} 依据计数判定，
     * 键过期自动恢复，不再持久化锁定状态到 sys_user.status
     * （T-ADMIN-022 删除 sys_user.status=2 写入与投影禁用编排）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param username 用户名
     */
    private void recordLoginFail(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        // 使用 Lua 脚本原子性地执行 INCR + EXPIRE，避免竞态条件
        redisTemplate.execute(
            new DefaultRedisScript<>(LUA_INCREMENT_WITH_EXPIRE, Long.class),
            Collections.singletonList(key),
            String.valueOf(LOCK_DURATION_MINUTES * 60)  // TTL in seconds
        );
    }

    /**
     * 清除登录失败计数
     * <p>
     * 登录成功后清除Redis中的失败计数记录。
     * </p>
     *
     * @param tenantId 租户ID
     * @param username 用户名
     */
    private void clearLoginFail(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        redisTemplate.delete(key);
    }

    /**
     * 验证短信验证码
     * <p>
     * 使用Lua脚本原子性地获取并删除短信验证码，确保一次性使用。
     * </p>
     *
     * @param phone 手机号
     * @param smsCode 短信验证码
     * @throws BizException 短信验证码参数缺失、错误或已过期
     */
    /**
     * 手机号掩码：保留前 3 位与后 4 位，中间替换为 {@code ****}（11 位标准手机号）。
     * <p>
     * 用于短信登录失败等场景的日志记录——手机号为 PII，避免完整明文写入
     * sys_login_log.username。超短或不规范输入原样返回，宁可不掩码
     * 也不抛错阻断登录流程。
     * </p>
     *
     * @param phone 手机号
     * @return 掩码后的手机号；null 返回 null
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private void validateSmsCode(String phone, String smsCode) {
        if (phone == null || smsCode == null) {
            throw new BizException(AdminErrorCode.CAPTCHA_INCORRECT.getCode(), "短信验证码参数缺失");
        }

        String key = SMS_CODE_PREFIX + phone;
        // 使用 Lua 脚本原子性地获取并删除短信验证码，确保一次性使用
        String stored = redisTemplate.execute(
            new DefaultRedisScript<>(LUA_GET_AND_DELETE, String.class),
            Collections.singletonList(key)
        );

        if (stored == null || !stored.equals(smsCode)) {
            throw new BizException(AdminErrorCode.CAPTCHA_INCORRECT.getCode(), "短信验证码错误或已过期");
        }
    }

    /**
     * 生成随机数字验证码
     * <p>
     * 使用SecureRandom生成指定长度的纯数字验证码。
     * </p>
     *
     * @param length 验证码长度
     * @return 随机数字验证码字符串
     */
    private String generateRandomCode(int length) {
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成验证码图片
     * <p>
     * 创建包含干扰线、噪点和随机旋转字符的验证码图片。
     * 图片尺寸120x40，PNG格式，Base64编码返回。
     * </p>
     *
     * @param code 验证码文本
     * @return Base64编码的PNG图片字符串（带data:image/png;base64前缀）
     */
    private String generateCaptchaImage(String code) {
        int width = 120;
        int height = 40;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景色（浅灰）
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(0, 0, width, height);

        // 绘制干扰线
        for (int i = 0; i < 8; i++) {
            g2d.setColor(new Color(
                SECURE_RANDOM.nextInt(100) + 100,
                SECURE_RANDOM.nextInt(100) + 100,
                SECURE_RANDOM.nextInt(100) + 100
            ));
            int x1 = SECURE_RANDOM.nextInt(width);
            int y1 = SECURE_RANDOM.nextInt(height);
            int x2 = SECURE_RANDOM.nextInt(width);
            int y2 = SECURE_RANDOM.nextInt(height);
            g2d.drawLine(x1, y1, x2, y2);
        }

        // 绘制噪点
        for (int i = 0; i < 50; i++) {
            g2d.setColor(new Color(
                SECURE_RANDOM.nextInt(150) + 100,
                SECURE_RANDOM.nextInt(150) + 100,
                SECURE_RANDOM.nextInt(150) + 100
            ));
            int x = SECURE_RANDOM.nextInt(width);
            int y = SECURE_RANDOM.nextInt(height);
            g2d.fillOval(x, y, 2, 2);
        }

        // 绘制验证码字符
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        int charWidth = width / code.length();
        for (int i = 0; i < code.length(); i++) {
            // 每个字符颜色略有不同
            g2d.setColor(new Color(
                SECURE_RANDOM.nextInt(50) + 30,
                SECURE_RANDOM.nextInt(50) + 30,
                SECURE_RANDOM.nextInt(50) + 80
            ));
            // 字符位置随机偏移
            int x = charWidth * i + SECURE_RANDOM.nextInt(10) - 5;
            int y = height / 2 + SECURE_RANDOM.nextInt(10) - 5 + 8;
            // 字符随机旋转
            double angle = (SECURE_RANDOM.nextDouble() - 0.5) * 0.3;
            g2d.rotate(angle, x + charWidth / 2, y);
            g2d.drawString(String.valueOf(code.charAt(i)), x, y);
            g2d.rotate(-angle, x + charWidth / 2, y);
        }

        g2d.dispose();

        // 转换为 Base64
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (java.io.IOException e) {
            // 图片生成失败时返回空白图片（不应影响正常流程）
            return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        }
    }

    /**
     * 获取用户菜单
     * <p>
     * 获取用户可访问的菜单树、角色列表和按钮级权限列表。
     * 跨域组合逻辑（菜单树构建 + 角色/权限聚合 + 菜单可见性判定）集中在
     * {@link UserMenuQueryService#buildUserMenuTree}（T-ACCESS-006）。
     * 本方法仅保留用户存在性校验（登录链路 admin 域职责）。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户菜单响应，包含菜单树、角色列表、权限列表
     * @throws BizException 用户不存在
     */
    @Override
    public UserMenuResp getUserMenu(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 1. 获取用户信息
        SysUser user = userDomainService.selectValidById(tenantId, userId);
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }

        // 2. 跨域聚合：菜单树 + 角色 + 权限码（一次加载保证同一权限快照）
        return userMenuQueryService.buildUserMenuTree(userId);
    }
}
