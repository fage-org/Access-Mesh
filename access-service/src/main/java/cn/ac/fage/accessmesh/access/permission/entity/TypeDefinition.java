package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import cn.ac.fage.accessmesh.access.infrastructure.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 类型定义实体
 * <p>
 * 表示系统中各种类型的定义配置。
 * 支持资源类型、操作类型、角色类型等多种枚举定义。
 * 通过typeKey区分不同类型体系，typeCode和typeValue定义具体类型项。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("type_definition")
public class TypeDefinition {

    /**
     * 类型定义唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 类型键，标识类型所属的分类体系
     */
    private String typeKey;

    /**
     * 类型编码，用于标识具体类型
     */
    private String typeCode;

    /**
     * 类型值，用于存储类型的数值表示
     */
    private Integer typeValue;

    /**
     * 类型名称
     */
    private String name;

    /**
     * 类型描述，说明类型的含义和用途
     */
    private String description;

    /**
     * 是否系统类型（true=系统预置，不可删除；false=用户自定义）
     */
    private Boolean isSystem;

    /**
     * 排序顺序，用于类型列表展示排序
     */
    private Integer sortOrder;

    /**
     * 扩展信息（JSON格式），存储额外属性
     */
    @Column(typeHandler = JsonbStringTypeHandler.class)
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