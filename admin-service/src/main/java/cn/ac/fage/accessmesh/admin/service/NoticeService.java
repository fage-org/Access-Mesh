package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface NoticeService {

    Long createNotice(NoticeCreateReq req);

    void updateNotice(NoticeCreateReq req);

    void deleteNotice(IdsReq req);

    NoticeResp getNotice(Long id);

    PaginatedResult<NoticeResp> pageNotices(PageReq pageReq);

    void publishNotice(Long id);

    void markNoticeAsRead(Long noticeId, Long userId);
}
