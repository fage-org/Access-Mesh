package cn.ac.fage.accessmesh.access.resource.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 服务配置保存请求体
 * <p>
 * 用于保存或更新服务配置，包括服务编码、名称、路径等。
 * </p>
 *
 * @param serviceCode 服务编码，必填，唯一标识
 * @param name        服务名称，必填，用于显示
 * @param basePath    服务基础路径，可选，用于API匹配
 * @param description 服务描述，可选
 * @param status      服务状态，可选，0=禁用，1=启用
 * @param extra       扩展属性JSON，可选
 */
public record ServiceConfigReq(
    // T-PERM-070 外评 P3：与凭证签发侧 ServiceCredentialCreateReq 同宽（同一业务键两个
    // 写入口形状约束闭合——注册宽松+签发严格会让特殊字符命名的已注册服务永远无法签发凭证）
    @NotBlank
    @Size(max = 128, message = "serviceCode 长度不能超过 128")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "serviceCode 仅允许字母数字与 . _ - 且以字母数字开头")
    String serviceCode,
    @NotBlank String name,
    String basePath,
    String description,
    Integer status,
    String extra
) {}