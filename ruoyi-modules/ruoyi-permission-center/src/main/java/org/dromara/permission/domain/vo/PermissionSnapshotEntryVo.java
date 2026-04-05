package org.dromara.permission.domain.vo;

import lombok.Data;

import java.util.Map;

@Data
public class PermissionSnapshotEntryVo {
    private Long roleId;
    private Long resourceId;
    private String resourceCode;
    private Integer resourceType;
    private Long operationId;
    private String operationCode;
    private Long conditionId;
    private Boolean canManage;
    private String serviceCode;
    private String httpMethod;
    private String pathPattern;
    private Map<String, Object> extra;
}
