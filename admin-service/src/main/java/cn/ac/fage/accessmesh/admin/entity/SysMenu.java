package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统菜单实体类
 * <p>
 * 对应数据库表sys_menu，用于存储系统菜单和权限配置。
 * 支持菜单树结构、路由配置、权限码绑定等。
 * </p>
 */
@Getter
@Setter
@Table("sys_menu")
public class SysMenu {

    /**
     * 主键ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 菜单类型（M=目录，C=菜单，F=按钮）
     */
    private String menuType;

    /**
     * 服务编码
     */
    private String serviceCode;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 权限编码
     */
    private String permCode;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 是否可见
     */
    private Boolean visible;

    /**
     * 是否外链
     */
    private Boolean isExternal;

    /**
     * 是否内嵌框架
     */
    private Boolean isFrame;

    /**
     * 是否缓存
     */
    private Boolean isCache;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 扩展配置（JSON格式）
     */
    private String extra;

    /**
     * 权限资源ID
     */
    private Long permResourceId;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 删除人ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，1=已删除）
     */
    private Long deleteFlag;
}