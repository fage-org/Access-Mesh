package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.NoticeUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.admin.entity.SysNotice;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserNotice;
import cn.ac.fage.accessmesh.admin.entity.table.SysNoticeTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserNoticeTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysNoticeMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserNoticeMapper;
import cn.ac.fage.accessmesh.admin.service.NoticeService;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
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


@Service
public class NoticeServiceImpl implements NoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final AdminPermissionValidator permissionValidator;
    private final UserDomainService userDomainService;

    public NoticeServiceImpl(SysNoticeMapper noticeMapper, SysUserNoticeMapper userNoticeMapper,
                             AdminPermissionValidator permissionValidator, UserDomainService userDomainService) {
        this.noticeMapper = noticeMapper;
        this.userNoticeMapper = userNoticeMapper;
        this.permissionValidator = permissionValidator;
        this.userDomainService = userDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotice(NoticeCreateReq req) {
        // Permission check - type-level CREATE
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNotice(NoticeUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        
        // FIX: 通过ID查找，而非title
        SysNotice notice = noticeMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysNoticeTableDef.SYS_NOTICE.ID.eq(req.id()))
                .and(SysNoticeTableDef.SYS_NOTICE.TENANT_ID.eq(tenantId))
                .and(SysNoticeTableDef.SYS_NOTICE.DELETE_FLAG.eq(0))
        );
        
        if (notice == null) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), 
                AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }

        // Permission check - instance-level UPDATE
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.NOTICE, resourceCodes, AdminOperationCode.DELETE);

        // Batch query valid notices (performance fix: avoid N+1 queries)
        List<SysNotice> notices = noticeMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysNoticeTableDef.SYS_NOTICE.ID.in(req.ids()))
                .and(SysNoticeTableDef.SYS_NOTICE.TENANT_ID.eq(tenantId))
                .and(SysNoticeTableDef.SYS_NOTICE.DELETE_FLAG.eq(0))
        );
        if (!notices.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> validIds = notices.stream().map(SysNotice::getId).collect(java.util.stream.Collectors.toList());
            noticeMapper.softDeleteBatch(tenantId, validIds, now);
        }
    }

    @Override
    public NoticeResp getNotice(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysNoticeTableDef.SYS_NOTICE.ID.eq(id))
                .and(SysNoticeTableDef.SYS_NOTICE.TENANT_ID.eq(tenantId))
                .and(SysNoticeTableDef.SYS_NOTICE.DELETE_FLAG.eq(0))
        );
        if (notice == null) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }
        return new NoticeResp(notice.getId(), notice.getTitle(), notice.getContent(),
            notice.getNoticeType() != null ? Integer.parseInt(notice.getNoticeType()) : null, notice.getTargetIds(), notice.getStatus(),
            notice.getCreatedAt(), notice.getUpdatedAt());
    }

    @Override
    public PaginatedResult<NoticeResp> pageNotices(PageReq pageReq) {
        Long tenantId = TenantContextHolder.getTenantId();
        Page<SysNotice> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysNotice> result = noticeMapper.paginate(page,
            QueryWrapper.create()
                .where(SysNoticeTableDef.SYS_NOTICE.TENANT_ID.eq(tenantId))
                .and(SysNoticeTableDef.SYS_NOTICE.DELETE_FLAG.eq(0))
                .orderBy(SysNoticeTableDef.SYS_NOTICE.CREATED_AT.desc()));

        List<NoticeResp> items = result.getRecords().stream()
            .map(n -> new NoticeResp(n.getId(), n.getTitle(), n.getContent(),
                n.getNoticeType() != null ? Integer.parseInt(n.getNoticeType()) : null, n.getTargetIds(),
                n.getStatus(), n.getCreatedAt(), n.getUpdatedAt()))
            .toList();

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishNotice(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysNotice notice = noticeMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysNoticeTableDef.SYS_NOTICE.ID.eq(id))
                .and(SysNoticeTableDef.SYS_NOTICE.TENANT_ID.eq(tenantId))
                .and(SysNoticeTableDef.SYS_NOTICE.DELETE_FLAG.eq(0))
        );
        if (notice == null) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }

        // Permission check - instance-level PUBLISH
        permissionValidator.checkInstanceLevel(AdminResourceType.NOTICE, notice.getId().toString(), AdminOperationCode.PUBLISH);

        notice.setStatus(1);
        notice.setPublishedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.update(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNoticeAsRead(Long noticeId, Long userId) {
        SysUserNotice existing = userNoticeMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysUserNoticeTableDef.SYS_USER_NOTICE.NOTICE_ID.eq(noticeId))
                .and(SysUserNoticeTableDef.SYS_USER_NOTICE.USER_ID.eq(userId))
        );
        if (existing != null) {
            existing.setIsRead(true);
            existing.setReadAt(LocalDateTime.now());
            userNoticeMapper.update(existing);
        } else {
            SysUserNotice userNotice = new SysUserNotice();
            userNotice.setNoticeId(noticeId);
            userNotice.setUserId(userId);
            userNotice.setIsRead(true);
            userNotice.setReadAt(LocalDateTime.now());
            userNoticeMapper.insert(userNotice);
        }
    }

    @Override
    public List<UserNoticeItem> listMyNotices(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        // Fallback: use two separate queries since MyBatis-Flex doesn't support raw SQL easily
        List<SysNotice> notices = noticeMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysNoticeTableDef.SYS_NOTICE.TENANT_ID.eq(tenantId))
                .and(SysNoticeTableDef.SYS_NOTICE.DELETE_FLAG.eq(0))
                .and(SysNoticeTableDef.SYS_NOTICE.STATUS.eq(1))
                .orderBy(SysNoticeTableDef.SYS_NOTICE.CREATED_AT.desc())
        );

        List<Long> noticeIds = notices.stream().map(SysNotice::getId).toList();
        List<SysUserNotice> userNotices = noticeIds.isEmpty() ? List.of() :
            userNoticeMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(SysUserNoticeTableDef.SYS_USER_NOTICE.USER_ID.eq(userId))
                    .and(SysUserNoticeTableDef.SYS_USER_NOTICE.NOTICE_ID.in(noticeIds))
            );

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
     * Parse and validate targetUserIds string
     * 
     * @param tenantId tenant ID for tenant isolation check
     * @param targetUserIds comma-separated user ID string
     * @throws BizException if contains invalid user ID format or non-existent users
     */
    private void parseAndValidateUserIds(Long tenantId, String targetUserIds) {
        if (targetUserIds == null || targetUserIds.isBlank()) {
            return;
        }
        
        // Parse to Long set
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
        
        // Validate user existence and tenant isolation
        List<SysUser> validUsers = userDomainService.selectValidByIds(tenantId, userIds);
        Map<Long, SysUser> validUserMap = validUsers.stream()
            .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        
        // Find non-existent user IDs
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
