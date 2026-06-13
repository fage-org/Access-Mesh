package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.permission.dto.common.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 抽象角色同步请求
 * <p>
 * 详见 docs/design/permission-center/api-contract.md §6.2.2.3。
 * </p>
 *
 * @param operation             UPSERT / DISABLE / DELETE
 * @param roleTypeCode          角色类型编码
 * @param roleExternalId        外部稳定 ID
 * @param name                  名称
 * @param parentRoleTypeCode    父角色类型编码（可选）
 * @param parentRoleExternalId  父角色外部 ID（可选）
 * @param treeRootExternalId    树根外部 ID（用于 scopeKey）
 * @param status                状态值（可选）
 * @param sortOrder             排序（可选）
 * @param extra                 扩展属性（Map，落库时由实现端序列化为 JSON 文本）
 * @param sourceService         调用方服务编码
 * @param sourceEntityType      来源实体类型（可选）
 * @param sourceEntityId        来源实体 ID（可选）
 * @param syncVersion           同步事件版本
 */
public record AbstractRoleSyncReq(
        @NotBlank String operation,
        @NotBlank String roleTypeCode,
        @NotBlank String roleExternalId,
        String name,
        String parentRoleTypeCode,
        String parentRoleExternalId,
        @NotBlank String treeRootExternalId,
        Integer status,
        Integer sortOrder,
        Map<String, Object> extra,
        @NotBlank String sourceService,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
