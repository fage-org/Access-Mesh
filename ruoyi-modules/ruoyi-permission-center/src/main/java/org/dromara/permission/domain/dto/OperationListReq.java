package org.dromara.permission.domain.dto;

import lombok.Data;

/**
 * 操作权限列表查询请求
 */
@Data
public class OperationListReq {
    private Long tenantId;
    private Integer resourceType;
}
