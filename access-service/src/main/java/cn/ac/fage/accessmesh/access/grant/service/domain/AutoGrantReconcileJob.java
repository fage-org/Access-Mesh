package cn.ac.fage.accessmesh.access.grant.service.domain;

import cn.ac.fage.accessmesh.access.infrastructure.task.JobInvocable;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自动授权对账任务入口（T-PERM-073，设计 §13；2026-09-21 用户定案：bootstrap 种子默认停用）。
 * <p>
 * {@code @JobInvocable} 白名单方法，经既有任务设施（sys_job 调度 + 手动触发，T-ACCESS-009）
 * 调用；invokeTarget = {@code autoGrantReconcileInvoker.reconcile}。对账只发现异常不修复；
 * 逐条差异 log.warn，汇总（String 返回值）经执行编排层写入任务执行日志成功消息。
 * 执行身份=TASK 可信上下文（上下文由编排层绑定），租户取任务行 tenantId。
 * </p>
 * <p>
 * 整轮对账的多类查询（编译图/角色清单/种子行/AUTO_DEP 行）经本入口的
 * {@code REPEATABLE_READ} 只读事务持同一快照（与 explain/preview 同款；任务设施反射
 * 调用链保留代理语义，注解经代理生效）——无注解时各查询分属不同时点，并发写窗口下
 * 日志可出现不可复现的假漂移。
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
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
