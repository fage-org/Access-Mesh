package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户同步请求
 * <p>
 * 用于从admin-service向permission-center的abstract_user表同步用户数据。
 * </p>
 */
public record UserSyncReq(
    /**
     * 主体类型码
     */
    @NotBlank String subjectTypeCode,
    /**
     * 外部ID
     */
    @NotBlank String externalId,
    /**
     * 用户名称
     */
    String name,
    /**
     * 是否启用
     */
    Boolean enabled,
    /**
     * 扩展信息
     */
    String extra,
    /**
     * 版本号，用于数据同步一致性校验
     */
    @NotBlank String version
) {}