package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.permission.dto.common.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 抽象用户同步请求
 * <p>
 * 详见 docs/design/permission-center/api-contract.md §6.2.2.3。
 * </p>
 *
 * @param operation         UPSERT / DISABLE / DELETE
 * @param subjectTypeCode   主体类型编码（type_definition.type_key=user_type）
 * @param subjectExternalId 外部稳定 ID
 * @param name              用户名（UPSERT 时必填）
 * @param enabled           启用标志
 * @param extra             扩展属性（Map，落库时由实现端序列化为 JSON 文本）
 * @param sourceService     调用方服务编码
 * @param sourceEntityType  来源实体类型（可选）
 * @param sourceEntityId    来源实体 ID（可选）
 * @param syncVersion       同步事件版本
 */
public record AbstractUserSyncReq(
        @NotBlank String operation,
        @NotBlank String subjectTypeCode,
        @NotBlank String subjectExternalId,
        String name,
        Boolean enabled,
        Map<String, Object> extra,
        @NotBlank String sourceService,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
