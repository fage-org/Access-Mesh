package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色资源权限实体
 * <p>
 * 表示角色对资源的操作权限配置。
 * 定义了角色可以执行哪些操作（operationPermissionId）、
 * 操作的资源范围（resourceEntityId）、权限条件（conditionId）等。
 * 支持权限继承（dependOn）和授权传递（canGrant）。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("role_resource_permission")
public class RoleResourcePermission {

    /**
     * 角色资源权限唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 抽象角色ID
     */
    private Long abstractRoleId;

    /**
     * 资源实体ID
     */
    private Long resourceEntityId;

    /**
     * 操作权限ID
     */
    private Long operationPermissionId;

    /**
     * 资源类型
     */
    private Integer resourceType;

    /**
     * 依赖的权限ID，用于权限继承关系
     */
    private Long dependOn;

    /**
     * 是否全部范围（true=全部范围，false=限定范围）
     */
    private Boolean scopeAll;

    /**
     * 是否可授权（true=可授权给他人，false=不可）
     */
    private Boolean canGrant;

    /**
     * 权限条件ID，用于动态权限判断
     */
    private Long conditionId;

    /**
     * 授权来源，标识权限授予方式
     */
    private String grantSource;

    /**
     * 授权依赖ID
     */
    private Long grantDepId;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 最后更新者用户ID
     */
    private Long updatedBy;

    /**
     * 删除者用户ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，其他=已删除）
     */
    private Long deleteFlag;
}