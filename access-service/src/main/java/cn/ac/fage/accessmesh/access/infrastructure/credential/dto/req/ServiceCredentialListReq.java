package cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req;

import jakarta.validation.constraints.Size;

/**
 * 服务凭证列表查询请求（T-PERM-070）。
 * <p>serviceCode 可选过滤（空=租户全部凭证）；凭证量低频管理面，全量返回不分页。</p>
 */
public record ServiceCredentialListReq(
    @Size(max = 128, message = "serviceCode 长度不能超过 128")
    String serviceCode) {

    /** 去空白后的过滤值（空白视同不过滤）。 */
    public String normalizedServiceCode() {
        return serviceCode == null || serviceCode.isBlank() ? null : serviceCode.trim();
    }
}
