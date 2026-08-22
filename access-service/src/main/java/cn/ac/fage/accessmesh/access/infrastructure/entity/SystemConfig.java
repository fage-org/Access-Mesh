package cn.ac.fage.accessmesh.access.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import cn.ac.fage.accessmesh.access.infrastructure.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置实体
 * <p>
 * 表示系统级别的配置参数。
 * 以键值对形式存储，支持各类系统参数配置。
 * 用于控制系统的运行行为和默认设置。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("system_config")
public class SystemConfig {

    /**
     * 系统配置唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 配置键，用于标识配置项
     */
    private String configKey;

    /**
     * 配置值
     */
    @Column(typeHandler = JsonbStringTypeHandler.class)
    private String configValue;

    /**
     * 配置描述，说明配置用途和效果
     */
    private String description;

    /**
     * 配置名称（T-ACCESS-002 归并自 admin sys_config）
     */
    private String configName;

    /**
     * 备注（T-ACCESS-002 归并自 admin sys_config）
     */
    private String remark;

    /**
     * 是否系统配置（T-ACCESS-002 归并自 admin sys_config；true=系统内置不可删除）
     */
    private Boolean isSystem;

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