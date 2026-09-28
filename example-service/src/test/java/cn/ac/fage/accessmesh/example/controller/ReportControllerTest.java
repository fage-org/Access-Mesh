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
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Decision;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Scope;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Target;
import cn.ac.fage.accessmesh.example.report.ExportJobRunner;
import cn.ac.fage.accessmesh.example.report.ReportStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * 另锁三条隔离/上下文铁律：③业务数据按租户分区（同码跨租户互不可见）；④导出作业仅
 * 归属人可查（他人/他租户=与不存在同口径）；⑤可信 clientIp（网关重建 X-Forwarded-For）
 * 经 checker 传入条件评估、异步作业提交时捕获重放。
 * </p>
 * <p>
 * 撤权窗口用例不依赖延迟余量：mock answer 按调用序号返回（第 1 次=提交时点、恒先于
 * 第 2 次=执行时点——submit 内 check 先于 schedule 完成是同步序），线程调度快慢不改变语义。
 * </p>
 */
class ReportControllerTest {

    private static final String USER = "42";
    private static final String TENANT = "7";
    private static final String OTHER_USER = "43";
    private static final String OTHER_TENANT = "8";
    private static final String CLIENT_IP = "10.0.0.9";

    private final BusinessPermChecker permChecker = mock(BusinessPermChecker.class);
    private final ReportStore reportStore = new ReportStore();
    private final ExportJobRunner exportJobRunner = new ExportJobRunner(permChecker, reportStore, 10);
    private final ReportController controller = new ReportController(permChecker, reportStore, exportJobRunner);

    /** 提交时点允许、执行时点拒绝的确定性 answer（按调用序号，无延迟余量竞态）。 */
    private void allowFirstCallThenDeny(String reason) {
        AtomicInteger calls = new AtomicInteger();
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenAnswer(inv -> calls.incrementAndGet() == 1
                ? new Decision(true, null)
                : new Decision(false, reason));
    }

    // ------------------------------------------------------------------
    // 查看：实际资源+对应操作
    // ------------------------------------------------------------------

