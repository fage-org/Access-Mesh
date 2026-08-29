package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 业务域更新请求体（T-PERM-026 切业务键 code 定位，原内部主键 domainId 退役）
 * <p>
 * 用于更新业务域的名称和描述。code 为租户内唯一键（uk_biz_domain），
 * 不可通过 update 修改（改 code 等于新建新域）；name/description 为 null 表示不更新，
 * description 传空串表示显式清空。
 * </p>
 *
 * @param domainCode  业务域编码，必填，定位键，最长 64
 * @param name        业务域名称，可选，最长 128
 * @param description 业务域描述，可选，最长 512（null=不更新，空串=清空）
 */
public record BizDomainUpdateReq(
    @NotBlank @Size(max = 64) String domainCode,
    @Size(max = 128) String name,
    @Size(max = 512) String description
) {}
