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
 * 服务配置实体
 * <p>
 * 表示微服务的配置信息。
 * 定义服务的编码、名称、基础路径等信息。
 * 用于资源归属服务和API权限校验的服务识别。
 * 支持多租户环境下不同租户的服务配置隔离。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("service_config")
public class ServiceConfig {

    /**
     * 服务配置唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 服务编码，用于标识和引用服务
     */
    private String serviceCode;

    /**
     * 服务名称
     */
    private String name;

    /**
     * 基础路径，API请求的基础URL路径
     */
    private String basePath;

    /**
     * 服务描述，说明服务的用途和功能
     */
    private String description;

    /**
     * 状态（0=禁用，1=启用）
     */
    private Integer status;

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