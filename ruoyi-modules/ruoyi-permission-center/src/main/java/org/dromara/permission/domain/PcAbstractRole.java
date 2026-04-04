package org.dromara.permission.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 抽象角色表 abstract_role
 * 树形；biz_domain_id 为 NULL 表示全局角色
 * 逻辑删除：基类 deleteFlag，0=未删除，删除时=id
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("abstract_role")
public class PcAbstractRole extends PermissionBaseEntity {

    /** 主键 */
    @TableId("id")
    private Long id;

    /** 所属业务域ID，NULL 表示全局角色 */
    private Long bizDomainId;

    /** 角色类型枚举，来自 type_definition */
    private Integer roleType;

    /** 父节点ID，NULL 为根 */
    private Long parentId;

    /** 外部业务标识 */
    private String externalId;

    /** 名称 */
    private String name;

    /** 树路径，如 /1/2/3 */
    private String path;

    /** 同层排序 */
    private Integer sortOrder;

    /** 扩展属性(JSON) */
    private String extra;
}
