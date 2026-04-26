package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.admin.entity.SysNotice;
import cn.ac.fage.accessmesh.admin.entity.SysUserNotice;
import cn.ac.fage.accessmesh.admin.entity.table.SysNoticeTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserNoticeTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysNoticeMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserNoticeMapper;
import cn.ac.fage.accessmesh.admin.service.NoticeService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.ac.fage.accessmesh.admin.entity.table.SysNoticeTableDef.SYS_NOTICE;
import static cn.ac.fage.accessmesh.admin.entity.table.SysUserNoticeTableDef.SYS_USER_NOTICE;

@Service
public class NoticeServiceImpl implements NoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserNoticeMapper userNoticeMapper;

    public NoticeServiceImpl(SysNoticeMapper noticeMapper, SysUserNoticeMapper userNoticeMapper) {
        this.noticeMapper = noticeMapper;
        this.userNoticeMapper = userNoticeMapper;
    }

    @Override
    @Transactional
    public Long createNotice(NoticeCreateReq req) {
        SysNotice notice = new SysNotice();
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
    @Transactional
    public void updateNotice(NoticeCreateReq req) {
        SysNotice notice = noticeMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_NOTICE.TITLE.eq(req.title()))
                .and(SYS_NOTICE.DELETE_FLAG.eq(0))
        );
        if (notice == null) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }
        notice.setTitle(req.title());
        notice.setContent(req.content());
        notice.setNoticeType(req.noticeType() != null ? String.valueOf(req.noticeType()) : notice.getNoticeType());
        notice.setTargetIds(req.targetUserIds());
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.update(notice);
    }

    @Override
    @Transactional
    public void deleteNotice(IdsReq req) {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.ids()) {
            SysNotice notice = noticeMapper.selectOneById(id);
            if (notice == null || notice.getDeleteFlag() != 0L) continue;
            notice.setDeleteFlag(1L);
            notice.setDeletedAt(now);
            noticeMapper.update(notice);
        }
    }

    @Override
    public NoticeResp getNotice(Long id) {
        SysNotice notice = noticeMapper.selectOneById(id);
        if (notice == null || notice.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }
        return new NoticeResp(notice.getId(), notice.getTitle(), notice.getContent(),
            notice.getNoticeType() != null ? Integer.parseInt(notice.getNoticeType()) : null, notice.getTargetIds(), notice.getStatus(),
            notice.getCreatedAt(), notice.getUpdatedAt());
    }

    @Override
    public PaginatedResult<NoticeResp> pageNotices(PageReq pageReq) {
        Page<SysNotice> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysNotice> result = noticeMapper.paginate(page,
            QueryWrapper.create()
                .where(SYS_NOTICE.DELETE_FLAG.eq(0))
                .orderBy(SYS_NOTICE.CREATED_AT.desc()));

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
    @Transactional
    public void publishNotice(Long id) {
        SysNotice notice = noticeMapper.selectOneById(id);
        if (notice == null || notice.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.NOTICE_NOT_FOUND.getCode(), AdminErrorCode.NOTICE_NOT_FOUND.getMessage());
        }
        notice.setStatus(1);
        notice.setPublishedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.update(notice);
    }

    @Override
    @Transactional
    public void markNoticeAsRead(Long noticeId, Long userId) {
        SysUserNotice existing = userNoticeMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_USER_NOTICE.NOTICE_ID.eq(noticeId))
                .and(SYS_USER_NOTICE.USER_ID.eq(userId))
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
}
