package org.dromara.permission.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 权限资源实体表 resource_entity
 * 树形；resource_type 来自 type_definition
 * 逻辑删除：基类 deleteFlag，0=未删除，删除时=id
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("resource_entity")
public class PcResourceEntity extends PermissionBaseEntity {

    /** 主键 */
    @TableId("id")
    private Long id;

    /** 所属业务域ID，NULL 表示全局资源 */
    private Long bizDomainId;

    /** 父节点ID */
    private Long parentId;

    /** 资源编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 资源类型枚举，来自 type_definition */
    private Integer resourceType;

    /** 树路径 */
    private String path;

    /** 同层排序 */
    private Integer sortOrder;

    /** 扩展属性(JSON) */
    private String extra;
}
