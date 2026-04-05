package org.dromara.permission.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.permission.model.permission.InheritMode;

import java.util.Map;

@Data
public class PermissionCheckReq {
    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    @JsonAlias("abstractUserId")
    @NotNull(message = "userId不能为空")
    private Long userId;

    @NotNull(message = "resourceEntityId不能为空")
    private Long resourceEntityId;

    @NotNull(message = "operationPermissionId不能为空")
    private Long operationPermissionId;

    private Long bizDomainId;
    private InheritMode inheritMode = InheritMode.NONE;
    private Boolean checkDependency = Boolean.FALSE;
    private Map<String, Object> context;

    @JsonIgnore
    public Long getAbstractUserId() {
        return userId;
    }

    @JsonIgnore
    public void setAbstractUserId(Long abstractUserId) {
        this.userId = abstractUserId;
    }
}
