package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;
import cn.ac.fage.accessmesh.admin.mapper.SysSyncRetryMapper;
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import cn.ac.fage.accessmesh.common.mybatis.TenantAwareScheduled;


/**
 * 同步重试服务实现类
 * <p>
 * 提供跨服务数据同步失败任务的管理和自动重试机制。
 * 用于确保admin-service与permission-center之间的数据一致性。
 * 采用指数退避重试策略，最大重试次数默认5次，重试间隔从1分钟逐步增加到60分钟。
 * 使用@TenantAwareScheduled确保定时任务在正确的租户上下文中执行。
 * 设计约束：用户、组织、成员关系同步需要分别匹配 permission-center 的
 * abstract_user、resource_entity、abstract_role、user_role 契约，均使用业务键定位。
 * 当前通用 /api/sync/{operation} URL 仍是旧占位实现，后续必须按具体同步类型改造。
 * </p>
 */
@Service
public class SyncRetryServiceImpl implements SyncRetryService {

    private static final Logger log = LoggerFactory.getLogger(SyncRetryServiceImpl.class);
    private static final int DEFAULT_MAX_RETRIES = 5;

    private final SysSyncRetryMapper syncRetryMapper;
    private final RestTemplate restTemplate;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param syncRetryMapper 同步重试任务数据访问Mapper
     * @param restTemplate HTTP客户端，用于调用远程服务
     * @param permissionValidator 权限校验器，校验任务操作权限
     */
    public SyncRetryServiceImpl(SysSyncRetryMapper syncRetryMapper, RestTemplate restTemplate, AdminPermissionValidator permissionValidator) {
        this.syncRetryMapper = syncRetryMapper;
        this.restTemplate = restTemplate;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 记录同步失败任务
     * <p>
     * 创建或更新同步失败任务记录。如果messageKey对应的记录已存在，累加重试次数，
     * 更新下次重试时间（指数退避）。如果不存在，创建新记录。
     * 达到最大重试次数后状态变为"exhausted"（耗尽）。
     * </p>
     *
     * @param messageKey 消息唯一标识（用于查找已有记录）
     * @param targetService 目标服务名称（如permission-center）
     * @param entityType 实体类型（如abstract_user）
     * @param externalId 外部实体ID
     * @param operationType 操作类型（如create、update、delete）
     * @param payload 同步数据JSON字符串
     * @param error 错误信息
     */
    @Override
    public void recordSyncFailure(String messageKey, String targetService, String entityType,
                                   String externalId, String operationType, String payload, String error) {
        LocalDateTime now = LocalDateTime.now();
        SysSyncRetry existing = null;
        if (messageKey != null) {
            existing = syncRetryMapper.selectByMessageKey(TenantContextHolder.getTenantId(), messageKey);
        }
        if (existing != null) {
            // 更新现有记录
            existing.setRetryCount(existing.getRetryCount() + 1);
            existing.setLastError(truncate(error, 500));
            existing.setNextRetryAt(now.plusMinutes(Math.min(existing.getRetryCount() * 5, 60)));
            existing.setStatus(existing.getRetryCount() >= existing.getMaxRetries() ? "exhausted" : "pending");
            existing.setUpdatedAt(now);
            syncRetryMapper.update(existing);
        } else {
            // 创建新记录
            SysSyncRetry record = new SysSyncRetry();
            record.setTenantId(TenantContextHolder.getTenantId());
            record.setMessageKey(messageKey);
            record.setTargetService(targetService);
            record.setEntityType(entityType);
            record.setExternalId(externalId);
            record.setOperationType(operationType);
            record.setPayload(payload);
            record.setRetryCount(0);
            record.setMaxRetries(DEFAULT_MAX_RETRIES);
            record.setNextRetryAt(now.plusMinutes(1));
            record.setStatus("pending");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setDeleteFlag(0L);
            syncRetryMapper.insert(record);
        }
    }

    /**
     * 标记同步任务成功
     * <p>
     * 将同步任务状态更新为"success"。
     * 执行实例级权限校验。
     * </p>
     *
     * @param id 任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long id) {
        // 权限检查 — SYNC_RETRY 实例级 UPDATE
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_RETRY, id.toString(), AdminOperationCode.UPDATE);

        SysSyncRetry record = syncRetryMapper.selectByIdSafe(id, TenantContextHolder.getTenantId());
        if (record != null) {
            record.setStatus("success");
            record.setUpdatedAt(LocalDateTime.now());
            syncRetryMapper.update(record);
        }
    }

    /**
     * 标记同步任务失败
     * <p>
     * 累加重试次数，更新下次重试时间和错误信息。
     * 达到最大重试次数后状态变为"exhausted"。
     * 执行实例级权限校验。
     * </p>
     *
     * @param id 任务ID
     * @param error 错误信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long id, String error) {
        // 权限检查 — SYNC_RETRY 实例级 UPDATE
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_RETRY, id.toString(), AdminOperationCode.UPDATE);

        SysSyncRetry record = syncRetryMapper.selectByIdSafe(id, TenantContextHolder.getTenantId());
        if (record != null) {
            LocalDateTime now = LocalDateTime.now();
            record.setRetryCount(record.getRetryCount() + 1);
            record.setLastError(truncate(error, 500));
            record.setNextRetryAt(now.plusMinutes(Math.min(record.getRetryCount() * 5, 60)));
            record.setStatus(record.getRetryCount() >= record.getMaxRetries() ? "exhausted" : "pending");
            record.setUpdatedAt(now);
            syncRetryMapper.update(record);
        }
    }

    /**
     * 获取待重试的任务列表
     * <p>
     * 查询状态为"pending"、重试次数未达上限、下次重试时间已到的任务。
     * 按创建时间正序排列，优先处理早期任务。
     * </p>
     *
     * @return 待重试任务列表
     */
    @Override
    public List<SysSyncRetry> getPendingRetries() {
        return syncRetryMapper.selectPendingRetries(TenantContextHolder.getTenantId(), LocalDateTime.now());
    }

    /**
     * 删除已处理任务
     * <p>
     * 软删除已成功或已耗尽的任务记录。
     * 执行实例级权限校验。
     * </p>
     *
     * @param id 任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcessed(Long id) {
        // 权限检查 — SYNC_RETRY 实例级 DELETE
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_RETRY, id.toString(), AdminOperationCode.DELETE);

        SysSyncRetry record = syncRetryMapper.selectByIdSafe(id, TenantContextHolder.getTenantId());
        if (record != null) {
            record.setDeleteFlag(record.getId());
            record.setDeletedAt(LocalDateTime.now());
            syncRetryMapper.update(record);
        }
    }

    /**
     * 分页查询同步任务列表
     * <p>
     * 获取当前租户的所有同步任务，按创建时间倒序排列。
     * 用于查看同步任务状态和手动干预。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @return 分页同步任务列表结果
     */
    @Override
    public PaginatedResult<SysSyncRetry> page(PageReq pageReq) {
        Page<SysSyncRetry> page = syncRetryMapper.paginateByTenantId(
            Page.of(pageReq.getPageNum(), pageReq.getPageSize()),
            TenantContextHolder.getTenantId()
        );
        long totalPages = (page.getTotalRow() + pageReq.getPageSize() - 1) / pageReq.getPageSize();
        return new PaginatedResult<>(page.getRecords(),
            new PaginatedResult.PaginationMeta(page.getTotalRow(), pageReq.getPageNum(), pageReq.getPageSize(), (int) totalPages));
    }

    /**
     * 定时重试待处理的同步任务
     * <p>
     * 每30秒执行一次，初始延迟60秒。
     * 通过@TenantAwareScheduled确保在每个租户上下文中执行。
     * 遍历待重试任务，通过RestTemplate调用目标服务进行同步。
     * 成功则标记"success"，失败则更新重试次数和下次重试时间。
     * </p>
     */
    @TenantAwareScheduled
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void processRetries() {
        List<SysSyncRetry> pending = getPendingRetries();
        for (SysSyncRetry record : pending) {
            try {
                String url = buildUrl(record);
                if (url == null || record.getPayload() == null) {
                    log.warn("Skipping retry: missing url or payload for key={}", record.getMessageKey());
                    record.setStatus("exhausted");
                    record.setUpdatedAt(LocalDateTime.now());
                    syncRetryMapper.update(record);
                    continue;
                }

                log.info("Retrying sync: key={}, attempt={}/{}", record.getMessageKey(), record.getRetryCount() + 1, record.getMaxRetries());
                record.setRetryCount(record.getRetryCount() + 1);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(record.getPayload(), headers);

                String response = restTemplate.postForObject(url, entity, String.class);
                if (response != null) {
                    record.setStatus("success");
                    record.setUpdatedAt(LocalDateTime.now());
                    syncRetryMapper.update(record);
                    log.info("Sync retry succeeded: key={}", record.getMessageKey());
                }
            } catch (Exception e) {
                log.error("Sync retry failed: key={}", record.getMessageKey(), e);
                record.setLastError(truncate(e.getMessage(), 500));
                record.setNextRetryAt(LocalDateTime.now().plusMinutes(Math.min(record.getRetryCount() * 5, 60)));
                if (record.getRetryCount() >= record.getMaxRetries()) {
                    record.setStatus("exhausted");
                } else {
                    record.setStatus("pending");
                }
                record.setUpdatedAt(LocalDateTime.now());
                syncRetryMapper.update(record);
            }
        }
    }

    /**
     * 构建目标服务URL
     * <p>
     * 根据目标服务名称构建同步接口URL。
     * 支持两种格式：完整HTTP URL或服务发现名称（lb://）。
     * TODO: 当前构造的是旧式 /api/sync/{operation} 占位路径。
     * 新设计要求同步重试保存可重放的具体 Feign/API 契约 payload（使用业务键），
     * 不能把 abstract_user、ADMIN_USER resource_entity、ADMIN_ORG resource_entity、
     * ORG/POSITION abstract_role 和 user_role 混成一个通用端点。
     * </p>
     *
     * @param record 同步任务记录
     * @return 目标URL，无效则返回null
     */
    private String buildUrl(SysSyncRetry record) {
        String target = record.getTargetService();
        if (target == null || target.isBlank()) {
            return null;
        }
        // 若目标已包含协议，直接使用
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target + "/" + record.getOperationType();
        }
        // 使用 lb:// 协议进行服务发现
        String serviceUrl = target.startsWith("lb://") ? target : "lb://" + target;
        return serviceUrl + "/api/sync/" + record.getOperationType();
    }

    /**
     * 截断字符串
     * <p>
     * 限制字符串最大长度，超出部分添加省略号。
     * 用于截断错误信息，防止过长日志。
     * </p>
     *
     * @param s 原字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
