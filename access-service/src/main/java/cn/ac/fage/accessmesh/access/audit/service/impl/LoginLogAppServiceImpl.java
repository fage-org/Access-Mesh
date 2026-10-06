package cn.ac.fage.accessmesh.access.audit.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.util.PageUtil;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.audit.entity.SysLoginLog;
import cn.ac.fage.accessmesh.access.audit.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.access.audit.service.LoginLogAppService;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 登录日志服务实现类
 * <p>
 * 提供登录日志的分页查询功能。
 * 登录日志记录用户登录成功/失败事件，用于安全审计和问题排查。
 * 日志按租户隔离，按登录时间倒序排列。
 * </p>
 */
@Service
public class LoginLogAppServiceImpl implements LoginLogAppService {

    private final SysLoginLogMapper loginLogMapper;
    private final cn.ac.fage.accessmesh.access.engine.query.QueryGate queryGate;
    private final cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService typeResolutionService;

    /**
     * 构造函数注入依赖
     *
     * @param loginLogMapper 登录日志数据访问Mapper
     */
    public LoginLogAppServiceImpl(SysLoginLogMapper loginLogMapper,
                                  cn.ac.fage.accessmesh.access.engine.query.QueryGate queryGate,
                                  cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService typeResolutionService) {
        this.loginLogMapper = loginLogMapper;
        this.queryGate = queryGate;
        this.typeResolutionService = typeResolutionService;
    }

    /**
     * 分页查询登录日志列表
     * <p>
     * 获取当前租户的登录日志，按登录时间倒序排列。
     * 用于查看用户登录历史，排查登录问题。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @return 分页登录日志列表结果
     */
    @Override
    public PageResp<SysLoginLog> pageLoginLogs(PageReq pageReq) {
        Long tenantId = TenantContextHolder.getTenantId();
        // 判定主体=抽象投影主体（QueryGate 契约 subjectId=abstract_user.id）：先经
        // TypeResolutionService.resolveUserId 解析投影（AdminPermissionValidatorImpl 同款），
        // 不裸传 sys_user.id——两者仅在投影 insert 分支不回填 id 的用户上错位，该类用户
        // 按错误主体判定（2026-10-06 逐任务评审修）
        Long operatorId = cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext.getOperatorId();
        Long subjectId = operatorId == null ? null
            : typeResolutionService.resolveUserId(tenantId,
                cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner.SUBJECT_LOCAL_USER,
                String.valueOf(operatorId));
        if (!queryGate.hasPermissionByCode(tenantId, subjectId,
            cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode.OPERATION_LOG, null,
            cn.ac.fage.accessmesh.access.engine.constant.OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION_LOG");
        }
        int pageNum = pageReq.getPageNum();
        int pageSize = pageReq.getPageSize();
        int offset = PageUtil.offset(pageNum, pageSize);

        // XML 分页统一 offset/limit + count 双查询（MyBatis-Flex Page 参数在 XML 映射下不生效）
        long total = loginLogMapper.countByTenantId(tenantId);
        List<SysLoginLog> items = total == 0 ? List.of()
            : loginLogMapper.selectByTenantIdPaged(tenantId, offset, pageSize);

        return new PageResp<>(items, total, pageNum, pageSize,
            PageUtil.hasNext(offset, items.size(), total));
    }
}
