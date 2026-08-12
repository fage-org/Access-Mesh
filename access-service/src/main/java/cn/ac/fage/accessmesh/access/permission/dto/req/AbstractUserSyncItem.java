package cn.ac.fage.accessmesh.access.permission.dto.req;

import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 抽象用户 full-sync 单条 item。
 *
 * @param subjectExternalId 外部稳定 ID
 * @param name              用户名
 * @param enabled           启用标志
 * @param extra             扩展属性（Map，落库时由实现端序列化为 JSON 文本）
 * @param sourceEntityType  来源实体类型（可选）
 * @param sourceEntityId    来源实体 ID（可选）
 * @param syncVersion       同步事件版本
 */
public record AbstractUserSyncItem(
        @NotBlank String subjectExternalId,
        String name,
        Boolean enabled,
        Map<String, Object> extra,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
