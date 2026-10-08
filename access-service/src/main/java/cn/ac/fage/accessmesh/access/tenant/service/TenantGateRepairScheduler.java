package cn.ac.fage.accessmesh.access.tenant.service;

import cn.ac.fage.accessmesh.access.tenant.entity.SysTenant;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.common.security.RedisTenantGateStore;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 有界分页巡检；正常请求不查数据库，修复与写入共用租户行锁。 */
@Component
@ConditionalOnProperty(prefix = "access.tenant.gate-repair", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TenantGateRepairScheduler {
    private static final Logger log = LoggerFactory.getLogger(TenantGateRepairScheduler.class);
    private static final int PAGE_SIZE = 200;
    private final TenantDomainService tenants;
    private final RedisTenantGateStore gates;
    private final TenantGateRepairAppService repair;
    private long cursor;

    public TenantGateRepairScheduler(TenantDomainService tenants, RedisTenantGateStore gates, TenantGateRepairAppService repair) {
        this.tenants = tenants;
        this.gates = gates;
        this.repair = repair;
    }

    @Scheduled(fixedDelayString = "${access.tenant.gate-repair.delay:1000}")
    public void repairNextPage() {
        try {
            var rows = tenants.scan(cursor, PAGE_SIZE);
            if (rows.isEmpty()) {
                cursor = 0;
                return;
            }
            var ids = rows.stream().map(SysTenant::getId).toList();
            var states = gates.readBatch(ids);
            var pending = ids.stream().filter(id -> states.get(id).status() == TenantGateState.Status.UNAVAILABLE).toList();
            if (!pending.isEmpty()) repair.repair(pending);
            cursor = rows.size() < PAGE_SIZE ? 0 : rows.getLast().getId();
        } catch (RuntimeException exception) {
            // 保留游标，下一次继续；未知状态的业务请求保持拒绝。整段堆栈入日志供排障（Redis 故障类需定位连接原因）。
            log.warn("Tenant gate repair unavailable", exception);
        }
    }
}
