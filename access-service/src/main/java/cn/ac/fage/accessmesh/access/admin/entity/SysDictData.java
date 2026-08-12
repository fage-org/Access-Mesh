package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统字典数据实体类
 * <p>
 * 对应数据库表sys_dict_data，用于存储字典的具体数据项。
 * 每条数据项归属于某个字典类型，包含标签、值、排序等信息。
 * </p>
 */
@Getter
@Setter
@Table("sys_dict_data")
public class SysDictData {

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
     * 字典类型
     */
    private String dictType;

    /**
     * 字典标签
     */
    private String dictLabel;

    /**
     * 字典值
     */
    private String dictValue;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * CSS样式类
     */
    private String cssClass;

    /**
     * 列表样式类
     */
    private String listClass;

    /**
     * 是否默认值
     */
    private Boolean isDefault;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

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