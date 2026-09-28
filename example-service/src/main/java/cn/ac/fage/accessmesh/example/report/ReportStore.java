package cn.ac.fage.accessmesh.example.report;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 演示报表内存仓（T-ACCESS-061）。
 * <p>
 * 演示服务无数据库：固定种子报表 + 运行期 create 产物，<b>按租户分区存取</b>——
 * 资源实体在 access-service 按租户登记，业务数据同口径隔离：同码跨租户互不可见，
 * 种子每租户各一份（DemoReport 为不可变 record，跨租户共享种子实例安全）。
 * 种子明细行是 depend_on 子权限示例（父=所属报表）。
 * </p>
 */
@Component
public class ReportStore {

    /** 种子报表：与 e2e/联调里登记的资源实体码一致（report-1..report-3 + 明细行）。 */
    private static final List<DemoReport> SEED = List.of(
        new DemoReport("report-1", "销售日报", "sales daily report content (report-1)"),
        new DemoReport("report-2", "库存周报", "inventory weekly report content (report-2)"),
        new DemoReport("report-3", "财务月报", "finance monthly report content (report-3)"),
        new DemoReport("report-1-detail", "销售日报-明细", "sales detail rows (child of report-1)"),
        new DemoReport("report-2-detail", "库存周报-明细", "inventory detail rows (child of report-2)"));

    private final Map<String, Map<String, DemoReport>> reportsByTenant = new ConcurrentHashMap<>();
    private final AtomicLong createSequence = new AtomicLong(100);

    public Optional<DemoReport> find(String tenantId, String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantReports(tenantId).get(code));
    }

    public List<DemoReport> all(String tenantId) {
        return tenantReports(tenantId).values().stream()
            .sorted(java.util.Comparator.comparing(DemoReport::code))
            .toList();
    }

    /** CREATE 路由产物：生成业务码（资源实体登记由接入方经管理面完成，演示省略）。 */
    public DemoReport create(String tenantId, String name) {
        String code = "report-" + createSequence.incrementAndGet();
        DemoReport created = new DemoReport(code, name, "created report content (" + code + ")");
        tenantReports(tenantId).put(code, created);
        return created;
    }

    /** 每租户一份种子，首次访问惰性初始化（不预判租户集；computeIfAbsent 保证并发下单次播种）。 */
    private Map<String, DemoReport> tenantReports(String tenantId) {
        return reportsByTenant.computeIfAbsent(tenantId, tenant -> {
            Map<String, DemoReport> seeded = new ConcurrentHashMap<>();
            SEED.forEach(r -> seeded.put(r.code(), r));
            return seeded;
        });
    }
}
