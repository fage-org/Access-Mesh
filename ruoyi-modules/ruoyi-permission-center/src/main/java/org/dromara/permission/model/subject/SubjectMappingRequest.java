package org.dromara.permission.model.subject;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 主体映射请求
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
public class SubjectMappingRequest {

    /**
     * 租户ID
     */
    @NotBlank(message = "租户ID不能为空")
    private String tenantId;

    /**
     * 用户类型编码（如 "sys_user", "service"）
     */
    @NotBlank(message = "用户类型不能为空")
    private String userTypeCode;

    /**
     * 外部业务系统唯一标识
     */
    @NotBlank(message = "外部ID不能为空")
    private String externalId;

    /**
     * 显示名（创建时使用）
     */
    private String name;
}
