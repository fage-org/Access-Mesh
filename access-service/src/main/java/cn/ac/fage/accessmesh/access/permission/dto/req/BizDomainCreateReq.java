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
 * T-PERM-046：global 可选（默认 false/null 均按普通域处理）——true 时每租户仅一个
 * （uk_biz_domain_global 兜底，违例拒绝 20057）；global 创建后不可变（update 不含此
 * 字段，换轨=删除重建，且全局域受 20051 不可删保护）。
 * </p>
 *
 * @param code        业务域编码，必填，唯一标识，大写字母开头+大写字母/数字/下划线，最长 64
 * @param name        业务域名称，必填，用于显示，最长 128
 * @param description 业务域描述，可选，最长 512
 * @param global      是否全局域，可选默认 false；true 时每租户仅一个（20057）
 */
public record BizDomainCreateReq(
    @NotBlank @Size(max = 64)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "业务域编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String code,
    @NotBlank @Size(max = 128) String name,
    @Size(max = 512) String description,
    Boolean global
) {}