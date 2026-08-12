package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 资源API映射实体
 * <p>
 * 表示资源与HTTP API接口的映射关系。
 * 定义哪个API接口对应哪个权限资源。
 * 支持HTTP方法、路径模式匹配和匹配顺序。
 * 用于API级别的权限校验，确保API访问受权限控制。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("resource_api_mapping")
public class ResourceApiMapping {

    /**
     * 资源API映射唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 资源实体ID
     */
    private Long resourceEntityId;

    /**
     * 服务编码，标识API所属的服务
     */
    private String serviceCode;

    /**
     * HTTP方法（GET/POST/PUT/DELETE等）
     */
    private String httpMethod;

    /**
     * 路径模式，支持Ant风格路径匹配
     */
    private String pathPattern;

    /**
     * 匹配顺序，用于多个匹配规则时的优先级判断
     */
    private Integer matchOrder;

    /**
     * 启用状态（true=启用，false=禁用）
     */
    private Boolean enabled;

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