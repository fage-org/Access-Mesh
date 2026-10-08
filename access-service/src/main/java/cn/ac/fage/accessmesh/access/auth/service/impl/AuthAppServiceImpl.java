package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.CaptchaResp;
import cn.ac.fage.accessmesh.access.auth.dto.LoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.LoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.UserInfoResp;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.menu.dto.resp.UserMenuResp;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.org.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import cn.ac.fage.accessmesh.access.auth.service.AuthAppService;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService.LoginLogEntry;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessDeniedException;
import cn.ac.fage.accessmesh.access.tenant.service.TenantGateUnavailableException;
import cn.ac.fage.accessmesh.access.auth.security.LoginChallengeSupport;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import cn.ac.fage.accessmesh.common.security.TenantSessionStamp;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.menu.service.UserMenuQueryAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
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
 * 提供用户登录认证相关的核心功能，包括验证码生成、密码登录（短信登录已删除）、
 * 用户信息获取、用户菜单获取等。
 * 实现了登录失败计数、临时锁定（计数键即锁，键过期自动恢复，不落库）、
 * 验证码一次性使用等安全机制（T-ADMIN-022）。
 * 使用Redis Lua脚本确保原子性操作，避免竞态条件。
 * 用户菜单和权限通过 UserMenuQueryAppService 跨域聚合查询获取（T-ACCESS-006）。
 * </p>
 */
@Service
public class AuthAppServiceImpl implements AuthAppService {

