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
 * 演示服务无数据库：固定种子报表 + 运行期 create 产物。资源实体本身在 access-service
 * 登记（资源树）；本仓只承载业务数据。种子明细行是 depend_on 子权限示例（父=所属报表）。
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

    private final Map<String, DemoReport> reports = new ConcurrentHashMap<>();
    private final AtomicLong createSequence = new AtomicLong(100);

    public ReportStore() {
        SEED.forEach(r -> reports.put(r.code(), r));
    }

    public Optional<DemoReport> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(reports.get(code));
    }

    public List<DemoReport> all() {
        return reports.values().stream()
            .sorted(java.util.Comparator.comparing(DemoReport::code))
            .toList();
    }

    /** CREATE 路由产物：生成业务码（资源实体登记由接入方经管理面完成，演示省略）。 */
    public DemoReport create(String name) {
        String code = "report-" + createSequence.incrementAndGet();
        DemoReport created = new DemoReport(code, name, "created report content (" + code + ")");
        reports.put(code, created);
        return created;
    }
}
