package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 资源实体
 * <p>
 * 表示系统中需要权限控制的资源对象。
 * 资源类型包括：菜单、按钮、API、数据等。
 * 支持资源层级结构（通过parentId）和资源路径定位。
 * 资源可由外部服务同步维护（通过maintainSource和syncKey）。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("resource_entity")
public class ResourceEntity {

    /**
     * 源唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 父资源ID，用于资源层级结构
     */
    private Long parentId;

    /**
     * 资源类型（0=菜单，1=按钮，2=API，3=数据）
     */
    private Integer resourceType;

    /**
     * 资源编码，用于权限标识
     */
    private String code;

    /**
     * 编码类型（用于区分不同编码体系）
     */
    private String codeType;

    /**
     * 资源名称
     */
    private String name;

    /**
     * 资源路径，用于定位资源位置
     */
    private String path;

    /**
     * 状态（0=禁用，1=启用）
     */
    private Integer status;

    /**
     * 排序顺序，用于资源列表展示排序
     */
    private Integer sortOrder;

    /**
     * 所属服务编码，标识资源所属的服务
     */
    private String ownerServiceCode;

    /**
     * 维护来源，标识资源的维护方式（MANUAL/SYNC）
     */
    private String maintainSource;

    /**
     * 同步键，用于外部系统同步时的唯一标识
     */
    private String syncKey;

    /**
     * 扩展信息（JSON格式），存储额外属性
     */
    private String extra;

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