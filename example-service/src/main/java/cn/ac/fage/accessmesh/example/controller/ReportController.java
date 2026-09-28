package cn.ac.fage.accessmesh.example.controller;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.BatchViewItem;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.BatchViewReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.BatchViewResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.CreateReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.CreateResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ExportStatusReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ExportStatusResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ExportSubmitReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ExportSubmitResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ListItem;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ListReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ListResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.SubViewReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.SubViewResp;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ViewReq;
import cn.ac.fage.accessmesh.example.dto.DemoReportDtos.ViewResp;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Decision;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Scope;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Target;
import cn.ac.fage.accessmesh.example.report.DemoReport;
import cn.ac.fage.accessmesh.example.report.ExportJobRunner;
import cn.ac.fage.accessmesh.example.report.ExportJobRunner.ExportJob;
import cn.ac.fage.accessmesh.example.report.ReportStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 报表示例控制器（T-ACCESS-061 §8.6 业务入口最终检查表逐路由落地——example-service 先行）。
 * <p>
 * 两层判定的第二层：Gateway 操作准入（MAY_ENTER=存在类型级覆盖候选）放行后，本控制器
 * 对<b>业务请求实际解析出的目标</b>做最后一道实例级检查（{@link BusinessPermChecker}
 * → auth/check 族端点）。两层跨 HTTP 两次执行、不共享运行状态；网关放行绝不等于业务
 * 放行（N01：准入经 VIEW 候选放行，业务对未授权实例 B 拒绝）。
 * </p>
 * <p>
 * 主体/租户取自 Gateway 注入且已经 {@code GatewaySignatureFilter} 验签的
 * X-User-Id/X-Tenant-Id 请求头（缺失=30002）；请求 DTO 无主体/租户字段，客户端不可
 * 自报（N25）。环境上下文（clientIp）取网关清洗重建后的 X-Forwarded-For（T-GW-008
 * 下游统一消费口径）传入条件评估。最终检查拒绝=信封 30004（HTTP 200，example 域
 * 约定）；鉴权服务不可用=fail-closed 30005，不放行任何数据。业务数据按租户分区
 * （{@link ReportStore}），检查与取数共用同一租户。
 * </p>
 */
@RestController
@RequestMapping("/api/example/report")
public class ReportController {

    /** 示例资源族：类型 EXAMPLE，操作 VIEW/CREATE/EXPORT/SUB_VIEW（access-service 登记面）。 */
    private static final String TYPE_EXAMPLE = "EXAMPLE";
    private static final String OP_VIEW = "VIEW";
    private static final String OP_CREATE = "CREATE";
    private static final String OP_EXPORT = "EXPORT";
    private static final String OP_SUB_VIEW = "SUB_VIEW";

    private final BusinessPermChecker permChecker;
    private final ReportStore reportStore;
    private final ExportJobRunner exportJobRunner;

    public ReportController(BusinessPermChecker permChecker, ReportStore reportStore,
                            ExportJobRunner exportJobRunner) {
        this.permChecker = permChecker;
        this.reportStore = reportStore;
        this.exportJobRunner = exportJobRunner;
    }

    /** 查看单报表（§8.6 查看/预览行）：实际资源+对应操作，允许后才返回数据。 */
    @PostMapping("/view")
    public R<ViewResp> view(@Valid @RequestBody ViewReq req,
                            @RequestHeader(name = "X-User-Id", required = false) String userId,
                            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                            @RequestHeader(name = "X-Forwarded-For", required = false) String clientIp) {
        requireIdentity(userId, tenantId);
        Decision decision = permChecker.check(tenantId, userId, clientIp,
            Target.of(TYPE_EXAMPLE, req.reportCode(), OP_VIEW));
        if (!decision.allowed()) {
            throw denied(decision);
        }
        DemoReport report = reportStore.find(tenantId, req.reportCode())
            .orElseThrow(() -> new BizException(ExampleErrorCode.DEMO_PARAM_INVALID.getCode(),
                "报表不存在: " + req.reportCode()));
        // 检查的目标与取数的目标是同一个业务码（不能检查 A 却按另一参数读取 B）
        return R.ok(new ViewResp(report.code(), report.name(), report.content()));
    }

