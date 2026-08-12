package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 抽象用户 full-sync scope。
 *
 * @param sourceService   调用方服务编码
 * @param subjectTypeCode 主体类型编码
 */
public record AbstractUserSyncScope(
        @NotBlank String sourceService,
        @NotBlank String subjectTypeCode
) {
}
