package cn.ac.fage.accessmesh.permission.scheduler;

import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户角色孤儿记录延迟补偿定时任务。
 * <p>
 * 扫描 abstract_user 已软删但 user_role 仍存活的孤儿记录，
 * 在确认 UNBIND envelope 未到达时兜底清理。
 * <p>
 * 幂等保证：{@code softDeleteBatch} 已内置幂等（已删的跳过），
 * 与 UNBIND envelope 双清不冲突。
 * <p>
 * 窗口期：仅清理 {@code updated_at < now - windowMinutes} 的记录，
 * 给 envelope 处理留出时间（默认 5 分钟）。
 * <p>
 * 多租户：{@code selectOrphansByCutoff} 不限 tenantId，
 * 按租户分组后逐批调用 {@code softDeleteBatch}。
 * <p>
 * 关联：覆盖 EXT-9（envelope 顺序/丢失风险兜底）。
 */
@Component
public class UserRoleOrphanCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(UserRoleOrphanCleanupTask.class);

    private final UserRoleMapper userRoleMapper;

    @Value("${permission.orphan-cleanup.window-minutes:5}")
    private int windowMinutes;

    public UserRoleOrphanCleanupTask(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    /**
     * 定期扫描并清理孤儿 user_role 记录。
     * <p>
     * 默认每 5 分钟执行一次，可通过 {@code permission.orphan-cleanup.interval} 配置。
     */
    @Scheduled(fixedDelayString = "${permission.orphan-cleanup.interval:300000}")
    public void cleanOrphanUserRoles() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(windowMinutes);

        try {
            // 全租户扫描孤儿记录
            List<UserRole> orphans = userRoleMapper.selectOrphansByCutoff(cutoff);

            if (orphans.isEmpty()) {
                return;
            }

            // 按租户分组，逐批软删除
            Map<Long, List<UserRole>> byTenant = orphans.stream()
                .collect(Collectors.groupingBy(UserRole::getTenantId));

            int totalCleaned = 0;
            for (var entry : byTenant.entrySet()) {
                Long tenantId = entry.getKey();
                List<UserRole> tenantOrphans = entry.getValue();

                // 监控：记录每个孤儿的 (userId, roleId) 便于排查 envelope 丢失根因
                for (UserRole orphan : tenantOrphans) {
                    log.warn("Orphan user_role detected: tenantId={}, userRoleId={}, abstractUserId={}, "
                            + "targetId={} — UNBIND envelope may have been lost",
                        tenantId, orphan.getId(),
                        orphan.getAbstractUserId(), orphan.getTargetId());
                }

                List<Long> orphanIds = tenantOrphans.stream()
                    .map(UserRole::getId)
                    .collect(Collectors.toList());

                // 批量软删除（幂等：已删的跳过）
                userRoleMapper.softDeleteBatch(tenantId, orphanIds, LocalDateTime.now());
                totalCleaned += orphanIds.size();
            }

            log.info("Cleaned {} orphan user_role records across {} tenants", totalCleaned, byTenant.size());
        } catch (Exception e) {
            log.error("Failed to clean orphan user_role records: {}", e.getMessage(), e);
        }
    }
}