    /** 独立批量查看（§8.6 批量行）：每目标一个 DECISION 项，返回逐项结果——任一允许不放行整批。 */
    @PostMapping("/batch-view")
    public R<BatchViewResp> batchView(@Valid @RequestBody BatchViewReq req,
                                      @RequestHeader(name = "X-User-Id", required = false) String userId,
                                      @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                      @RequestHeader(name = "X-Forwarded-For", required = false) String clientIp) {
        requireIdentity(userId, tenantId);
        var byCode = permChecker.batchCheck(tenantId, userId, clientIp,
            TYPE_EXAMPLE, req.reportCodes(), OP_VIEW);
        List<BatchViewItem> items = new ArrayList<>(req.reportCodes().size());
        int allowed = 0;
        for (String code : req.reportCodes()) {
            Decision decision = byCode.getOrDefault(code, new Decision(false, "NOT_CHECKED"));
            if (decision.allowed()) {
                allowed++;
                DemoReport report = reportStore.find(tenantId, code).orElse(null);
                items.add(new BatchViewItem(code, true, null,
                    report == null ? null : report.name(), report == null ? null : report.content()));
            } else {
                // 拒绝项不携带任何数据内容（只回原因）
                items.add(new BatchViewItem(code, false, decision.reason(), null, null));
            }
        }
        return R.ok(new BatchViewResp(items, allowed, req.reportCodes().size() - allowed));
    }

    /** 列表/搜索（§8.6 列表/搜索行）：权限范围过滤落实到返回数据，total 与数据同口径。 */
    @PostMapping("/list")
    public R<ListResp> list(@Valid @RequestBody ListReq req,
                            @RequestHeader(name = "X-User-Id", required = false) String userId,
                            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                            @RequestHeader(name = "X-Forwarded-For", required = false) String clientIp) {
        requireIdentity(userId, tenantId);
        int page = req.page() == null || req.page() < 1 ? 1 : req.page();
        int size = req.size() == null || req.size() < 1 ? 10 : Math.min(req.size(), 100);
        // 同一权限范围先过滤再分页：total 与 items 恒同口径；ALL=类型级全量授权不按码过滤
        Scope scope = permChecker.accessibleScope(tenantId, userId, clientIp, TYPE_EXAMPLE, OP_VIEW);
        List<DemoReport> visible = reportStore.all(tenantId).stream()
            .filter(r -> scope.contains(r.code()))
            .filter(r -> req.keyword() == null || req.keyword().isBlank()
                || r.name().contains(req.keyword()) || r.code().contains(req.keyword()))
            .toList();
        int from = Math.min((page - 1) * size, visible.size());
        int to = Math.min(from + size, visible.size());
        List<ListItem> items = visible.subList(from, to).stream()
            .map(r -> new ListItem(r.code(), r.name()))
            .toList();
        return R.ok(new ListResp(items, visible.size(), page, size));
    }

    /** 创建报表（§8.6 CREATE 行）：最终 TYPE_LEVEL——resourceCode 传 null，实例准入不授予类型创建权。 */
    @PostMapping("/create")
    public R<CreateResp> create(@Valid @RequestBody CreateReq req,
                                @RequestHeader(name = "X-User-Id", required = false) String userId,
                                @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                @RequestHeader(name = "X-Forwarded-For", required = false) String clientIp) {
        requireIdentity(userId, tenantId);
        Decision decision = permChecker.check(tenantId, userId, clientIp,
            Target.of(TYPE_EXAMPLE, null, OP_CREATE));
        if (!decision.allowed()) {
            throw denied(decision);
        }
        DemoReport created = reportStore.create(tenantId, req.name());
        return R.ok(new CreateResp(created.code(), created.name()));
    }

