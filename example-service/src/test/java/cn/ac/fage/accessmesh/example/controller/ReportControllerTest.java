package cn.ac.fage.accessmesh.example.controller;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.BatchViewReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.BatchViewResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.CreateReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ExportStatusReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ExportStatusResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ExportSubmitReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ListReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ListResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.SubViewReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ViewReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ViewResp;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Decision;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Target;
import cn.ac.fage.accessmesh.example.report.ExportJobRunner;
import cn.ac.fage.accessmesh.example.report.ReportStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §8.6 逐路由最终检查反向拒绝测试（T-ACCESS-061 迁移资格载体——契约 §25.7：
 * 迁移资格=最终检查的代码位置+反向拒绝测试）。
 * <p>
 * 逐路由锁两件事：①检查目标=业务请求实际解析值（查 A 不得按 B 取数，ArgumentCaptor
 * 逐参核对）；②检查拒绝时业务确实拒绝（30004 信封 / 作业 DENIED），任一允许不放行整批。
 * </p>
 */
class ReportControllerTest {

    private static final String USER = "42";
    private static final String TENANT = "7";

    private final BusinessPermChecker permChecker = mock(BusinessPermChecker.class);
    private final ReportStore reportStore = new ReportStore();
    private final ExportJobRunner exportJobRunner = new ExportJobRunner(permChecker, reportStore, 10);
    private final ReportController controller = new ReportController(permChecker, reportStore, exportJobRunner);

    // ------------------------------------------------------------------
    // 查看：实际资源+对应操作
    // ------------------------------------------------------------------

    @Test
    @DisplayName("view：检查目标=请求实际 reportCode，允许后返回同一报表的数据")
    void view_checksActualTargetAndReturnsSameReport() {
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenReturn(new Decision(true, null));

        ViewResp resp = controller.view(new ViewReq("report-1"), USER, TENANT).getData();

        assertThat(resp.reportCode()).isEqualTo("report-1");
        assertThat(resp.content()).contains("report-1");
        // 检查目标与取数目标同为请求值（查 A 不按 B 取数）
        verify(permChecker).check(eq(TENANT), eq(USER), eq(Target.of("EXAMPLE", "report-1", "VIEW")));
    }

    @Test
    @DisplayName("view 反向拒绝：最终检查拒绝 → 30004，不返回任何数据（N01 业务半边）")
    void view_deniedByFinalCheck_throws30004() {
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenReturn(new Decision(false, "NO_PERMISSION"));

        assertThatThrownBy(() -> controller.view(new ViewReq("report-2"), USER, TENANT))
            .isInstanceOf(BizException.class)
            .satisfies(e -> {
                assertThat(((BizException) e).getErrorCode()).isEqualTo(30004);
                assertThat(e.getMessage()).contains("NO_PERMISSION");
            });
    }