    private static final Logger log = LoggerFactory.getLogger(AuthAppServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CAPTCHA_KEY_PREFIX = LoginChallengeSupport.CAPTCHA_PREFIX;

    /** 登录方式（对齐 sys_login_log.login_type 列注释：PASSWORD/OAUTH2；SMS 为历史值，登录链路现只写 PASSWORD） */
    private static final String LOGIN_TYPE_PASSWORD = "PASSWORD";

    private final UserDomainService userDomainService;
    private final UserOrgDomainService userOrgDomainService;
    private final TenantDomainService tenantDomainService;
    private final TenantAccessGuard tenantAccess;
    private final LoginFailureStore loginFailures;
    private final LoginLogDomainService loginLogDomainService;
    private final StringRedisTemplate redisTemplate;
    private final UserMenuQueryAppService userMenuQueryService;

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
     * @param tenantDomainService 租户编码解析
     * @param tenantAccess 租户即时门禁
     * @param loginLogDomainService 登录日志领域服务，记录登录成功/失败
     * @param redisTemplate Redis操作模板，用于验证码和登录失败计数
     * @param userMenuQueryService 跨域用户菜单聚合查询服务（/auth/user-menu）
     */
    public AuthAppServiceImpl(UserDomainService userDomainService,
                           UserOrgDomainService userOrgDomainService,
                           TenantDomainService tenantDomainService,
                           TenantAccessGuard tenantAccess,
                           LoginFailureStore loginFailures,
                           LoginLogDomainService loginLogDomainService,
                           StringRedisTemplate redisTemplate,
                           UserMenuQueryAppService userMenuQueryService) {
        this.userDomainService = userDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.tenantDomainService = tenantDomainService;
        this.tenantAccess = tenantAccess;
        this.loginFailures = loginFailures;
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
     * 执行完整的密码登录流程：租户编码解析、验证码校验、用户查询、密码校验
     * （用户不存在与密码错误同码同计数——2026-10-06 拍板：停用/锁定状态不在
     * 密码前披露，关存在性探测）、密码正确后的停用检查（管理员手工启停，
     * 优先于临时锁定提示）、临时锁定检查、Sa-Token会话创建。
     * 登录成功后清除失败计数；密码失败累加计数（锁定期内错误密码同样推进），
     * 达到阈值后凭键剩余 TTL 临时锁定，键过期自动恢复（T-ADMIN-022）。
     * 验证码失败不计入失败计数，仅留审计日志（2026-10-06 拍板：免验证码零成本锁号回退）。
     * </p>
     *
     * @param req 登录请求，包含租户ID、用户名、密码、验证码等
     * @return 登录响应，包含令牌、用户信息、是否强制重置密码等
     * @throws BizException 验证码错误、用户不存在、用户已停用、账号临时锁定、密码错误等
     * <p>
     * 2026-10-06 复评轮拍板（行锁串行化）：本方法为事务方法，用户读取走
     * {@link UserDomainService#lockValidByUsername}（FOR UPDATE 行锁持续到提交），与
     * resetPassword 的 UPDATE 行锁互斥——关闭「在途旧密码登录在重置吊销后建立新会话」
     * 并发窗口（交错两侧其一必见对方已提交结果）。锁持有期间占用数据库连接；
     * 失败审计日志 REQUIRES_NEW 独立短事务，不受本事务回滚影响。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResp login(LoginReq req) {
        var tenant = tenantDomainService.findByCode(req.tenantCode());
        Long tenantId = tenant == null ? null : tenant.getId();
        try {
            validateCaptcha(req.captchaId(), req.captchaCode());
        } catch (BizException e) {
            // 验证码失败不推进失败计数（2026-10-06 拍板恢复改前语义）：计数键无 IP 维度，
            // 免验证码请求可零成本定向锁定任意已知账号（验证码一次性消费，攻击者无需获取
            // 验证码即可推动计数）；验证码失败仅留审计日志，只有通过验证码后的密码失败才锁号
            safeRecordLoginLog(tenantId, null, req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 0, "验证码错误");
            throw e;
        }
        SysUser user = tenantId == null ? null : userDomainService.lockValidByUsername(tenantId, req.username());
        // 密码校验先行（2026-10-06 拍板：关停用先序存在性探测）——用户不存在与密码错误
        // 同码同计数；停用/锁定状态只在密码正确后披露，按错误码差异枚举用户名的通道关闭
        if (user == null || user.getPassword() == null || !BCrypt.checkpw(req.password(), user.getPassword())) {
            if (tenantId != null) loginFailures.recordTenantFailure(tenantId, req.username());
            safeRecordLoginLog(tenantId, user == null ? null : user.getId(), req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 0,
                user == null ? "用户不存在" : "密码错误");
            throw new BizException(AccessErrorCode.PASSWORD_INCORRECT.getCode(), AccessErrorCode.PASSWORD_INCORRECT.getMessage());
        }
        String redisProcessId;
        try {
            redisProcessId = tenantAccess.captureLoginProcess(tenantId, tenant.getSessionEpoch());
        } catch (TenantAccessDeniedException | TenantGateUnavailableException exception) {
            safeRecordLoginLog(tenantId, user.getId(), req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 0,
                "租户不可用或状态已变化");
            throw exception;
        }
        // 停用检查用 status != 1 fail-closed：仅 0/1 收口后任何未定义值
        // 都不应进入会话（与投影 isEnabled(status)==1 对齐，防止认证放行+主体停用分裂）；
        // 停用（管理员事实）仍优先于临时锁定提示，避免重叠时误导「30分钟后重试」
        if (user.getStatus() == null || user.getStatus() != 1) {
            safeRecordLoginLog(tenantId, user.getId(), req.username(), LOGIN_TYPE_PASSWORD, req.clientId(), 0, "用户已停用");
            throw new BizException(AccessErrorCode.USER_DISABLED.getCode(), AccessErrorCode.USER_DISABLED.getMessage());
        }
        if (loginFailures.isTenantLocked(tenantId, req.username())) {
            // T-ADMIN-022：计数键即锁（临时，键过期自动恢复），拒绝时补记登录日志留审计痕迹。
            // 锁定检查在密码校验之后：错误密码仍推进计数，窗口由首次失败时设置的 TTL 决定。
            safeRecordLoginLog(tenantId, user.getId(), req.username(),
                LOGIN_TYPE_PASSWORD, req.clientId(), 0, "登录失败次数过多，账号临时锁定");
            throw new BizException(AccessErrorCode.USER_LOCKED.getCode(),
                "登录失败次数过多，账号已临时锁定，请" + LoginFailureStore.LOCK_WINDOW_MINUTES + "分钟后重试");
        }

        loginFailures.clearTenant(tenantId, req.username());
        StpUtil.login(user.getId());
        // FIX #1: Store tenantId in session for security validation
        SaSession session = StpUtil.getSession();
        session.set("tenantId", user.getTenantId());
        session.set("subjectTypeCode", LocalProjectionOwner.SUBJECT_LOCAL_USER);
        // 操作者名称供 @OperationLog AOP 会话回填（未登录/无会话调用为 null）
        session.set("operatorName", user.getUsername());
        String token = StpUtil.getTokenValue();
        SaSession tokenSession = StpUtil.getTokenSession();
        tokenSession.set(TenantSessionStamp.EPOCH, tenant.getSessionEpoch());
        tokenSession.set(TenantSessionStamp.PROCESS, redisProcessId);
        tokenSession.set(TenantSessionStamp.FORCE_RESET, Boolean.TRUE.equals(user.getForceResetPwd()));

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
        if (tenantId == null) {
            log.warn("Login rejected without a resolvable tenant; no synthetic tenant id is recorded");
            return;
        }
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
            throw new BizException(AccessErrorCode.ADMIN_USER_NOT_FOUND.getCode(), AccessErrorCode.ADMIN_USER_NOT_FOUND.getMessage());
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
        LoginChallengeSupport.validate(redisTemplate, captchaId, captchaCode);
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
     * {@link UserMenuQueryAppService#buildUserMenuTree}（T-ACCESS-006）。
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
            throw new BizException(AccessErrorCode.ADMIN_USER_NOT_FOUND.getCode(), AccessErrorCode.ADMIN_USER_NOT_FOUND.getMessage());
        }

        // 2. 跨域聚合：菜单树 + 角色 + 权限码（一次加载保证同一权限快照）
        return userMenuQueryService.buildUserMenuTree(userId);
    }
}
