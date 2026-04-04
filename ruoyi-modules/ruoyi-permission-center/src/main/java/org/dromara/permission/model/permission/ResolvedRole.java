package org.dromara.permission.model.permission;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResolvedRole {
    private Long roleId;
    private Long bizDomainId;
    private Integer roleType;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
}