    @Test
    @DisplayName("身份头缺失：30002（主体只取可信认证链，N25）")
    void missingIdentityHeaders_rejectedWith30002() {
        assertThatThrownBy(() -> controller.view(new ViewReq("report-1"), null, TENANT))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30002));
    }

    // ------------------------------------------------------------------
    // 独立批量：每目标一个 DECISION 项
    // ------------------------------------------------------------------

    @Test
    @DisplayName("batch-view：混入未授权目标——逐项独立，任一允许不放行整批（N24）")
    void batchView_mixedTargets_perItemIndependent() {
        when(permChecker.batchCheck(anyString(), anyString(), anyString(), any(List.class), anyString()))
            .thenReturn(java.util.Map.of(
                "report-1", new Decision(true, null),
                "report-2", new Decision(false, "NO_PERMISSION")));

        BatchViewResp resp = controller.batchView(
            new BatchViewReq(List.of("report-1", "report-2")), USER, TENANT).getData();

        assertThat(resp.allowedCount()).isEqualTo(1);
        assertThat(resp.deniedCount()).isEqualTo(1);
        var allowed = resp.items().stream().filter(i -> i.reportCode().equals("report-1")).findFirst().orElseThrow();
        var denied = resp.items().stream().filter(i -> i.reportCode().equals("report-2")).findFirst().orElseThrow();
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.content()).isNotNull();
        // 拒绝项零数据内容（只回原因）
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.content()).isNull();
        assertThat(denied.name()).isNull();
        assertThat(denied.reason()).isEqualTo("NO_PERMISSION");
    }

    @Test
    @DisplayName("batch-view 反向拒绝：整批无权限 → 全部拒绝项（无一条带数据）")
    void batchView_allDenied_noDataLeak() {
        when(permChecker.batchCheck(anyString(), anyString(), anyString(), any(List.class), anyString()))
            .thenReturn(java.util.Map.of(
                "report-1", new Decision(false, "NO_PERMISSION"),
                "report-2", new Decision(false, "NO_PERMISSION")));

        BatchViewResp resp = controller.batchView(
            new BatchViewReq(List.of("report-1", "report-2")), USER, TENANT).getData();

        assertThat(resp.allowedCount()).isZero();
        assertThat(resp.items()).allSatisfy(i -> {
            assertThat(i.allowed()).isFalse();
            assertThat(i.content()).isNull();
        });
    }

    // ------------------------------------------------------------------
    // 列表/搜索：范围过滤 + 分页 total 同口径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("list：范围外报表不出现在数据与 total 中（过滤/total 同口径）")
    void list_filtersByScope_totalSameScope() {
        when(permChecker.accessibleCodes(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Set.of("report-1", "report-2")); // report-3 与明细行不在范围内

        ListResp resp = controller.list(new ListReq(null, 1, 10), USER, TENANT).getData();

        assertThat(resp.items()).extracting("reportCode")
            .containsExactly("report-1", "report-2");
        assertThat(resp.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("list 反向拒绝：零可见资源 → 空列表（total=0，不放大也不 403——范围语义由业务明确）")
    void list_emptyScope_returnsEmptyWithZeroTotal() {
        when(permChecker.accessibleCodes(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Set.of());

        ListResp resp = controller.list(new ListReq(null, 1, 10), USER, TENANT).getData();

        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isZero();
    }

    @Test
    @DisplayName("list：搜索关键词在同范围内过滤，total 同口径收缩")
    void list_keywordWithinScope() {
        when(permChecker.accessibleCodes(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Set.of("report-1", "report-2"));

        ListResp resp = controller.list(new ListReq("库存", 1, 10), USER, TENANT).getData();

        assertThat(resp.items()).extracting("reportCode").containsExactly("report-2");
        assertThat(resp.total()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // CREATE：最终 TYPE_LEVEL
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：TYPE_LEVEL 检查（resourceCode=null），允许后创建")
    void create_checksTypeLevel() {
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenReturn(new Decision(true, null));

        var resp = controller.create(new CreateReq("新报表"), USER, TENANT).getData();

        assertThat(resp.name()).isEqualTo("新报表");
        ArgumentCaptor<Target> captor = ArgumentCaptor.forClass(Target.class);
        verify(permChecker).check(eq(TENANT), eq(USER), captor.capture());
        // CREATE=类型级：实例准入不授予类型创建权（resourceCode 必须为 null）
        assertThat(captor.getValue().resourceCode()).isNull();
        assertThat(captor.getValue().operationCode()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("create 反向拒绝：无类型级 CREATE → 30004，不产生数据")
    void create_denied_throws30004() {
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenReturn(new Decision(false, "NO_PERMISSION"));

        assertThatThrownBy(() -> controller.create(new CreateReq("新报表"), USER, TENANT))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30004));
    }

    // ------------------------------------------------------------------
    // 上下文子权限：真实父 + 引擎验证父授权绑定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sub-view：携带真实父资源与父操作 [VIEW] 交引擎验证父绑定（N26 载体）")
    void subView_passesRealParentContext() {
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenReturn(new Decision(true, null));

        var resp = controller.subView(
            new SubViewReq("report-1-detail", "report-1"), USER, TENANT).getData();

        assertThat(resp.subReportCode()).isEqualTo("report-1-detail");
        verify(permChecker).check(eq(TENANT), eq(USER), eq(Target.childOf(
            "EXAMPLE", "report-1-detail", "SUB_VIEW", "EXAMPLE", "report-1", List.of("VIEW"))));
    }

    @Test
    @DisplayName("sub-view 反向拒绝：错父（引擎 DEPENDENT_NOT_IN_PARENT_CONTEXT）→ 30004")
    void subView_wrongParent_denied() {
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenReturn(new Decision(false, "DEPENDENT_NOT_IN_PARENT_CONTEXT"));

        assertThatThrownBy(() -> controller.subView(
                new SubViewReq("report-1-detail", "report-2"), USER, TENANT))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30004));
    }

    // ------------------------------------------------------------------
    // 异步作业：提交与执行时点各自鉴权（N24）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("export：提交时点检查 EXPORT（拒绝不入队）；执行时点允许 → DONE 带内容")
    void export_submitChecksAndExecutionAllows() {
        AtomicReference<Decision> decision = new AtomicReference<>(new Decision(true, null));
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenAnswer(inv -> decision.get());

        var submit = controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT).getData();

        ExportStatusResp done = awaitStatus(submit.jobId());
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.content()).contains("report-1");
        // 提交时点与执行时点各查一次同一目标（N24 双时点；终态后计数确定，不与异步重查抢跑）
        verify(permChecker, times(2)).check(eq(TENANT), eq(USER), eq(Target.of("EXAMPLE", "report-1", "EXPORT")));
    }

    @Test
    @DisplayName("export 提交时点反向拒绝：无 EXPORT → 30004，不产生作业")
    void export_submitDenied_throws30004() {
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenReturn(new Decision(false, "NO_PERMISSION"));

        assertThatThrownBy(() -> controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30004));
    }

    @Test
    @DisplayName("export 撤权窗口（N24）：提交允许、执行时点重查拒绝 → 作业终态 DENIED 零内容——不永久复用提交时点结论")
    void export_revokedBetweenSubmitAndExecution_deniedAtExecutionTime() {
        AtomicReference<Decision> decision = new AtomicReference<>(new Decision(true, null));
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenAnswer(inv -> decision.get());

        var submit = controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT).getData();
        // 提交后、执行前撤权（第一次调用=提交时点已返回允许；后续执行时点检查拒绝）
        decision.set(new Decision(false, "NO_PERMISSION"));

        ExportStatusResp denied = awaitStatus(submit.jobId());
        assertThat(denied.status()).isEqualTo("DENIED");
        assertThat(denied.content()).isNull();
        assertThat(denied.reason()).isEqualTo("NO_PERMISSION");
    }

    @Test
    @DisplayName("export 执行时点鉴权不可用（fail-closed 30005）→ 作业终态 FAILED 零内容")
    void export_checkUnavailableAtExecution_failsClosed() {
        AtomicReference<Decision> decision = new AtomicReference<>(new Decision(true, null));
        when(permChecker.check(anyString(), anyString(), any(Target.class)))
            .thenAnswer(inv -> {
                Decision d = decision.get();
                if (d == null) {
                    throw new BizException(30005, "鉴权服务暂不可用");
                }
                return d;
            });

        var submit = controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT).getData();
        decision.set(null); // 执行时点：鉴权不可用

        ExportStatusResp failed = awaitStatus(submit.jobId());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.content()).isNull();
    }

    /** 轮询作业到终态（DONE/DENIED/FAILED）——确定性等待，不用裸 sleep 余量。 */
    private ExportStatusResp awaitStatus(String jobId) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ExportStatusResp s = controller.exportStatus(new ExportStatusReq(jobId), USER, TENANT).getData();
            assertThat(s.status()).isIn("DONE", "DENIED", "FAILED");
        });
        return controller.exportStatus(new ExportStatusReq(jobId), USER, TENANT).getData();
    }
}
