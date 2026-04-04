package org.dromara.permission.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 抽象用户表 abstract_user
 * user_type 来自 type_definition
 * 逻辑删除：基类 deleteFlag，0=未删除，删除时=id
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("abstract_user")
public class PcAbstractUser extends PermissionBaseEntity {

    /** 主键 */
    @TableId("id")
    private Long id;

    /** 用户类型枚举值，来自 type_definition.type_key=user_type */
    private Integer userType;

    /** 外部业务系统唯一标识 */
    private String externalId;

    /** 显示名 */
    private String name;

    /** 扩展属性(JSON) */
    private String extra;
}
