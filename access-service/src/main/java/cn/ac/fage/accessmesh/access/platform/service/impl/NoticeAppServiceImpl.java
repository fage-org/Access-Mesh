package cn.ac.fage.accessmesh.access.platform.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.dto.IdsReq;
import cn.ac.fage.accessmesh.access.platform.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.access.platform.dto.req.NoticeUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.platform.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.access.platform.entity.SysNotice;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.platform.entity.SysUserNotice;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.platform.mapper.SysNoticeMapper;
import cn.ac.fage.accessmesh.access.platform.mapper.SysUserNoticeMapper;
import cn.ac.fage.accessmesh.access.platform.service.NoticeAppService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 系统通知管理服务实现类（T-ADMIN-029：状态机对齐 DDL + 受众生命周期闭合）
 * <p>
 * 状态机（DDL 权威：0=草稿，1=已发布，2=已撤回；严格转换拒绝）：
 * create 置 0；publish 0/2 → 1（2→1 为重新发布，已读记录延续）；
 * revoke 1 → 2（保留已读记录）；非法/重复转换拒绝（10402）。
 * </p>
 * <p>
 * 受众（typed IDs + JSONB 数组，零兼容层）：targetType ALL=全员 / USER=指定用户
 * （ORG 预留未实现，DTO @Pattern 拒绝）；ALL 必须不带 targetUserIds、USER 必须非空
 * 且全部为当前租户有效用户；落库 JSONB 数组 {@code [101,102]}，读取反序列化为
 * List&lt;Long&gt;。my-notices 与标已读均按「已发布 + 受众含该用户」过滤。
 * </p>
 * <p>
 * 管理面门禁全档类型级（ADMIN_NOTICE 无 resource_entity 投影，实例级校验无资源
 * 可挂）：create=CREATE、update=UPDATE、delete=DELETE、detail/page=VIEW、
 * publish/revoke=PUBLISH。
 * </p>
 */
@Service
public class NoticeAppServiceImpl implements NoticeAppService {

    private static final String TARGET_TYPE_ALL = "ALL";
    private static final String TARGET_TYPE_USER = "USER";

    /** 状态值（DDL sys_notice.status：0=草稿；1=已发布/2=已撤回的转换判定已下沉条件 UPDATE，见 publishFrom/revokeFrom） */
    private static final int STATUS_DRAFT = 0;

