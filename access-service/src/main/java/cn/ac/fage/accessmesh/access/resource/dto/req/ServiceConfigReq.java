package cn.ac.fage.accessmesh.access.resource.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 服务配置保存请求体
 * <p>
 * 用于保存或更新服务配置，包括服务编码、名称、路径等。
 * </p>
 *
 * @param serviceCode      服务编码，必填，唯一标识
 * @param name             服务名称，必填，用于显示
 * @param basePath         服务基础路径，可选，用于API匹配；空白拒绝，清空须传 basePathClear（T-API-004）
 * @param description      服务描述，可选；空白拒绝，清空须传 descriptionClear（T-API-004）
 * @param status           服务状态，可选，0=禁用，1=启用
 * @param extra            扩展属性JSON，可选；空白拒绝，清空须传 extraClear（T-API-004；
 *                         清空语义=撤销 extra.syncTypes 同步白名单，该服务同步通道全拒，fail-closed）
 * @param basePathClear    基础路径显式清空标志（T-API-004，仅更新分支有意义；创建分支携带拒绝）
 * @param descriptionClear 服务描述显式清空标志（同上）
 * @param extraClear       扩展属性显式清空标志（同上）
 */
public record ServiceConfigReq(
    // T-PERM-070 外评 P3：与凭证签发侧 ServiceCredentialCreateReq 同宽（同一业务键两个
    // 写入口形状约束闭合——注册宽松+签发严格会让特殊字符命名的已注册服务永远无法签发凭证）
    @NotBlank
    @Size(max = 128, message = "serviceCode 长度不能超过 128")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "serviceCode 仅允许字母数字与 . _ - 且以字母数字开头")
    String serviceCode,
    @NotBlank String name,
    @Pattern(regexp = "(?s).*\\S.*", message = "basePath 不能为空白；清空请传 basePathClear=true")
    String basePath,
    @Pattern(regexp = "(?s).*\\S.*", message = "description 不能为空白；清空请传 descriptionClear=true")
    String description,
    Integer status,
    @Pattern(regexp = "(?s).*\\S.*", message = "extra 不能为空白；清空请传 extraClear=true")
    String extra,
    Boolean basePathClear,
    Boolean descriptionClear,
    Boolean extraClear
) {
    /**
     * T-API-004（U006 拍板：全端点冲突拒绝）：新值与 Clear 同传拒绝。
     */
    @AssertTrue(message = "basePath 与 basePathClear 不能同时提供（清空请只传 basePathClear=true）")
    public boolean isBasePathConflictFree() {
        return basePath == null || !Boolean.TRUE.equals(basePathClear);
    }

    @AssertTrue(message = "description 与 descriptionClear 不能同时提供（清空请只传 descriptionClear=true）")
    public boolean isDescriptionConflictFree() {
        return description == null || !Boolean.TRUE.equals(descriptionClear);
    }

    @AssertTrue(message = "extra 与 extraClear 不能同时提供（清空请只传 extraClear=true）")
    public boolean isExtraConflictFree() {
        return extra == null || !Boolean.TRUE.equals(extraClear);
    }
}
