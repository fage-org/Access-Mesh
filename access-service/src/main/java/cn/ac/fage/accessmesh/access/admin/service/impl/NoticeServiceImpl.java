package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.NoticeUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysNotice;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserNotice;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysNoticeMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserNoticeMapper;
import cn.ac.fage.accessmesh.access.admin.service.NoticeService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 系统通知管理服务实现类
 * <p>
 * 提供系统通知的CRUD操作、发布、标记已读、用户通知列表等功能。
 * 系统通知用于向用户推送重要信息，支持指定目标用户群。
 * 用户通知阅读状态通过SysUserNotice表记录，支持批量查询优化。
 * 目标用户ID列表在创建和更新时校验有效性和租户隔离。
 * </p>
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final AdminPermissionValidator permissionValidator;
    private final UserDomainService userDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param noticeMapper 通知数据访问Mapper
     * @param userNoticeMapper 用户通知关联数据访问Mapper
     * @param permissionValidator 权限校验器
     * @param userDomainService 用户领域服务，用于校验目标用户
     */
    public NoticeServiceImpl(SysNoticeMapper noticeMapper, SysUserNoticeMapper userNoticeMapper,
                             AdminPermissionValidator permissionValidator, UserDomainService userDomainService) {
        this.noticeMapper = noticeMapper;
        this.userNoticeMapper = userNoticeMapper;
        this.permissionValidator = permissionValidator;
        this.userDomainService = userDomainService;
    }

    /**
     * 创建系统通知
     * <p>
     * 创建新的系统通知，设置标题、内容、通知类型、目标用户等。
     * 执行类型级权限校验(CREATE)。
     * 创建前校验目标用户ID列表的有效性和租户隔离。
     * </p>
     *
     * @param req 通知创建请求，包含标题、内容、类型、目标用户ID
     * @return 新通知ID
     * @throws BizException 目标用户ID格式无效或用户不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotice(NoticeCreateReq req) {
        // 权限检查 — 类型级 CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.NOTICE, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();

        // FIX: Parse and validate targetUserIds before storing
        parseAndValidateUserIds(tenantId, req.targetUserIds());

        SysNotice notice = new SysNotice();
        notice.setTenantId(tenantId);
        notice.setTitle(req.title());
        notice.setContent(req.content());
        notice.setNoticeType(req.noticeType() != null ? String.valueOf(req.noticeType()) : "1");
        notice.setTargetIds(req.targetUserIds());
        notice.setStatus(1);
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        notice.setDeleteFlag(0L);
        noticeMapper.insert(notice);
        return notice.getId();
    }

    /**
     * 更新系统通知
     * <p>
     * 更新通知的标题、内容、类型、目标用户等属性。
     * 执行实例级权限校验(UPDATE)。
     * 更新前校验目标用户ID列表的有效性和租户隔离。
     * </p>
     *
     * @param req 通知更新请求，包含通知ID和新属性值
     * @throws BizException 通知不存在、目标用户ID格式无效或用户不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNotice(NoticeUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // FIX: 通过ID查找，而非title
        SysNotice notice = noticeMapper.selectByIdSafe(tenantId, req.id());

        if (notice == null) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(),
                AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }

        // 权限检查 — 实例级 UPDATE
        permissionValidator.checkInstanceLevel(AdminResourceType.NOTICE,
            String.valueOf(req.id()), AdminOperationCode.UPDATE);

        // FIX: Parse and validate targetUserIds before updating
        parseAndValidateUserIds(tenantId, req.targetUserIds());

        notice.setTitle(req.title());
        notice.setContent(req.content());
        notice.setNoticeType(req.noticeType() != null ? String.valueOf(req.noticeType()) : notice.getNoticeType());
        notice.setTargetIds(req.targetUserIds());
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.update(notice);
    }

    /**
     * 批量删除系统通知
     * <p>
     * 执行批量实例级权限校验后软删除通知。
     * 使用批量查询和批量软删除优化性能。
     * </p>
     *
     * @param req ID集合请求，包含待删除的通知ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 权限检查 — 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.NOTICE, resourceCodes, AdminOperationCode.DELETE);

        // 批量查询有效通知（性能优化：避免 N+1 查询）
        List<SysNotice> notices = noticeMapper.selectByIdsSafe(tenantId, req.ids());
        if (!notices.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> validIds = notices.stream().map(SysNotice::getId).collect(java.util.stream.Collectors.toList());
            noticeMapper.softDeleteBatch(tenantId, validIds, now);
        }
    }

    /**
     * 获取通知详情
     * <p>
     * 根据通知ID查询通知完整信息。
     * </p>
     *
     * @param id 通知ID
     * @return 通知详情响应
     * @throws BizException 通知不存在
     */
    @Override
    public NoticeResp getNotice(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectByIdSafe(tenantId, id);
        if (notice == null) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }
        return new NoticeResp(notice.getId(), notice.getTitle(), notice.getContent(),
            notice.getNoticeType() != null ? Integer.parseInt(notice.getNoticeType()) : null, notice.getTargetIds(), notice.getStatus(),
            notice.getCreatedAt(), notice.getUpdatedAt());
    }

    /**
     * 分页查询通知列表
     * <p>
     * 获取当前租户的所有通知，按创建时间倒序排列。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @return 分页通知列表结果
     */
    @Override
    public PaginatedResult<NoticeResp> pageNotices(PageReq pageReq) {
        Long tenantId = TenantContextHolder.getTenantId();
        Page<SysNotice> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysNotice> result = noticeMapper.paginateByTenant(page, tenantId);

        List<NoticeResp> items = result.getRecords().stream()
            .map(n -> new NoticeResp(n.getId(), n.getTitle(), n.getContent(),
                n.getNoticeType() != null ? Integer.parseInt(n.getNoticeType()) : null, n.getTargetIds(),
                n.getStatus(), n.getCreatedAt(), n.getUpdatedAt()))
            .toList();

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    /**
     * 发布通知
     * <p>
     * 将通知状态设置为已发布，记录发布时间。
     * 执行实例级权限校验(PUBLISH)。
     * </p>
     *
     * @param id 通知ID
     * @throws BizException 通知不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishNotice(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectByIdSafe(tenantId, id);
        if (notice == null) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }

        // 权限检查 — 实例级 PUBLISH
        permissionValidator.checkInstanceLevel(AdminResourceType.NOTICE, notice.getId().toString(), AdminOperationCode.PUBLISH);

        notice.setStatus(2);
        notice.setPublishedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.update(notice);
    }

    /**
     * 标记通知已读
     * <p>
     * 记录用户已阅读指定通知的状态和时间。
     * 如果已有记录则更新，否则创建新记录。
     * </p>
     *
     * @param noticeId 通知ID
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNoticeAsRead(Long noticeId, Long userId) {
        SysUserNotice existing = userNoticeMapper.selectByNoticeAndUser(noticeId, userId, TenantContextHolder.getTenantId());
        if (existing != null) {
            existing.setIsRead(true);
            existing.setReadAt(LocalDateTime.now());
            userNoticeMapper.update(existing);
        } else {
            SysUserNotice userNotice = new SysUserNotice();
            userNotice.setTenantId(TenantContextHolder.getTenantId());
            userNotice.setNoticeId(noticeId);
            userNotice.setUserId(userId);
            userNotice.setIsRead(true);
            userNotice.setReadAt(LocalDateTime.now());
            userNoticeMapper.insert(userNotice);
        }
    }

    /**
     * 获取用户通知列表
     * <p>
     * 获取指定用户可见的通知列表，包含阅读状态和阅读时间。
     * 使用两次查询优化：先查通知列表，再查用户阅读记录。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户通知项列表，包含通知内容和阅读状态
     */
    @Override
    public List<UserNoticeItem> listMyNotices(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        // 降级方案：使用两次查询，因 MyBatis-Flex 不便直接执行原生 SQL
        List<SysNotice> notices = noticeMapper.selectPublishedByTenant(tenantId);

        List<Long> noticeIds = notices.stream().map(SysNotice::getId).toList();
        List<SysUserNotice> userNotices = noticeIds.isEmpty() ? List.of() :
            userNoticeMapper.selectByUserAndNoticeIds(userId, noticeIds, tenantId);

        var readMap = userNotices.stream()
            .collect(java.util.stream.Collectors.toMap(SysUserNotice::getNoticeId, un -> un));

        return notices.stream().map(n -> {
            SysUserNotice un = readMap.get(n.getId());
            return new UserNoticeItem(
                n.getId(), n.getTitle(), n.getContent(),
                n.getNoticeType() != null ? n.getNoticeType() : null,
                n.getCreatedAt(),
                un != null ? un.getIsRead() : false,
                un != null ? un.getReadAt() : null
            );
        }).toList();
    }

    /**
     * 解析并校验目标用户ID列表
     * <p>
     * 解析逗号分隔的用户ID字符串，校验格式有效性和用户存在性。
     * 验证用户是否属于当前租户（租户隔离）。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离校验
     * @param targetUserIds 逗号分隔的用户ID字符串
     * @throws BizException 用户ID格式无效或用户不存在/不属于当前租户
     */
    private void parseAndValidateUserIds(Long tenantId, String targetUserIds) {
        if (targetUserIds == null || targetUserIds.isBlank()) {
            return;
        }

        // 解析为 Long 集合
        Set<Long> userIds = new HashSet<>();
        List<String> invalidIds = new ArrayList<>();

        for (String idStr : targetUserIds.split(",")) {
            String trimmed = idStr.trim();
            if (!trimmed.isEmpty()) {
                try {
                    userIds.add(Long.valueOf(trimmed));
                } catch (NumberFormatException e) {
                    invalidIds.add(trimmed);
                }
            }
        }

        if (!invalidIds.isEmpty()) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "Invalid user ID format: " + String.join(", ", invalidIds));
        }

        if (userIds.isEmpty()) {
            return;
        }

        // 验证用户存在性和租户隔离
        List<SysUser> validUsers = userDomainService.selectValidByIds(tenantId, userIds);
        Map<Long, SysUser> validUserMap = validUsers.stream()
            .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        // 找出不存在的用户ID
        Set<Long> missingUserIds = userIds.stream()
            .filter(id -> !validUserMap.containsKey(id))
            .collect(Collectors.toSet());

        if (!missingUserIds.isEmpty()) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(),
                "User does not exist or does not belong to current tenant: " + missingUserIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        }
    }
}