    /** JSONB 数组序列化/反序列化（Jackson 线程安全，静态复用） */
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};

    private final SysNoticeMapper noticeMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final AdminPermissionValidator permissionValidator;
    private final UserDomainService userDomainService;

    public NoticeAppServiceImpl(SysNoticeMapper noticeMapper, SysUserNoticeMapper userNoticeMapper,
                             AdminPermissionValidator permissionValidator, UserDomainService userDomainService) {
        this.noticeMapper = noticeMapper;
        this.userNoticeMapper = userNoticeMapper;
        this.permissionValidator = permissionValidator;
        this.userDomainService = userDomainService;
    }

    /**
     * 创建系统通知（草稿）
     * <p>
     * 创建后为草稿状态（status=0），对任何接收者不可见；发布后才可见。
     * 执行类型级权限校验(CREATE)；受众合法性与目标用户有效性前置校验。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "NOTICE_CREATE", targetType = "sys_notice",
        targetId = "#result", summary = "'create notice'")
    public Long createNotice(NoticeCreateReq req) {
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_NOTICE, OperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();
        String targetType = normalizeAndValidateTarget(tenantId, req.targetType(), req.targetUserIds());

        SysNotice notice = new SysNotice();
        notice.setTenantId(tenantId);
        notice.setTitle(req.title());
        notice.setContent(req.content());
        notice.setNoticeType(req.noticeType() != null ? String.valueOf(req.noticeType()) : "1");
        notice.setTargetType(targetType);
        notice.setTargetIds(TARGET_TYPE_USER.equals(targetType) ? serializeIds(req.targetUserIds()) : null);
        notice.setStatus(STATUS_DRAFT);
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        notice.setDeleteFlag(0L);
        noticeMapper.insert(notice);
        return notice.getId();
    }

    /**
     * 更新系统通知
     * <p>
     * 全量更新标题/内容/类型/受众（不改变状态）。UpdateEntity 显式列集更新——
     * targetType USER→ALL 切换时 target_ids 必须真正置空（MyBatis-Flex update(entity)
     * 忽略 null 列，读改写全列回写会残留旧受众数组）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "NOTICE_UPDATE", targetType = "sys_notice",
        targetId = "#req.id()", summary = "'update notice ' + #req.id()")
    public void updateNotice(NoticeUpdateReq req) {
        // 门禁先于存在性查询（与 create/delete/detail/page 同序——先查后判权的差异形态可被用于探测公告 id 存在性）
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_NOTICE, OperationCode.UPDATE);

        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectByIdSafe(tenantId, req.id());
        if (notice == null) {
            throw noticeNotFound();
        }

        String targetType = normalizeAndValidateTarget(tenantId, req.targetType(), req.targetUserIds());

        SysNotice patch = com.mybatisflex.core.util.UpdateEntity.of(SysNotice.class);
        patch.setId(notice.getId());
        patch.setTitle(req.title());
        patch.setContent(req.content());
        patch.setNoticeType(req.noticeType() != null ? String.valueOf(req.noticeType()) : notice.getNoticeType());
        patch.setTargetType(targetType);
        patch.setTargetIds(TARGET_TYPE_USER.equals(targetType) ? serializeIds(req.targetUserIds()) : null);
        patch.setUpdatedAt(LocalDateTime.now());
        noticeMapper.update(patch);
    }

    /**
     * 批量删除系统通知
     * <p>
     * 软删除通知并物理级联清理 sys_user_notice 已读记录（本表无 delete_flag，
     * 物理删除即 DDL 形态；兑现 Controller 历来 javadoc 声称的级联行为）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "NOTICE_DELETE", targetType = "sys_notice",
        targetId = "", summary = "'batch delete notices'")
    public void deleteNotice(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_NOTICE, OperationCode.DELETE);

        List<SysNotice> notices = noticeMapper.selectByIdsSafe(tenantId, req.ids());
        if (!notices.isEmpty()) {
            List<Long> validIds = notices.stream().map(SysNotice::getId).toList();
            noticeMapper.softDeleteBatch(tenantId, validIds, LocalDateTime.now());
            userNoticeMapper.deleteByNoticeIds(tenantId, validIds);
        }
    }

    /**
     * 获取通知详情（类型级 VIEW 门禁；管理面读取通道，全状态可见）
     */
    @Override
    public NoticeResp getNotice(Long id) {
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_NOTICE, OperationCode.VIEW);

        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectByIdSafe(tenantId, id);
        if (notice == null) {
            throw noticeNotFound();
        }
        return toResp(notice);
    }

    /**
     * 分页查询通知列表（类型级 VIEW 门禁；管理面全状态列表，按创建时间倒序）
     */
    @Override
    public PageResp<NoticeResp> pageNotices(PageReq pageReq) {
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_NOTICE, OperationCode.VIEW);

        Long tenantId = TenantContextHolder.getTenantId();
        int pageNum = pageReq.getPageNum();
        int pageSize = pageReq.getPageSize();

        // XML 分页统一 offset/limit + count 双查询（MyBatis-Flex Page 参数在 XML 映射下不生效）
        long total = noticeMapper.countByTenant(tenantId);
        List<SysNotice> records = total == 0 ? List.of()
            : noticeMapper.selectByTenantPaged(tenantId, (pageNum - 1) * pageSize, pageSize);

        List<NoticeResp> items = records.stream().map(this::toResp).toList();

        return new PageResp<>(items, total, pageNum, pageSize,
            (pageNum - 1) * pageSize + items.size() < total);
    }

    /**
     * 发布通知（0 草稿/2 已撤回 → 1 已发布）
     * <p>
     * 严格状态机：对已发布状态重复发布拒绝（10402）；2→1 为重新发布
     * （已读记录保留延续），publishedAt 刷新为当次发布时间。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "NOTICE_PUBLISH", targetType = "sys_notice",
        targetId = "#id", summary = "'publish notice ' + #id")
    public void publishNotice(Long id) {
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_NOTICE, OperationCode.PUBLISH);

        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectByIdSafe(tenantId, id);
        if (notice == null) {
            throw noticeNotFound();
        }

        // 条件 UPDATE 原子判定（0/2→1）：并发重复发布恰一个成功，另一 0 行转 10402（双轨评审 P2-1）
        if (noticeMapper.publishFrom(tenantId, id, LocalDateTime.now()) == 0) {
            throw statusConflict();
        }
    }

    /**
     * 撤回通知（1 已发布 → 2 已撤回；T-ADMIN-029）
     * <p>
     * 严格状态机：草稿（0，未发布无需撤回）与已撤回（2，重复撤回）均拒绝（10402）。
     * 撤回后受众不可读、不可标已读；已读记录保留——重新发布后已读状态延续；
     * publishedAt 保留作发布痕迹。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "NOTICE_REVOKE", targetType = "sys_notice",
        targetId = "#id", summary = "'revoke notice ' + #id")
    public void revokeNotice(Long id) {
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_NOTICE, OperationCode.PUBLISH);

        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectByIdSafe(tenantId, id);
        if (notice == null) {
            throw noticeNotFound();
        }

        // 条件 UPDATE 原子判定（1→2）：草稿/已撤回/并发重复撤回均 0 行转 10402（双轨评审 P2-1）
        if (noticeMapper.revokeFrom(tenantId, id, LocalDateTime.now()) == 0) {
            throw statusConflict();
        }
    }

    /**
     * 标记通知已读
     * <p>
     * 前置校验可见性（已发布 + 受众含该用户），不可见统一按不存在拒绝——
     * 草稿/已撤回/非受众/不存在均 10401，不泄露公告存在性。
     * </p>
     * <p>
     * 审计口径：用户自操作的轻量已读标记，写 sys_user_notice 状态位，不标注
     * @OperationLog——高频低价值操作，审计追踪价值低，与 admin 域管理写操作区分；
     * 已读事件可通过 sys_user_notice.read_at 列追踪。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNoticeAsRead(Long noticeId, Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (noticeMapper.countVisibleById(tenantId, noticeId, userId) == 0) {
            throw noticeNotFound();
        }

        // 幂等 upsert（双轨评审 P2-2）：并发双 read 的先查后插撞 uk_user_notice 窗口消除
        userNoticeMapper.upsertRead(tenantId, noticeId, userId, LocalDateTime.now());
    }

    /**
     * 获取用户可见公告列表（受众过滤）
     * <p>
     * SQL 层过滤「已发布 + 受众含该用户」（target_type ALL 或 USER 数组包含），
     * 两次查询：可见公告列表 + 该用户已读记录，组装阅读状态。
     * </p>
     */
    @Override
    public List<UserNoticeItem> listMyNotices(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<SysNotice> notices = noticeMapper.selectVisibleByUser(tenantId, userId);

        List<Long> noticeIds = notices.stream().map(SysNotice::getId).toList();
        List<SysUserNotice> userNotices = noticeIds.isEmpty() ? List.of()
            : userNoticeMapper.selectByUserAndNoticeIds(userId, noticeIds, tenantId);

        Map<Long, SysUserNotice> readMap = userNotices.stream()
            .collect(Collectors.toMap(SysUserNotice::getNoticeId, Function.identity()));

        return notices.stream().map(n -> {
            SysUserNotice un = readMap.get(n.getId());
            return new UserNoticeItem(
                n.getId(), n.getTitle(), n.getContent(),
                n.getNoticeType() != null ? Integer.parseInt(n.getNoticeType()) : null,
                n.getCreatedAt(),
                un != null && Boolean.TRUE.equals(un.getIsRead()),
                un != null ? un.getReadAt() : null
            );
        }).toList();
    }

    // ===== 受众校验与 JSONB 序列化 =====

    /**
     * 归一并校验受众表达（T-ADMIN-029 明确语义）
     * <p>
     * targetType 缺省（null）归一 ALL（拍板口径；DTO @Pattern 已拦非法值与 ORG，
     * 服务层显式值域拒绝兜底——防内部调用意外归一，fail-closed）；
     * ALL 带目标用户拒绝（矛盾请求）；USER 空目标拒绝；USER 目标须全部为
     * 当前租户有效用户（不存在/跨租户拒绝）。
     * </p>
     *
     * @return 归一后的 targetType（ALL 或 USER）
     */
    private String normalizeAndValidateTarget(Long tenantId, String targetType, List<Long> targetUserIds) {
        String normalized = targetType == null ? TARGET_TYPE_ALL : targetType;
        boolean hasIds = targetUserIds != null && !targetUserIds.isEmpty();

        if (TARGET_TYPE_ALL.equals(normalized)) {
            if (hasIds) {
                throw new BizException(AccessErrorCode.ADMIN_INVALID_PARAM.getCode(),
                    "目标类型为 ALL（全员）时不能指定目标用户");
            }
            return TARGET_TYPE_ALL;
        }

        if (TARGET_TYPE_USER.equals(normalized)) {
            // USER：必须显式给出非空目标集，且全部为当前租户有效用户
            if (!hasIds) {
                throw new BizException(AccessErrorCode.ADMIN_INVALID_PARAM.getCode(),
                    "目标类型为 USER（指定用户）时目标用户列表不能为空");
            }
            Set<Long> userIds = new HashSet<>(targetUserIds);
            List<SysUser> validUsers = userDomainService.selectValidByIds(tenantId, userIds);
            Set<Long> validIds = validUsers.stream().map(SysUser::getId).collect(Collectors.toSet());
            Set<Long> missing = userIds.stream().filter(id -> !validIds.contains(id)).collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                throw new BizException(AccessErrorCode.ADMIN_USER_NOT_FOUND.getCode(),
                    "User does not exist or does not belong to current tenant: " + missing.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")));
            }
            return TARGET_TYPE_USER;
        }

        // 值域 fail-closed（含 ORG 与拼错值——DTO @Pattern 之外的服务层兜底，双轨评审 P3-2）
        throw new BizException(AccessErrorCode.ADMIN_INVALID_PARAM.getCode(),
            "目标类型仅支持 ALL 或 USER");
    }

    /** List&lt;Long&gt; → JSONB 数组文本（[101,102]；经 JsonbStringTypeHandler 以 jsonb 落库） */
    private static String serializeIds(List<Long> ids) {
        try {
            return JSON.writeValueAsString(ids);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("目标用户序列化失败", e);
        }
    }

    /** JSONB 数组文本 → List&lt;Long&gt;；null/空返回空列表。损坏形态上抛 fail-fast（拍板无存量，
     * 容错分支无真实输入且静默吞损坏会呈现为「空受众 USER 行」——双轨评审裁剪项） */
    private static List<Long> deserializeIds(String targetIds) {
        if (targetIds == null || targetIds.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(targetIds, LONG_LIST);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("目标用户受众数据损坏（非 JSONB 数字数组）: " + targetIds, e);
        }
    }

    private NoticeResp toResp(SysNotice n) {
        return new NoticeResp(n.getId(), n.getTitle(), n.getContent(),
            n.getNoticeType() != null ? Integer.parseInt(n.getNoticeType()) : null,
            n.getTargetType() != null ? n.getTargetType() : TARGET_TYPE_ALL,
            deserializeIds(n.getTargetIds()),
            n.getStatus(), n.getCreatedAt(), n.getUpdatedAt());
    }

    private static BizException noticeNotFound() {
        return new BizException(AccessErrorCode.NOTICE_NOT_FOUND.getCode(),
            AccessErrorCode.NOTICE_NOT_FOUND.getMessage());
    }

    private static BizException statusConflict() {
        return new BizException(AccessErrorCode.NOTICE_STATUS_CONFLICT.getCode(),
            AccessErrorCode.NOTICE_STATUS_CONFLICT.getMessage());
    }
}
