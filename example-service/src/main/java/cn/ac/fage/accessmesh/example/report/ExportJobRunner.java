package cn.ac.fage.accessmesh.example.report;

import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 演示异步导出作业（T-ACCESS-061 §8.6「异步作业」行 + N24）。
 * <p>
 * §8.6 口径：提交与实际执行各自鉴权——提交时点检查 EXPORT（拒绝则不入队）只是第一道；
 * 真正取数前在<b>执行时点</b>重查同一目标，不永久复用提交时点结论绕过撤权（提交后、
 * 执行前撤权 → 执行时点检查拒绝，作业终态 DENIED）。重查走与在线请求同一
 * {@link BusinessPermChecker}（租户/主体与提交时的环境上下文 clientIp 一并在提交时
 * 捕获、显式传入调度线程，不依赖已结束的 servlet 上下文；执行时同样检查固定凭证租户）。
 * </p>
 */
@Component
public class ExportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportJobRunner.class);

    /** 作业终态机：PENDING → RUNNING → DONE（取数成功）/ DENIED（执行时点检查拒绝）/ FAILED（检查不可用或取数失败）。 */
    public enum Status { PENDING, RUNNING, DONE, DENIED, FAILED }

    /** 作业视图（状态字段 volatile：调度线程写、请求线程读）。 */
    public static final class ExportJob {
        private final String jobId;
        private final String reportCode;
        private final String tenantId;
        private final String userId;
        private final String clientIp;
        private volatile Status status;
        private volatile String content;
        private volatile String reason;

        private ExportJob(String jobId, String reportCode, String tenantId, String userId,
                          String clientIp, Status status) {
            this.jobId = jobId;
            this.reportCode = reportCode;
            this.tenantId = tenantId;
            this.userId = userId;
            this.clientIp = clientIp;
            this.status = status;
        }

        public String jobId() {
            return jobId;
        }

        public String reportCode() {
            return reportCode;
        }

        public String tenantId() {
            return tenantId;
        }

        public String userId() {
            return userId;
        }

        public String clientIp() {
            return clientIp;
        }

        public Status status() {
            return status;
        }

        public String content() {
            return content;
        }

        public String reason() {
            return reason;
        }
    }

    private final BusinessPermChecker permChecker;
    private final ReportStore reportStore;
    private final ScheduledExecutorService executor;
    private final long delayMillis;
    private final Map<String, ExportJob> jobs = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong jobSequence = new java.util.concurrent.atomic.AtomicLong();

    public ExportJobRunner(BusinessPermChecker permChecker, ReportStore reportStore,
                           @Value("${example.export.delay-ms:1500}") long delayMillis) {
        this.permChecker = permChecker;
        this.reportStore = reportStore;
        this.delayMillis = delayMillis;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "example-export-job");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 提交导出作业（提交时点检查已由调用方完成并放行）。
     * 返回作业 ID；延迟 {@code example.export.delay-ms} 后在调度线程执行取数+执行时点重查。
     */
    public ExportJob submit(String reportCode, String tenantId, String userId, String clientIp) {
        String jobId = "job-" + jobSequence.incrementAndGet();
        ExportJob job = new ExportJob(jobId, reportCode, tenantId, userId, clientIp, Status.PENDING);
        jobs.put(jobId, job);
        executor.schedule(() -> execute(job), delayMillis, TimeUnit.MILLISECONDS);
        return job;
    }

    public Optional<ExportJob> find(String jobId) {
        return jobId == null ? Optional.empty() : Optional.ofNullable(jobs.get(jobId));
    }

    /** 执行时点：重查 EXPORT → 允许才取数（N24：不能任一通过或提交时准入长期放行）。 */
    private void execute(ExportJob job) {
        job.status = Status.RUNNING;
        try {
            BusinessPermChecker.Decision decision = permChecker.check(job.tenantId(), job.userId(),
                job.clientIp(),
                new BusinessPermChecker.Target("EXAMPLE", job.reportCode(), "EXPORT",
                    null, null, null, null));
            if (!decision.allowed()) {
                job.reason = decision.reason();
                job.status = Status.DENIED;
                log.info("Export job denied at execution time (job={}, report={}, reason={})",
                    job.jobId(), job.reportCode(), decision.reason());
                return;
            }
            DemoReport report = reportStore.find(job.tenantId(), job.reportCode()).orElse(null);
            if (report == null) {
                job.reason = "REPORT_NOT_FOUND";
                job.status = Status.FAILED;
                return;
            }
            job.content = report.content();
            job.status = Status.DONE;
        } catch (Exception e) {
            // 检查不可用（fail-closed 30005）或意外异常：作业失败，绝不带数据返回
            job.reason = e.getMessage();
            job.status = Status.FAILED;
            log.warn("Export job failed at execution time (job={})", job.jobId(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
