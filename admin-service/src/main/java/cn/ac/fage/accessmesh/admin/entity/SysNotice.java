package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统通知公告实体类
 * <p>
 * 对应数据库表sys_notice，用于存储系统通知公告。
 * 支持不同类型的通知、目标用户配置、发布状态等。
 * </p>
 */
@Getter
@Setter
@Table("sys_notice")
public class SysNotice {

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
     * 通知类型
     */
    private String noticeType;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 目标类型（ALL=全员，USER=指定用户，ORG=指定组织）
     */
    private String targetType;

    /**
     * 目标ID列表（逗号分隔）
     */
    private String targetIds;

    /**
     * 状态（0=草稿，1=已发布）
     */
    private Integer status;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

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