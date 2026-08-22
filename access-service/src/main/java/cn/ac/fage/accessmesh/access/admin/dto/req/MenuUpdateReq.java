package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 菜单更新请求记录类（v3.5 菜单零权限化终态，T-ACCESS-015）
 * <p>
 * 可选字段仅更新提供的值（null 跳过保留原值）。
 * sourceService 为创建期追溯标识，不允许更新。
 * </p>
 *
 * @param id           菜单ID（必填）
 * @param menuType     菜单类型（可选，DIR/MENU/EXTERNAL/IFRAME/HIDDEN）
 * @param displayName  菜单显示名（可选）
 * @param parentId     父级菜单ID（可选）
 * @param path         路由路径（可选，租户内唯一）
 * @param icon         图标名称（可选）
 * @param sortOrder    排序权重（可选）
 * @param status       状态（可选，1=ENABLED，0=DISABLED）
 * @param resourceType 关联业务资源类型（可选，与 resourceCode 成对）
 * @param resourceCode 关联业务资源实例（可选，与 resourceType 成对，租户内唯一）
 */
public record MenuUpdateReq(
    /**
     * 菜单ID
     */
    @NotNull(message = "菜单ID不能为空")
    Long id,

    /**
     * 菜单类型（DIR=目录，MENU=菜单，EXTERNAL=外链，IFRAME=嵌入，HIDDEN=隐藏路由）
     */
    @Pattern(regexp = "DIR|MENU|EXTERNAL|IFRAME|HIDDEN", message = "菜单类型必须为 DIR/MENU/EXTERNAL/IFRAME/HIDDEN")
    String menuType,

    /**
     * 菜单显示名
     */
    @Size(max = 128, message = "菜单名称长度不能超过128")
    String displayName,

    /**
     * 父级菜单ID
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
     * 状态（1=ENABLED，0=DISABLED）
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
    String resourceCode
) {
    /**
     * resourceType 与 resourceCode 必须成对填写（同填或同空）
     */
    @AssertTrue(message = "resourceType 与 resourceCode 必须成对填写")
    public boolean isResourceLinkPaired() {
        return (resourceType == null || resourceType.isBlank())
            == (resourceCode == null || resourceCode.isBlank());
    }
}
