package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 业务域创建请求体
 * <p>
 * 用于创建新的业务域，包括编码、名称和描述。
 * 编码租户内唯一（uk_biz_domain，软删行不占用），重复拒绝 20052；
 * 长度与格式校验对齐 schema 列宽与前端表单校验（T-PERM-026）。
 * </p>
 *
 * @param code        业务域编码，必填，唯一标识，大写字母开头+大写字母/数字/下划线，最长 64
 * @param name        业务域名称，必填，用于显示，最长 128
 * @param description 业务域描述，可选，最长 512
 */
public record BizDomainCreateReq(
    @NotBlank @Size(max = 64)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "业务域编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String code,
    @NotBlank @Size(max = 128) String name,
    @Size(max = 512) String description
) {}