    /**
     * 上下文子权限查看（§8.6 上下文子权限行）：depend_on 子资源 + 真实父资源/父操作，
     * 引擎验证父授权记录绑定——无父/错父由引擎拒绝（DEPENDENT_NOT_IN_PARENT_CONTEXT）。
     */
    @PostMapping("/sub-view")
    public R<SubViewResp> subView(@Valid @RequestBody SubViewReq req,
                                  @RequestHeader(name = "X-User-Id", required = false) String userId,
                                  @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                  @RequestHeader(name = "X-Forwarded-For", required = false) String clientIp) {
        requireIdentity(userId, tenantId);
        Decision decision = permChecker.check(tenantId, userId, clientIp,
            Target.childOf(TYPE_EXAMPLE, req.subReportCode(), OP_SUB_VIEW,
                TYPE_EXAMPLE, req.parentReportCode(), List.of(OP_VIEW)));
        if (!decision.allowed()) {
            throw denied(decision);
        }
        DemoReport sub = reportStore.find(tenantId, req.subReportCode())
            .orElseThrow(() -> new BizException(ExampleErrorCode.DEMO_PARAM_INVALID.getCode(),
                "子权限报表不存在: " + req.subReportCode()));
        return R.ok(new SubViewResp(sub.code(), req.parentReportCode(), sub.name(), sub.content()));
    }

    /** 提交异步导出（§8.6 异步作业行·提交时点）：提交时检查 EXPORT；执行时点重查在 ExportJobRunner。 */
    @PostMapping("/export/submit")
    public R<ExportSubmitResp> exportSubmit(@Valid @RequestBody ExportSubmitReq req,
                                            @RequestHeader(name = "X-User-Id", required = false) String userId,
                                            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                            @RequestHeader(name = "X-Forwarded-For", required = false) String clientIp) {
        requireIdentity(userId, tenantId);
        Decision decision = permChecker.check(tenantId, userId, clientIp,
            Target.of(TYPE_EXAMPLE, req.reportCode(), OP_EXPORT));
        if (!decision.allowed()) {
            throw denied(decision);
        }
        ExportJob job = exportJobRunner.submit(req.reportCode(), tenantId, userId, clientIp);
        return R.ok(new ExportSubmitResp(job.jobId(), job.reportCode()));
    }

    /**
     * 查询导出作业状态（仅作业归属人：租户+用户与提交时不符=与不存在同口径拒绝，不泄露
     * 作业存在性）：DONE 才携带内容；DENIED=执行时点重查拒绝（N24 撤权窗口演示）。
     */
    @PostMapping("/export/status")
    public R<ExportStatusResp> exportStatus(@Valid @RequestBody ExportStatusReq req,
                                            @RequestHeader(name = "X-User-Id", required = false) String userId,
                                            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                            @RequestHeader(name = "X-Forwarded-For", required = false) String clientIp) {
        requireIdentity(userId, tenantId);
        ExportJob job = exportJobRunner.find(req.jobId())
            .orElseThrow(() -> jobNotFound(req.jobId()));
        if (!job.tenantId().equals(tenantId) || !job.userId().equals(userId)) {
            // 他人/他租户作业与不存在同响应（防按递增 jobId 枚举探测他人导出内容）
            throw jobNotFound(req.jobId());
        }
        return R.ok(new ExportStatusResp(job.jobId(), job.reportCode(), job.status().name(),
            job.content(), job.reason()));
    }

    /** 身份头存在性校验（身份信任链由 GatewaySignatureFilter 前置完成；本服务只信已验签头）。 */
    private static void requireIdentity(String userId, String tenantId) {
        if (userId == null || tenantId == null) {
            throw new BizException(ExampleErrorCode.DEMO_IDENTITY_HEADER_MISSING.getCode(),
                ExampleErrorCode.DEMO_IDENTITY_HEADER_MISSING.getMessage());
        }
    }

    private static BizException jobNotFound(String jobId) {
        return new BizException(ExampleErrorCode.DEMO_PARAM_INVALID.getCode(),
            "导出作业不存在: " + jobId);
    }

    /** 业务最终检查拒绝：30004 信封（HTTP 200，example 域约定；与网关准入 403 形成层次区分）。 */
    private static BizException denied(Decision decision) {
        return new BizException(ExampleErrorCode.PERMISSION_DENIED.getCode(),
            ExampleErrorCode.PERMISSION_DENIED.getMessage()
                + (decision.reason() == null ? "" : "（reason=" + decision.reason() + "）"));
    }
}
