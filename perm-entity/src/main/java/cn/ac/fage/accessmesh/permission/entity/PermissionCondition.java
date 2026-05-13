package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 权限条件实体
 * <p>
 * 表示动态权限判断的条件规则配置。
 * 条件规则以JSON格式存储，支持复杂的条件表达式。
 * 可用于实现基于时间、组织、数据属性等的动态权限控制。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("permission_condition")
public class PermissionCondition {

    /**
     * 权限条件唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 条件编码，用于标识和引用
     */
    private String code;

    /**
     * 条件名称
     */
    private String name;

    /**
     * 条件规则（JSON格式），定义具体的条件逻辑
     */
    private String conditionRules;

    /**
     * 启用状态（true=启用，false=禁用）
     */
    private Boolean enabled;

    /**
     * 条件描述，说明条件用途和效果
     */
    private String description;

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