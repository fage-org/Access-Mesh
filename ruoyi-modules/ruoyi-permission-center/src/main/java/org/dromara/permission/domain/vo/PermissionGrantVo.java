package org.dromara.permission.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PermissionGrantVo {
    private boolean success;
    private Long permissionId;
    private List<String> rejectReasons;
}
