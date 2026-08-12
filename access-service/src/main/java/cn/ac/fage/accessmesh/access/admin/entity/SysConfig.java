package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 * <p>
 * 对应数据库表sys_config，用于存储系统运行配置项。
 * 支持租户级配置和系统级配置。
 * </p>
 */
@Getter
@Setter
@Table("sys_config")
public class SysConfig {

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
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否系统配置
     */
    private Boolean isSystem;

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