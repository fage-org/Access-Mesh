package cn.ac.fage.accessmesh.access.grant.service.domain;

import cn.ac.fage.accessmesh.access.infrastructure.task.JobInvocable;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自动授权对账任务入口（T-PERM-073，设计 §13；2026-09-21 用户定案：bootstrap 种子默认停用）。
 * <p>
 * {@code @JobInvocable} 白名单方法，经既有任务设施（sys_job 调度 + 手动触发，T-ACCESS-009）
 * 调用；invokeTarget = {@code autoGrantReconcileInvoker.reconcile}。对账只发现异常不修复；
 * 逐条差异 log.warn，汇总（String 返回值）经执行编排层写入任务执行日志成功消息。
 * 执行身份=TASK 可信上下文（上下文由编排层绑定），租户取任务行 tenantId。
 * </p>
 */
@Component("autoGrantReconcileInvoker")
public class AutoGrantReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(AutoGrantReconcileJob.class);

    private final AutoGrantReconcileDomainService reconcileDomainService;

    public AutoGrantReconcileJob(AutoGrantReconcileDomainService reconcileDomainService) {
        this.reconcileDomainService = reconcileDomainService;
    }

    @JobInvocable
    public String reconcile(TaskExecutionContext context) {
        AutoGrantReconcileDomainService.ReconcileReport report =
            reconcileDomainService.reconcile(context.tenantId());
        if (report.clean()) {
            log.info("auto-grant reconcile tenant {}: {}", context.tenantId(), report.summary());
        } else {
            report.roleDriftDetails().forEach(log::warn);
            report.declarationIssueDetails().forEach(log::warn);
            log.warn("auto-grant reconcile tenant {}: {}", context.tenantId(), report.summary());
        }
        return report.summary();
    }
}
