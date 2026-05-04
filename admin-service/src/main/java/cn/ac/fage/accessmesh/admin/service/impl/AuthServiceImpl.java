package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.auth.CaptchaResp;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.admin.dto.auth.SmsLoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysLoginLogTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysOauth2ClientTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.*;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.service.AuthService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import static cn.ac.fage.accessmesh.admin.entity.table.SysLoginLogTableDef.SYS_LOGIN_LOG;
import static cn.ac.fage.accessmesh.admin.entity.table.SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT;
import static cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;
import static cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef.SYS_USER;

@Service
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_MINUTES = 30;

    // Lua脚本：INCR + EXPIRE 合并为原子操作，避免竞态条件
    private static final String LUA_INCREMENT_WITH_EXPIRE =
        "local count = redis.call('INCR', KEYS[1]) " +
        "if count == 1 then " +
        "    redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
        "end " +
        "return count";

    // Lua脚本：GET + DEL 合并为原子操作，确保验证码一次性使用
    private static final String LUA_GET_AND_DELETE =
        "local value = redis.call('GET', KEYS[1]) " +
        "if value then " +
        "    redis.call('DEL', KEYS[1]) " +
        "end " +
        "return value";

    private final SysUserMapper userMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final SysOauth2ClientMapper oauth2ClientMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final StringRedisTemplate redisTemplate;

    public AuthServiceImpl(SysUserMapper userMapper,
                           SysUserOrgMapper userOrgMapper,
                           SysOauth2ClientMapper oauth2ClientMapper,
                           SysLoginLogMapper loginLogMapper,
                           StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.userOrgMapper = userOrgMapper;
        this.oauth2ClientMapper = oauth2ClientMapper;
        this.loginLogMapper = loginLogMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public CaptchaResp generateCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String code = generateRandomCode(4);
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, code, 5, TimeUnit.MINUTES);
        String image = generateCaptchaImage(code);
        return new CaptchaResp(captchaId, image);
    }

    @Override
    public LoginResp login(LoginReq req) {
        validateCaptcha(req.captchaId(), req.captchaCode());
        SysOauth2Client client = validateClient(req.clientId());

        Long tenantId = Long.parseLong(req.tenantId());
        SysUser user = findUser(tenantId, req.username());
        checkAccountLocked(tenantId, req.username());
        if (user == null) {
            recordLoginFail(tenantId, req.username());
            recordLoginLog(tenantId, req.username(), req.clientId(), 0, "用户不存在");
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLoginLog(tenantId, req.username(), req.clientId(), 0, "用户已停用");
            throw new BizException(AdminErrorCode.USER_DISABLED.getCode(), AdminErrorCode.USER_DISABLED.getMessage());
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            recordLoginLog(tenantId, req.username(), req.clientId(), 0, "账号已锁定");
            throw new BizException(AdminErrorCode.USER_LOCKED.getCode(), AdminErrorCode.USER_LOCKED.getMessage());
        }
        if (user.getPassword() == null || !BCrypt.checkpw(req.password(), user.getPassword())) {
            recordLoginFail(tenantId, req.username());
            recordLoginLog(tenantId, req.username(), req.clientId(), 0, "密码错误");
            throw new BizException(AdminErrorCode.PASSWORD_INCORRECT.getCode(), AdminErrorCode.PASSWORD_INCORRECT.getMessage());
        }

        clearLoginFail(tenantId, req.username());
        StpUtil.login(user.getId());
        // FIX #1: Store tenantId in session for security validation
        SaSession session = StpUtil.getSession();
        session.set("tenantId", user.getTenantId());
        String token = StpUtil.getTokenValue();

        recordLoginLog(tenantId, req.username(), req.clientId(), 1, null);

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

    @Override
    public LoginResp smsLogin(SmsLoginReq req) {
        SysOauth2Client client = validateClient(req.clientId());
        Long tenantId = Long.parseLong(req.tenantId());

        validateSmsCode(req.phone(), req.smsCode());

        SysUser user = findUserByPhone(tenantId, req.phone());
        if (user == null) {
            recordLoginLog(tenantId, req.phone(), req.clientId(), 0, "用户不存在");
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLoginLog(tenantId, req.phone(), req.clientId(), 0, "用户已停用");
            throw new BizException(AdminErrorCode.USER_DISABLED.getCode(), AdminErrorCode.USER_DISABLED.getMessage());
        }

        StpUtil.login(user.getId());
        // FIX #1: Store tenantId in session for security validation (sms login)
        SaSession session = StpUtil.getSession();
        session.set("tenantId", user.getTenantId());
        String token = StpUtil.getTokenValue();

        recordLoginLog(tenantId, req.phone(), req.clientId(), 1, null);

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

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserInfoResp getUserInfo(Long userId) {
        // FIX #13: Validate userId belongs to current tenant
        Long currentTenantId = TenantContextHolder.getTenantId();
        SysUser user = userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_USER.ID.eq(userId))
                .and(SYS_USER.TENANT_ID.eq(currentTenantId))
                .and(SYS_USER.DELETE_FLAG.eq(0))
        );
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }

        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create().where(SYS_USER_ORG.USER_ID.eq(userId))
        );

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

    private SysOauth2Client validateClient(String clientId) {
        if (clientId == null) return null;
        SysOauth2Client client = oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_OAUTH2_CLIENT.CLIENT_ID.eq(clientId))
                .and(SYS_OAUTH2_CLIENT.STATUS.eq(1))
                .and(SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
        if (client == null) return null;
        if (!containsGrantType(client.getGrantTypes(), "password")) {
            throw new BizException(AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getCode(),
                AdminErrorCode.OAUTH2_GRANT_TYPE_NOT_SUPPORTED.getMessage());
        }
        return client;
    }

    private boolean containsGrantType(String grantTypes, String targetType) {
        if (grantTypes == null || grantTypes.isBlank()) return false;
        for (String gt : grantTypes.split(",")) {
            if (gt.trim().equals(targetType)) return true;
        }
        return false;
    }

    private SysUser findUser(Long tenantId, String username) {
        return userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_USER.TENANT_ID.eq(tenantId))
                .and(SYS_USER.USERNAME.eq(username))
                .and(SYS_USER.DELETE_FLAG.eq(0))
        );
    }

    private void recordLoginLog(Long tenantId, String username, String clientId, int status, String failReason) {
        SysLoginLog log = new SysLoginLog();
        log.setTenantId(tenantId);
        log.setUsername(username);
        log.setLoginType("password");
        log.setClientId(clientId);
        log.setStatus(status);
        log.setFailReason(failReason);
        log.setLoginAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    private void checkAccountLocked(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        Long failCount = redisTemplate.opsForValue().increment(key, 0);
        if (failCount != null && failCount >= MAX_LOGIN_FAIL_COUNT) {
            throw new BizException(AdminErrorCode.USER_LOCKED.getCode(),
                "登录失败次数过多，账号已锁定" + LOCK_DURATION_MINUTES + "分钟");
        }
    }

    private void recordLoginFail(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        // 使用 Lua 脚本原子性地执行 INCR + EXPIRE，避免竞态条件
        Long count = redisTemplate.execute(
            new DefaultRedisScript<>(LUA_INCREMENT_WITH_EXPIRE, Long.class),
            Collections.singletonList(key),
            String.valueOf(LOCK_DURATION_MINUTES * 60)  // TTL in seconds
        );

        if (count != null && count >= MAX_LOGIN_FAIL_COUNT) {
            // Mark user status as locked in DB
            SysUser user = findUser(tenantId, username);
            if (user != null) {
                SysUser update = new SysUser();
                update.setId(user.getId());
                update.setStatus(2);
                userMapper.update(update);
            }
        }
    }

    private void clearLoginFail(Long tenantId, String username) {
        String key = LOGIN_FAIL_PREFIX + tenantId + ":" + username;
        redisTemplate.delete(key);
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

    private SysUser findUserByPhone(Long tenantId, String phone) {
        return userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_USER.TENANT_ID.eq(tenantId))
                .and(SYS_USER.PHONE.eq(phone))
                .and(SYS_USER.DELETE_FLAG.eq(0))
        );
    }

    private String generateRandomCode(int length) {
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成验证码图片（PNG格式，Base64编码）
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
}
