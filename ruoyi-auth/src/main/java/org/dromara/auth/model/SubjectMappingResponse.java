package org.dromara.auth.model;

import lombok.Data;

/**
 * 主体映射响应
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
public class SubjectMappingResponse {

    /**
     * 抽象用户ID
     */
    private Long abstractUserId;

    /**
     * 权限版本号
     */
    private String permissionVersion;

    /**
     * 版本更新时间戳
     */
    private Long versionUpdatedAt;
}
