package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 归属寻址包含来源身份，避免同码重建后旧事件挂到新主资源。 */
public record ResourceAssociationOwnerReq(
    @NotBlank String resourceTypeCode,
    @NotBlank String code,
    String codeType,
    @NotBlank @Size(max = 256) String sourceEntityId
) {
    public String normalizedCodeType() {
        return new ResourceKeyReq(resourceTypeCode, code, codeType).normalizedCodeType();
    }
}
