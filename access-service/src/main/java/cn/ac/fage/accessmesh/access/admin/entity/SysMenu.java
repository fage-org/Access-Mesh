package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统菜单实体类
 * <p>
 * 对应数据库表 sys_menu（v3.5 菜单零权限化终态，权威 DDL 见
 * docs/design/schema/access-service.sql）。仅承载 UI 路由元数据与
 * 关联资源 link，不承载权限语义；按钮级权限由 OperationPermission 承担。
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
     * 父菜单ID（顶级为 0）
     */
    private Long parentId;

    /**
     * 菜单显示名
     */
    private String displayName;

    /**
     * 路由路径（EXTERNAL/IFRAME 为外链 URL；uk_sys_menu_tenant_path 唯一）
     */
    private String path;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 排序权重（升序）
     */
    private Integer sortOrder;

    /**
     * 菜单类型：DIR/MENU/EXTERNAL/IFRAME/HIDDEN
     */
    private String menuType;

    /**
     * 状态：0=DISABLED，1=ENABLED
     */
    private Integer status;

    /**
     * 关联业务资源类型（不参与鉴权决策，仅 link，v3.5 §4.1 派生公式用）
     */
    private String resourceType;

    /**
     * 关联业务资源实例（不参与鉴权决策，仅 link）
     */
    private String resourceCode;

    /**
     * 业务服务标识（链路追溯，管理端创建缺省 access-service）
     */
    private String sourceService;

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
     * 删除标记（0=未删除，删除时填本行id）
     */
    private Long deleteFlag;
}