    @Test
    @DisplayName("view：检查目标=请求实际 reportCode，允许后返回同一报表的数据")
    void view_checksActualTargetAndReturnsSameReport() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(true, null));

        ViewResp resp = controller.view(new ViewReq("report-1"), USER, TENANT, CLIENT_IP).getData();

        assertThat(resp.reportCode()).isEqualTo("report-1");
        assertThat(resp.content()).contains("report-1");
        // 检查目标与取数目标同为请求值（查 A 不按 B 取数）；clientIp 从可信链透传
        verify(permChecker).check(eq(TENANT), eq(USER), eq(CLIENT_IP),
            eq(Target.of("EXAMPLE", "report-1", "VIEW")));
    }

    @Test
    @DisplayName("view 反向拒绝：最终检查拒绝 → 30004，不返回任何数据（N01 业务半边）")
    void view_deniedByFinalCheck_throws30004() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(false, "NO_PERMISSION"));

        assertThatThrownBy(() -> controller.view(new ViewReq("report-2"), USER, TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> {
                assertThat(((BizException) e).getErrorCode()).isEqualTo(30004);
                assertThat(e.getMessage()).contains("NO_PERMISSION");
            });
    }

    @Test
    @DisplayName("身份头缺失：30002（主体只取可信认证链，N25）")
    void missingIdentityHeaders_rejectedWith30002() {
        assertThatThrownBy(() -> controller.view(new ViewReq("report-1"), null, TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30002));
    }

    // ------------------------------------------------------------------
    // 独立批量：每目标一个 DECISION 项
    // ------------------------------------------------------------------

    @Test
    @DisplayName("batch-view：混入未授权目标——逐项独立，任一允许不放行整批（N24）")
    void batchView_mixedTargets_perItemIndependent() {
        when(permChecker.batchCheck(anyString(), anyString(), any(), anyString(), any(List.class), anyString()))
            .thenReturn(java.util.Map.of(
                "report-1", new Decision(true, null),
                "report-2", new Decision(false, "NO_PERMISSION")));

        BatchViewResp resp = controller.batchView(
            new BatchViewReq(List.of("report-1", "report-2")), USER, TENANT, CLIENT_IP).getData();

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
        when(permChecker.batchCheck(anyString(), anyString(), any(), anyString(), any(List.class), anyString()))
            .thenReturn(java.util.Map.of(
                "report-1", new Decision(false, "NO_PERMISSION"),
                "report-2", new Decision(false, "NO_PERMISSION")));

        BatchViewResp resp = controller.batchView(
            new BatchViewReq(List.of("report-1", "report-2")), USER, TENANT, null).getData();

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
        when(permChecker.accessibleScope(anyString(), anyString(), any(), anyString(), anyString()))
            .thenReturn(new Scope(false, java.util.Set.of("report-1", "report-2"))); // report-3 与明细行不在范围内

        ListResp resp = controller.list(new ListReq(null, 1, 10), USER, TENANT, CLIENT_IP).getData();

        assertThat(resp.items()).extracting("reportCode")
            .containsExactly("report-1", "report-2");
        assertThat(resp.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("list：全量授权（scopeMode=ALL）→ 类型级全量可见，不按码过滤出空列表")
    void list_allScope_returnsAllReportsNotFilteredToEmpty() {
        when(permChecker.accessibleScope(anyString(), anyString(), any(), anyString(), anyString()))
            .thenReturn(new Scope(true, java.util.Set.of()));

        ListResp resp = controller.list(new ListReq(null, 1, 10), USER, TENANT, CLIENT_IP).getData();

        // 仓内 5 份种子报表全部可见（旧实现只收集 resourceCode，ALL 行 code=null → total=0）
        assertThat(resp.total()).isEqualTo(5);
        assertThat(resp.items()).hasSize(5);
    }

    @Test
    @DisplayName("list 反向拒绝：零可见资源 → 空列表（total=0，不放大也不 403——范围语义由业务明确）")
    void list_emptyScope_returnsEmptyWithZeroTotal() {
        when(permChecker.accessibleScope(anyString(), anyString(), any(), anyString(), anyString()))
            .thenReturn(new Scope(false, java.util.Set.of()));

        ListResp resp = controller.list(new ListReq(null, 1, 10), USER, TENANT, null).getData();

        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isZero();
    }

    @Test
    @DisplayName("list：搜索关键词在同范围内过滤，total 同口径收缩")
    void list_keywordWithinScope() {
        when(permChecker.accessibleScope(anyString(), anyString(), any(), anyString(), anyString()))
            .thenReturn(new Scope(false, java.util.Set.of("report-1", "report-2")));

        ListResp resp = controller.list(new ListReq("库存", 1, 10), USER, TENANT, null).getData();

        assertThat(resp.items()).extracting("reportCode").containsExactly("report-2");
        assertThat(resp.total()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // CREATE：最终 TYPE_LEVEL
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：TYPE_LEVEL 检查（resourceCode=null），允许后创建")
    void create_checksTypeLevel() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(true, null));

        var resp = controller.create(new CreateReq("新报表"), USER, TENANT, CLIENT_IP).getData();

        assertThat(resp.name()).isEqualTo("新报表");
        ArgumentCaptor<Target> captor = ArgumentCaptor.forClass(Target.class);
        verify(permChecker).check(eq(TENANT), eq(USER), eq(CLIENT_IP), captor.capture());
        // CREATE=类型级：实例准入不授予类型创建权（resourceCode 必须为 null）
        assertThat(captor.getValue().resourceCode()).isNull();
        assertThat(captor.getValue().operationCode()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("create 反向拒绝：无类型级 CREATE → 30004，不产生数据")
    void create_denied_throws30004() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(false, "NO_PERMISSION"));

        assertThatThrownBy(() -> controller.create(new CreateReq("新报表"), USER, TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30004));
    }

    // ------------------------------------------------------------------
    // 上下文子权限：真实父 + 引擎验证父绑定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sub-view：携带真实父资源与父操作 [VIEW] 交引擎验证父绑定（N26 载体）")
    void subView_passesRealParentContext() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(true, null));

        var resp = controller.subView(
            new SubViewReq("report-1-detail", "report-1"), USER, TENANT, CLIENT_IP).getData();

        assertThat(resp.subReportCode()).isEqualTo("report-1-detail");
        verify(permChecker).check(eq(TENANT), eq(USER), eq(CLIENT_IP), eq(Target.childOf(
            "EXAMPLE", "report-1-detail", "SUB_VIEW", "EXAMPLE", "report-1", List.of("VIEW"))));
    }

    @Test
    @DisplayName("sub-view 反向拒绝：错父（引擎 DEPENDENT_NOT_IN_PARENT_CONTEXT）→ 30004")
    void subView_wrongParent_denied() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(false, "DEPENDENT_NOT_IN_PARENT_CONTEXT"));

        assertThatThrownBy(() -> controller.subView(
                new SubViewReq("report-1-detail", "report-2"), USER, TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30004));
    }

    // ------------------------------------------------------------------
    // 异步作业：提交与执行时点各自鉴权（N24）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("export：提交时点检查 EXPORT（拒绝不入队）；执行时点允许 → DONE 带内容")
    void export_submitChecksAndExecutionAllows() {
        AtomicInteger calls = new AtomicInteger();
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenAnswer(inv -> calls.incrementAndGet() <= 2
                ? new Decision(true, null)
                : new Decision(false, "NO_PERMISSION"));

        var submit = controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT, CLIENT_IP).getData();

        ExportStatusResp done = awaitStatus(submit.jobId(), USER, TENANT);
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.content()).contains("report-1");
        // 提交时点与执行时点各查一次同一目标且透传提交时捕获的 clientIp（N24 双时点；
        // 终态后计数确定，不与异步重查抢跑）
        verify(permChecker, times(2)).check(eq(TENANT), eq(USER), eq(CLIENT_IP),
            eq(Target.of("EXAMPLE", "report-1", "EXPORT")));
    }

    @Test
    @DisplayName("export 提交时点反向拒绝：无 EXPORT → 30004，不产生作业")
    void export_submitDenied_throws30004() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(false, "NO_PERMISSION"));

        assertThatThrownBy(() -> controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(30004));
    }

    @Test
    @DisplayName("export 撤权窗口（N24）：提交允许、执行时点重查拒绝 → DENIED 零内容——answer 按调用序号定序，无延迟余量竞态")
    void export_revokedBetweenSubmitAndExecution_deniedAtExecutionTime() {
        allowFirstCallThenDeny("NO_PERMISSION");

        var submit = controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT, null).getData();

        // 第 1 次 check=提交时点（submit 内同步完成，恒先于调度线程的第 2 次=执行时点）
        ExportStatusResp denied = awaitStatus(submit.jobId(), USER, TENANT);
        assertThat(denied.status()).isEqualTo("DENIED");
        assertThat(denied.content()).isNull();
        assertThat(denied.reason()).isEqualTo("NO_PERMISSION");
    }

    @Test
    @DisplayName("export 执行时点鉴权不可用（fail-closed 30005）→ 作业终态 FAILED 零内容")
    void export_checkUnavailableAtExecution_failsClosed() {
        AtomicInteger calls = new AtomicInteger();
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenAnswer(inv -> {
                if (calls.incrementAndGet() == 1) {
                    return new Decision(true, null);
                }
                throw new BizException(30005, "鉴权服务暂不可用");
            });

        var submit = controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT, null).getData();

        ExportStatusResp failed = awaitStatus(submit.jobId(), USER, TENANT);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.content()).isNull();
    }

    @Test
    @DisplayName("export/status 归属校验：他人/他租户作业=与不存在同口径拒绝（防按递增 jobId 枚举他人导出内容）")
    void exportStatus_foreignJob_sameResponseAsNotFound() {
        allowFirstCallThenDeny("NO_PERMISSION");

        String jobId = controller.exportSubmit(new ExportSubmitReq("report-1"), USER, TENANT, null)
            .getData().jobId();
        awaitStatus(jobId, USER, TENANT);

        // 同租户他人
        assertThatThrownBy(() -> controller.exportStatus(new ExportStatusReq(jobId), OTHER_USER, TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> {
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(ExampleErrorCode.DEMO_PARAM_INVALID.getCode());
                assertThat(e.getMessage()).contains("导出作业不存在");
            });
        // 他租户（同人）
        assertThatThrownBy(() -> controller.exportStatus(new ExportStatusReq(jobId), USER, OTHER_TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(e.getMessage()).contains("导出作业不存在"));
    }

    // ------------------------------------------------------------------
    // 业务数据租户隔离
    // ------------------------------------------------------------------

    @Test
    @DisplayName("view：同码跨租户互不可见（B 租户鉴权通过也取不到 A 租户创建的同码报表）")
    void view_sameCode_crossTenantIsolated() {
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(true, null));
        // A 租户创建报表（得 report-101）
        var created = controller.create(new CreateReq("租户A的报表"), USER, TENANT, null).getData();
        String code = created.reportCode();

        // B 租户同码查看：即使鉴权放行，本租户分区内不存在该码（旧实现全局存取会返回 A 的数据）
        assertThatThrownBy(() -> controller.view(new ViewReq(code), "50", OTHER_TENANT, null))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(e.getMessage()).contains("报表不存在"));
    }

    @Test
    @DisplayName("种子按租户各一份：两租户互不放大对方数据，本租户种子完整可见")
    void seeds_perTenantIndependent() {
        when(permChecker.accessibleScope(anyString(), anyString(), any(), anyString(), anyString()))
            .thenReturn(new Scope(true, java.util.Set.of()));
        when(permChecker.check(anyString(), anyString(), any(), any(Target.class)))
            .thenReturn(new Decision(true, null));

        var created = controller.create(new CreateReq("仅A可见"), USER, TENANT, null).getData();

        ListResp inA = controller.list(new ListReq(null, 1, 10), USER, TENANT, null).getData();

        ListResp inB = controller.list(new ListReq(null, 1, 100), "50", OTHER_TENANT, null).getData();

        assertThat(inA.total()).isEqualTo(6); // 5 种子 + 1 新建
        assertThat(inB.total()).isEqualTo(5); // B 只见自己的种子，A 的 create 产物不可见
        assertThat(inB.items()).extracting("reportCode").doesNotContain(created.reportCode());
    }

    /** 轮询作业到终态（DONE/DENIED/FAILED）——确定性等待，不用裸 sleep 余量。 */
    private ExportStatusResp awaitStatus(String jobId, String userId, String tenantId) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            ExportStatusResp s = controller.exportStatus(new ExportStatusReq(jobId), userId, tenantId, null)
                .getData();
            assertThat(s.status()).isIn("DONE", "DENIED", "FAILED");
        });
        return controller.exportStatus(new ExportStatusReq(jobId), userId, tenantId, null).getData();
    }
}
