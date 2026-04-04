package org.dromara.permission.domain.dto;

import lombok.Data;

/**
 * 操作权限保存请求（单条）
 */
@Data
public class OperationSaveReq {
    private Long id;
    private Long tenantId;
    private Integer resourceType;
    private String code;
    private String name;
    private Long binaryBit;
    private Long inheritMask;
}
