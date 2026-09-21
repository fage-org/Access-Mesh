package cn.ac.fage.accessmesh.access.resource.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 依赖声明诊断响应（T-PERM-073，契约 §12.3 declaration-status）。
 *
 * <p>只读诊断面：每服务 manifest 发布状态（service_manifest_sync）+ 声明行
 * （permission_dependency_declaration，含 REJECTED 原因）。依赖声明由所属业务服务
 * 经 manifest 发布，本面只展示不修改。</p>
 *
 * @param manifestSyncs 每服务发布状态行（按服务编码排序）
 * @param declarations  声明行（按来源服务 + 声明键排序；REJECTED 行含 rejectReason）
 */
public record DependencyDeclarationStatusResp(
    List<ManifestSyncItem> manifestSyncs,
    List<DeclarationItem> declarations
) {

    /** 服务依赖发布状态（tenant+service 单行）。 */
    public record ManifestSyncItem(
        String sourceService,
        Long publicationGeneration,
        String revision,
        String syncStatus,
        Boolean isDirty,
        LocalDateTime lastSyncedAt
    ) {}

    /** 声明行：业务键回显（源/目标/操作）+ 编译状态与拒绝原因。 */
    public record DeclarationItem(
        Long id,
        String sourceService,
        String declarationKey,
        String compileStatus,
        String rejectReason,
        String description,
        String sourceResourceTypeCode,
        String sourceResourceCode,
        String sourceCodeType,
        String sourceOperationCode,
        String targetResourceTypeCode,
        String targetResourceCode,
        String targetCodeType,
        List<String> requiredOperationCodes,
        LocalDateTime updatedAt
    ) {}
}
