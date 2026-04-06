package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.permission.domain.bo.ChangeLogQueryBo;
import org.dromara.permission.domain.dto.ChangeLogPageReq;
import org.dromara.permission.domain.vo.ChangeLogVo;
import org.dromara.permission.service.PermissionChangeLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 变更记录接口：分页查询权限变更记录，支持按用户、角色、业务域、实体类型、时间、requestId 过滤
 * 接口统一 POST + JSON
 *
 * @author RuoYi-Cloud-Plus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm")
public class PermissionChangeLogController extends BaseController {

    private final PermissionChangeLogService permissionChangeLogService;

    /**
     * 分页查询变更记录（POST JSON）
     * 请求体：{ "query": { "tenantId": 1, ... }, "pageQuery": { "pageNum": 1, "pageSize": 10 } }
     * 支持过滤：tenantId（必填）、abstractUserId、abstractRoleId、bizDomainId、entityType、requestId、beginTime、endTime
     */
    @PostMapping("/change-logs")
    public TableDataInfo<ChangeLogVo> list(@RequestBody(required = false) ChangeLogPageReq req) {
        if (req == null) {
            return TableDataInfo.build();
        }
        ChangeLogQueryBo query = req.getQuery() != null ? req.getQuery() : new ChangeLogQueryBo();
        PageQuery pageQuery = req.getPageQuery() != null ? req.getPageQuery() : new PageQuery();
        return permissionChangeLogService.queryPage(query, pageQuery);
    }
}
