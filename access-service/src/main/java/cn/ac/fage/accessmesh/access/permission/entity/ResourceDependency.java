package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 资源依赖关系实体
 * <p>
 * 表示资源之间的权限依赖关系。
 * 定义某个资源的权限依赖于另一个资源的权限。
 * 支持权限自动授予（autoGrant）和权限位映射（operationBits）。
 * 用于实现权限的级联授予和依赖管理。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("resource_dependency")
public class ResourceDependency {

    /**
     * 资源依赖关系唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 资源实体ID（依赖方）
     */
    private Long resourceEntityId;

    /**
     * 依赖的资源实体ID（被依赖方）
     */
    private Long dependsOnResourceEntityId;

    /**
     * 源操作位值，触发依赖的操作权限位
     */
    private Long sourceOperationBits;

    /**
     * 需要的操作位值，依赖关系需要授予的权限位
     */
    private Long requiredOperationBits;

    /**
     * 是否自动授予（true=自动授予依赖权限，false=不自动）
     */
    private Boolean autoGrant;

    /**
     * 依赖关系描述
     */
    private String description;

    /**
     * 所属服务编码
     */
    private String ownerServiceCode;

    /**
     * 维护来源（MANUAL/SYNC）
     */
    private String maintainSource;

    /**
     * 同步键，用于外部系统同步
     */
    private String syncKey;

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