package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 资源依赖批量同步请求体
 * <p>
 * 用于批量同步资源依赖关系，支持服务级同步和多种同步模式。
 * </p>
 *
 * @param serviceCode    服务编码，必填
 * @param maintainSource 维护来源，必填（schema 四值白名单：ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC）
 * @param syncMode       同步模式，可选（FULL=全量 diff；非 FULL 走增量 upsert）
 * @param items          依赖同步条目列表
 */
public record DependencyBatchSyncReq(
    @NotBlank String serviceCode,
    @NotBlank @Pattern(regexp = "ADMIN_UI|SDK_SCAN|MANIFEST|SERVICE_SYNC",
        message = "maintainSource 仅接受 ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC") String maintainSource,
    String syncMode,
    @Valid List<@NotNull DependencySyncItem> items
) {
    /**
     * 依赖同步条目
     * <p>
     * 表示单个资源依赖关系的同步参数。
     * </p>
     *
     * @param sourceResourceTypeCode 源资源类型编码，必填
     * @param sourceResourceCode     源资源编码，必填
     * @param sourceCodeType         源编码类型，可选
     * @param sourceOperationCodes   源操作编码列表，可选
     * @param targetResourceTypeCode 目标资源类型编码，必填
     * @param targetResourceCode     目标资源编码，必填
     * @param targetCodeType         目标编码类型，可选
     * @param requiredOperationCodes 要求操作编码列表，可选
     * @param autoGrant              预留未实现：自动授权暂缓（T-PERM-035），仅接受 false/省略，传 true 返回 20048
     * @param description            依赖描述，可选（≤512）
     */
    public record DependencySyncItem(
        @NotBlank String sourceResourceTypeCode,
        @NotBlank String sourceResourceCode,
        String sourceCodeType,
        List<String> sourceOperationCodes,
        @NotBlank String targetResourceTypeCode,
        @NotBlank String targetResourceCode,
        String targetCodeType,
        List<String> requiredOperationCodes,
        Boolean autoGrant,
        @Size(max = 512) String description
    ) {}
}
