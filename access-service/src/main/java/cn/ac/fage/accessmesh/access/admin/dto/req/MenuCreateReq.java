package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 菜单创建请求记录类（v3.5 菜单零权限化终态，T-ACCESS-015）
 * <p>
 * 菜单仅承载 UI 路由元数据与关联资源 link，不含权限字段
 * （perm_code/component/visible 已随权威 DDL 移除）。
 * 唯一性约束：path（uk_sys_menu_tenant_path）、
 * resource_type+resource_code（uk_sys_menu_tenant_resource）。
 * </p>
 *
 * @param menuType     菜单类型（必填，DIR/MENU/EXTERNAL/IFRAME/HIDDEN）
 * @param displayName  菜单显示名（必填，最长 128）
 * @param parentId     父级菜单ID（可选，null/0 表示顶级菜单）
 * @param path         路由路径（EXTERNAL/IFRAME 为外链 URL；租户内唯一）
 * @param icon         图标名称（可选，最长 64）
 * @param sortOrder    排序权重（可选，默认 0，升序）
 * @param status       状态（可选，1=ENABLED 默认，0=DISABLED）
 * @param resourceType 关联业务资源类型（可选，与 resourceCode 成对；v3.5 §4.1 派生公式用）
 * @param resourceCode 关联业务资源实例（可选，与 resourceType 成对；租户内唯一）
 * @param sourceService 业务服务标识（可选，缺省 access-service 表示管理端创建）
 */
public record MenuCreateReq(
    /**
     * 菜单类型（DIR=目录，MENU=菜单，EXTERNAL=外链，IFRAME=嵌入，HIDDEN=隐藏路由）
     */
    @NotBlank(message = "菜单类型不能为空")
    @Pattern(regexp = "DIR|MENU|EXTERNAL|IFRAME|HIDDEN", message = "菜单类型必须为 DIR/MENU/EXTERNAL/IFRAME/HIDDEN")
    String menuType,

    /**
     * 菜单显示名
     */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 128, message = "菜单名称长度不能超过128")
    String displayName,

    /**
     * 父级菜单ID（null/0 表示顶级菜单）
     */
    Long parentId,

    /**
     * 路由路径（EXTERNAL/IFRAME 为外链 URL）
     */
    @Size(max = 256, message = "路由路径长度不能超过256")
    String path,

    /**
     * 图标名称
     */
    @Size(max = 64, message = "图标名称长度不能超过64")
    String icon,

    /**
     * 排序权重（升序）
     */
    Integer sortOrder,

    /**
     * 状态（1=ENABLED，0=DISABLED，默认 ENABLED）
     */
    @Min(value = 0, message = "状态只允许 0=DISABLED 或 1=ENABLED")
    @Max(value = 1, message = "状态只允许 0=DISABLED 或 1=ENABLED")
    Integer status,

    /**
     * 关联业务资源类型（与 resourceCode 成对填写）
     */
    @Size(max = 64, message = "资源类型长度不能超过64")
    String resourceType,

    /**
     * 关联业务资源实例（与 resourceType 成对填写）
     */
    @Size(max = 64, message = "资源编码长度不能超过64")
    String resourceCode,

    /**
     * 业务服务标识（缺省 access-service）
     */
    @Size(max = 64, message = "业务服务标识长度不能超过64")
    String sourceService
) {
    /**
     * resourceType 与 resourceCode 必须成对填写（同填或同空）：
     * DDL 无成对约束，读链路对 resource_code 缺失的业务菜单 fail-closed 不可见
     */
    @AssertTrue(message = "resourceType 与 resourceCode 必须成对填写")
    public boolean isResourceLinkPaired() {
        return (resourceType == null || resourceType.isBlank())
            == (resourceCode == null || resourceCode.isBlank());
    }
}
