package cn.ac.fage.accessmesh.access.admin.schedule;

import cn.ac.fage.accessmesh.access.admin.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务配置对账组件（T-ACCESS-009 用户决策：周期对账 + 触发时重读）
 * <p>
 * 任务 CRUD（创建/更新/停用/删除）只操作当前实例的内存调度表，其他实例
 * 通过本组件周期性从数据库重载启用任务并 diff 重调度，收敛跨实例配置漂移
 * （漂移窗口约为对账间隔 60s + 单轮对账耗时）。窗口内以数据库行为准：
 * 计划触发出发点会重读任务行，已删除/已停用任务跳过执行。Cron 变更在窗口内
 * 旧实例可能按旧 cron 多触发一次，该执行携带独立执行键并走完整租约/幂等治理
 * （避免实时广播的过度设计）。
 * </p>
 */
@Component
public class JobScheduleReconciler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduleReconciler.class);

    private final JobService jobService;

    public JobScheduleReconciler(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 每 60 秒对账一轮（初延迟 60s：启动加载 @PostConstruct 先行，错峰首轮）。
     */
    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    public void reconcile() {
        try {
            jobService.reconcileScheduledJobs();
        } catch (Exception e) {
            // @Scheduled 异常会被调度器记录，这里显式兜底保证下一轮继续
            log.error("Job schedule reconcile pass failed", e);
        }
    }
}
