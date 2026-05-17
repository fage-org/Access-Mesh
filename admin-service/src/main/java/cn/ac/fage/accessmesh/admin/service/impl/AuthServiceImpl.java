package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.auth.CaptchaResp;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.admin.dto.auth.SmsLoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.AuthService;
import cn.ac.fage.accessmesh.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.common.model.PermResult;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 认证服务实现类
 * <p>
 * 提供用户登录认证相关的核心功能，包括验证码生成、密码登录、短信登录、
 * 用户信息获取、用户菜单获取等。
 * 实现了登录失败次数限制、账号锁定、验证码一次性使用等安全机制。
 * 使用Redis Lua脚本确保原子性操作，避免竞态条件。
 * 用户菜单和权限通过Feign调用permission-center服务获取。
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
    private final MenuDomainService menuDomainService;
    private final PermissionFeignClient permissionFeignClient;

    private static final String SUBJECT_TYPE_ADMIN_USER = "ADMIN_USER";
    private static final String OPERATION_VIEW = "VIEW";

    /**
     * 构造函数注入依赖
     *
     * @param userDomainService 用户领域服务，处理用户数据访问
     * @param userOrgDomainService 用户组织关联领域服务
     * @param oauth2ClientDomainService OAuth2客户端领域服务
     * @param loginLogDomainService 登录日志领域服务，记录登录成功/失败
     * @param redisTemplate Redis操作模板，用于验证码和登录失败计数
     * @param menuDomainService 菜单领域服务，获取菜单数据
     * @param permissionFeignClient 权限中心Feign客户端，获取用户角色和权限
     */
    public AuthServiceImpl(UserDomainService userDomainService,
                           UserOrgDomainService userOrgDomainService,
                           OAuth2ClientDomainService oauth2ClientDomainService,
                           LoginLogDomainService loginLogDomainService,
                           StringRedisTemplate redisTemplate,
                           MenuDomainService menuDomainService,
                           PermissionFeignClient permissionFeignClient) {
        this.userDomainService = userDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.oauth2ClientDomainService = oauth2ClientDomainService;
        this.loginLogDomainService = loginLogDomainService;
        this.redisTemplate = redisTemplate;
        this.menuDomainService = menuDomainService;
        this.permissionFeignClient = permissionFeignClient;
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
     * 账号锁定检查、密码校验、登录失败记录、Sa-Token会话创建。
     * 登录成功后清除失败计数，失败时累加计数并可能锁定账号。
     * </p>
     *
     * @param req 登录请求，包含租户ID、用户名、密码、验证码等
     * @return 登录响应，包含令牌、用户信息、是否强制重置密码等
     * @throws BizException 验证码错误、用户不存在、账号锁定、密码错误等
     */
    @Override
    public LoginResp login(LoginReq req) {
        validateCaptcha(req.captchaId(), req.captchaCode());
        SysOauth2Client client = validateClient(req.clientId());

        Long tenantId = Long.parseLong(req.tenantId());
        SysUser user = userDomainService.findByUsername(tenantId, req.username());
        checkAccountLocked(tenantId, req.username());
        if (user == null) {
            recordLoginFail(tenantId, req.username());
            loginLogDomainService.recordLoginLog(tenantId, req.username(), req.clientId(), 0, "用户不存在");
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            loginLogDomainService.recordLoginLog(tenantId, req.username(), req.clientId(), 0, "用户已停用");
            throw new BizException(AdminErrorCode.USER_DISABLED.getCode(), AdminErrorCode.USER_DISABLED.getMessage());
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            loginLogDomainService.recordLoginLog(tenantId, req.username(), req.clientId(), 0, "账号已锁定");
            throw new BizException(AdminErrorCode.USER_LOCKED.getCode(), AdminErrorCode.USER_LOCKED.getMessage());
        }
        if (user.getPassword() == null || !BCrypt.checkpw(req.password(), user.getPassword())) {
            recordLoginFail(tenantId, req.username());
            loginLogDomainService.recordLoginLog(tenantId, req.username(), req.clientId(), 0, "密码错误");
            throw new BizException(AdminErrorCode.PASSWORD_INCORRECT.getCode(), AdminErrorCode.PASSWORD_INCORRECT.getMessage());
        }

        clearLoginFail(tenantId, req.username());
        StpUtil.login(user.getId());
        // FIX #1: Store tenantId in session for security validation
        SaSession session = StpUtil.getSession();
        session.set("tenantId", user.getTenantId());
        String token = StpUtil.getTokenValue();

        loginLogDomainService.recordLoginLog(tenantId, req.username(), req.clientId(), 1, null);

        return new LoginResp(
            token,
            null,
            client != null ? client.getAccessTokenTtl() : 86400,
            "Bearer",
            user.getId(),
            user.getUsername(),
            user.getTenantId(),
            user.getForceResetPwd() != null && user.getForceResetPwd()
        );
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
        SysOauth2Client client = validateClient(req.clientId());
        Long tenantId = Long.parseLong(req.tenantId());

        validateSmsCode(req.phone(), req.smsCode());

        SysUser user = userDomainService.findByPhone(tenantId, req.phone());
        if (user == null) {
            loginLogDomainService.recordLoginLog(tenantId, req.phone(), req.clientId(), 0, "用户不存在");
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            loginLogDomainService.recordLoginLog(tenantId, req.phone(), req.clientId(), 0, "用户已停用");
            throw new BizException(AdminErrorCode.USER_DISABLED.getCode(), AdminErrorCode.USER_DISABLED.getMessage());
        }

        StpUtil.login(user.getId());
        // FIX #1: Store tenantId in session for security validation (sms login)
        SaSession session = StpUtil.getSession();
        session.set("tenantId", user.getTenantId());
        String token = StpUtil.getTokenValue();

        loginLogDomainService.recordLoginLog(tenantId, req.phone(), req.clientId(), 1, null);

        return new LoginResp(
            token,
            null,
            client != null ? client.getAccessTokenTtl() : 86400,
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
     * 检查账号是否被锁定
     * <p>
     * 检查Redis中的登录失败计数，达到上限则抛出账号锁定异常。
     * </p>
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @throws BizException 登录失败次数过多，账号已锁定
     */
    private void checkAccountLocked(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        Long failCount = redisTemplate.opsForValue().increment(key, 0);
        if (failCount != null && failCount >= MAX_LOGIN_FAIL_COUNT) {
            throw new BizException(AdminErrorCode.USER_LOCKED.getCode(),
                "登录失败次数过多，账号已锁定" + LOCK_DURATION_MINUTES + "分钟");
        }
    }

    /**
     * 记录登录失败
     * <p>
     * 使用Lua脚本原子性地累加失败计数并设置过期时间。
     * 达到上限时将用户状态更新为锁定状态。
     * </p>
     *
     * @param tenantId 租户ID
     * @param username 用户名
     */
    private void recordLoginFail(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        // 使用 Lua 脚本原子性地执行 INCR + EXPIRE，避免竞态条件
        Long count = redisTemplate.execute(
            new DefaultRedisScript<>(LUA_INCREMENT_WITH_EXPIRE, Long.class),
            Collections.singletonList(key),
            String.valueOf(LOCK_DURATION_MINUTES * 60)  // TTL in seconds
        );

        if (count != null && count >= MAX_LOGIN_FAIL_COUNT) {
            // 在数据库中标记用户状态为锁定
            SysUser user = userDomainService.findByUsername(tenantId, username);
            if (user != null) {
                userDomainService.batchUpdateStatus(tenantId, List.of(user.getId()), 2);
            }
        }
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
     * 通过permission-center获取用户角色和权限，批量校验菜单访问权限。
     * 构建前端路由格式的菜单树，包含子菜单自动继承父菜单可见性。
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

        // 2. 获取用户角色（通过 permission-center）
        List<String> roles = getUserRoles(tenantId, userId);

        // 3. 获取用户按钮级权限（通过 permission-center）
        List<String> permissions = getUserPermissions(tenantId, userId);

        // 4. 获取所有菜单
        List<SysMenu> allMenus = menuDomainService.selectAllValid(tenantId);

        // 5. 过滤用户有权限的菜单
        Set<Long> allowedMenuIds = filterAllowedMenus(tenantId, userId, allMenus);

        // 6. 构建菜单树
        List<UserMenuResp.MenuRouteItem> menus = buildMenuTree(allMenus, allowedMenuIds, 0L);

        return new UserMenuResp(menus, roles, permissions);
    }

    /**
     * 获取用户角色列表
     * <p>
     * 通过Feign调用permission-center获取用户关联的角色列表。
     * 失败时返回空列表并记录警告日志。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 角色名称列表
     */
    private List<String> getUserRoles(Long tenantId, Long userId) {
        try {
            cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleListReq req =
                new cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleListReq(
                    SUBJECT_TYPE_ADMIN_USER,
                    String.valueOf(userId)
                );
            PermResult<cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp> result =
                permissionFeignClient.getUserRoles(req);
            if (result != null && result.getData() != null && result.getData().roles() != null) {
                return result.getData().roles().stream()
                    .map(cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp.RoleSummary::roleName)
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to get user roles for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }

    /**
     * 获取用户按钮级权限列表
     * <p>
     * 通过Feign调用permission-center获取用户对MENU资源类型的操作权限。
     * 权限码格式为"资源编码:操作编码"，如"system:user:add"。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 权限码列表
     */
    private List<String> getUserPermissions(Long tenantId, Long userId) {
        try {
            cn.ac.fage.accessmesh.perm.common.dto.req.UserPermissionViewReq req =
                new cn.ac.fage.accessmesh.perm.common.dto.req.UserPermissionViewReq(
                    "USER",
                    SUBJECT_TYPE_ADMIN_USER,
                    String.valueOf(userId),
                    null,
                    null,
                    null,
                    List.of("MENU"),
                    null,
                    null,
                    null,
                    Boolean.FALSE,
                    Boolean.FALSE,
                    Boolean.FALSE,
                    null,
                    1,
                    100
                );
            PermResult<cn.ac.fage.accessmesh.perm.common.dto.resp.PermissionEffectivePermissionsResp<Map<String, Object>>> result =
                permissionFeignClient.getEffectivePermissions(req);
            if (result != null && result.getData() != null && result.getData().items() != null) {
                Set<String> permCodes = new HashSet<>();
                for (Map<String, Object> item : result.getData().items()) {
                    Object opCodeObj = item.get("operationCode");
                    Object resourceCodeObj = item.get("resourceCode");
                    if (opCodeObj != null && resourceCodeObj != null) {
                        permCodes.add(resourceCodeObj.toString() + ":" + opCodeObj.toString());
                    }
                }
                return new ArrayList<>(permCodes);
            }
        } catch (Exception e) {
            log.warn("Failed to get user permissions for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }

    /**
     * 过滤用户有权限访问的菜单
     * <p>
     * 通过批量权限校验获取用户可访问的菜单ID集合。
     * 自动补充父菜单ID，确保菜单树完整性（子菜单有权限时父菜单也显示）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param allMenus 所有菜单列表
     * @return 用户有权限访问的菜单ID集合
     */
    private Set<Long> filterAllowedMenus(Long tenantId, Long userId, List<SysMenu> allMenus) {
        if (allMenus.isEmpty()) {
            return Set.of();
        }

        // 构建批量权限检查请求
        List<cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq.AuthCheckItem> items = allMenus.stream()
            .filter(m -> m.getMenuType() != null && !"3".equals(m.getMenuType())) // 排除按钮类型
            .map(m -> new cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq.AuthCheckItem(
                AdminResourceType.MENU,
                String.valueOf(m.getId()),
                OPERATION_VIEW,
                null,
                null,
                null
            ))
            .collect(Collectors.toList());

        if (items.isEmpty()) {
            return Set.of();
        }

        try {
            cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq req =
                new cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq(
                    SUBJECT_TYPE_ADMIN_USER,
                    String.valueOf(userId),
                    items,
                    null
                );
            PermResult<cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp> result =
                permissionFeignClient.batchCheckAuth(req);
            if (result != null && result.getData() != null && result.getData().items() != null) {
                Set<Long> allowed = new HashSet<>();
                for (cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp.AuthCheckItemResult check : result.getData().items()) {
                    if (check.allowed()) {
                        try {
                            allowed.add(Long.valueOf(check.resourceCode()));
                        } catch (NumberFormatException e) {
                            // 忽略无效的 resourceCode
                        }
                    }
                }
                // 补充父菜单（即使父菜单没有权限，只要子菜单有权限就显示）
                Set<Long> withParents = new HashSet<>(allowed);
                for (SysMenu menu : allMenus) {
                    if (allowed.contains(menu.getId()) && menu.getParentId() != null && menu.getParentId() > 0) {
                        addParentMenus(allMenus, menu.getParentId(), withParents);
                    }
                }
                return withParents;
            }
        } catch (Exception e) {
            log.warn("Failed to filter allowed menus for tenant={}, userId={}", tenantId, userId, e);
        }
        return Set.of();
    }

    /**
     * 递归添加父菜单
     * <p>
     * 从子菜单向上递归，将所有祖先菜单ID添加到集合中。
     * 确保菜单树的层级结构完整。
     * </p>
     *
     * @param allMenus 所有菜单列表
     * @param parentId 当前要添加的父菜单ID
     * @param withParents 菜单ID集合（会被修改）
     */
    private void addParentMenus(List<SysMenu> allMenus, Long parentId, Set<Long> withParents) {
        for (SysMenu menu : allMenus) {
            if (menu.getId().equals(parentId)) {
                withParents.add(menu.getId());
                if (menu.getParentId() != null && menu.getParentId() > 0) {
                    addParentMenus(allMenus, menu.getParentId(), withParents);
                }
                break;
            }
        }
    }

    /**
     * 构建菜单树
     * <p>
     * 将菜单列表转换为前端路由格式的树形结构。
     * 只包含用户有权限、可见、启用状态的菜单，按排序字段排序。
     * </p>
     *
     * @param allMenus 所有菜单列表
     * @param allowedIds 用户有权限的菜单ID集合
     * @param parentId 当前层级父菜单ID（0表示根级）
     * @return 菜单路由项列表
     */
    private List<UserMenuResp.MenuRouteItem> buildMenuTree(List<SysMenu> allMenus, Set<Long> allowedIds, Long parentId) {
        return allMenus.stream()
            .filter(m -> parentId.equals(m.getParentId() != null ? m.getParentId() : 0L))
            .filter(m -> allowedIds.contains(m.getId()))
            .filter(m -> m.getVisible() != null && m.getVisible()) // 只显示可见菜单
            .filter(m -> m.getStatus() != null && m.getStatus() == 1) // 只显示启用菜单
            .sorted((a, b) -> {
                int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                return Integer.compare(orderA, orderB);
            })
            .map(m -> {
                List<UserMenuResp.MenuRouteItem> children = buildMenuTree(allMenus, allowedIds, m.getId());
                UserMenuResp.MetaInfo meta = new UserMenuResp.MetaInfo(
                    m.getName(),
                    m.getIcon(),
                    m.getSortOrder(),
                    m.getVisible(),
                    m.getIsCache() != null ? m.getIsCache() : false,
                    m.getIsExternal() != null && m.getIsExternal() ? m.getPath() : null,
                    null, // roles 由前端根据用户角色判断
                    m.getPermCode() != null ? List.of(m.getPermCode()) : null
                );
                return new UserMenuResp.MenuRouteItem(
                    m.getPath(),
                    generateRouteName(m),
                    m.getComponent(),
                    !children.isEmpty() ? children.get(0).path() : null,
                    meta,
                    children
                );
            })
            .collect(Collectors.toList());
    }

    /**
     * 生成路由名称
     * <p>
     * 根据菜单类型和路径生成前端路由的name属性。
     * 目录类型自动添加"Parent"后缀，菜单类型使用路径转换为驼峰命名。
     * </p>
     *
     * @param menu 菜单实体
     * @return 路由名称字符串
     */
    private String generateRouteName(SysMenu menu) {
        if (menu.getMenuType() != null && "1".equals(menu.getMenuType())) {
            // 目录类型：自动生成名称
            return menu.getPath().replace("/", "_").replaceAll("^_", "") + "Parent";
        }
        // 菜单类型：使用路径生成名称
        String path = menu.getPath();
        if (path == null || path.isBlank()) {
            return "Menu" + menu.getId();
        }
        // 将路径转换为驼峰命名
        String[] parts = path.split("/");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                if (name.isEmpty()) {
                    name.append(part);
                } else {
                    name.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        name.append(part.substring(1));
                    }
                }
            }
        }
        return name.toString();
    }
}