package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.permission.dto.common.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 抽象角色 full-sync 单条 item。
 *
 * @param roleExternalId        外部稳定 ID
 * @param name                  名称
 * @param parentRoleTypeCode    父角色类型编码（可选）
 * @param parentRoleExternalId  父角色外部 ID（可选）
 * @param status                状态值（可选）
 * @param sortOrder             排序（可选）
 * @param extra                 扩展属性（Map，落库时由实现端序列化为 JSON 文本）
 * @param sourceEntityType      来源实体类型（可选）
 * @param sourceEntityId        来源实体 ID（可选）
 * @param syncVersion           同步事件版本
 */
public record AbstractRoleSyncItem(
        @NotBlank String roleExternalId,
        String name,
        String parentRoleTypeCode,
        String parentRoleExternalId,
        Integer status,
        Integer sortOrder,
        Map<String, Object> extra,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
