package cn.ac.fage.accessmesh.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 报表示例族请求/响应 DTO（T-ACCESS-061 §8.6 逐路由最终检查）。
 * <p>
 * 铁律：请求 DTO 不携带任何主体/租户字段——主体取自可信认证链（Gateway 注入且已验签
 * 的 X-User-Id/X-Tenant-Id 请求头），客户端无法自报（N25）；租户同理由服务端透传。
 * </p>
 */
public final class DemoReportDtos {

    private DemoReportDtos() {
    }

    /** 查看单报表：实际资源+对应操作（§8.6 查看/预览行——检查 A 就取 A 的数）。 */
    public record ViewReq(@NotBlank(message = "reportCode 不能为空") String reportCode) {
    }

    public record ViewResp(String reportCode, String name, String content) {
    }

    /** 独立批量查看：逐目标独立 DECISION（§8.6 批量行——任一允许不放行整批）。 */
    public record BatchViewReq(
        @NotEmpty(message = "reportCodes 不能为空")
        @Size(max = 100, message = "演示批量上限 100") List<@NotBlank String> reportCodes) {
    }

    public record BatchViewItem(String reportCode, boolean allowed, String reason,
                                String name, String content) {
    }

    public record BatchViewResp(List<BatchViewItem> items, int allowedCount, int deniedCount) {
    }

    /** 列表/搜索：权限范围过滤 + 分页 total 同口径（§8.6 列表/搜索行）。 */
    public record ListReq(String keyword, @Min(1) Integer pageNum, @Min(1) @Max(200) Integer pageSize) {
    }

    public record ListItem(String reportCode, String name) {
    }

    /** 创建：最终 TYPE_LEVEL（§8.6 CREATE 行——实例准入不授予类型创建权）。 */
    public record CreateReq(@NotBlank(message = "name 不能为空") @Size(max = 64) String name) {
    }

    public record CreateResp(String reportCode, String name) {
    }

    /** 上下文子权限查看：depend_on 子资源 + 真实父资源（§8.6 上下文子权限行——无父/错父拒绝）。 */
    public record SubViewReq(
        @NotBlank(message = "subReportCode 不能为空") String subReportCode,
        @NotBlank(message = "parentReportCode 不能为空") String parentReportCode) {
    }

    public record SubViewResp(String subReportCode, String parentReportCode, String name, String content) {
    }

    /** 异步导出：提交时点检查 EXPORT（§8.6 异步作业行——执行时点重查见 ExportJobRunner）。 */
    public record ExportSubmitReq(@NotBlank(message = "reportCode 不能为空") String reportCode) {
    }

    public record ExportSubmitResp(String jobId, String reportCode) {
    }

    public record ExportStatusReq(@NotBlank(message = "jobId 不能为空") String jobId) {
    }

    /** status：PENDING/RUNNING/DONE/DENIED/FAILED；DONE 才携带 content，DENIED 携带 reason。 */
    public record ExportStatusResp(String jobId, String reportCode, String status,
                                   String content, String reason) {
    }
}
