package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 抽象角色实体
 * <p>
 * 表示系统中的角色主体，支持多种角色类型和层级结构。
 * 角色类型包括：全局角色、业务域角色、组织角色、岗位角色等。
 * 支持角色继承（通过parentId）和角色排序。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("abstract_role")
public class AbstractRole {

    /**
     * 角色唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 父角色ID，用于角色继承层级
     */
    private Long parentId;

    /**
     * 角色类型（0=全局角色，1=业务域角色，2=组织角色，3=岗位角色）
     */
    private Integer roleType;

    /**
     * 外部标识，用于关联外部系统角色
     */
    private String externalId;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 状态（0=禁用，1=启用）
     */
    private Integer status;

    /**
     * 排序顺序，用于角色列表展示排序
     */
    private Integer sortOrder;

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