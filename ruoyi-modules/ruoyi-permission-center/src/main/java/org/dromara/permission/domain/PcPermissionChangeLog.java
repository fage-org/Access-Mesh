package org.dromara.permission.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.permission.handler.PgLongArrayToStringTypeHandler;

import java.time.LocalDateTime;

/**
 * 权限变更记录表 permission_change_log
 * 无 updated_at/updated_by/deleted_at，仅插入不更新
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@TableName(value = "permission_change_log", autoResultMap = true)
public class PcPermissionChangeLog implements java.io.Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId("id")
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 业务域ID，NULL 表示与域无关 */
    private Long bizDomainId;

    /** 变更实体类型：user_role/batch_user_role/role_resource_permission 等 */
    private String entityType;

    /** 被变更记录的主键ID，批量时可0或批次ID */
    private Long entityId;

    /** 操作：INSERT/UPDATE/DELETE */
    private String operation;

    /** 变更前快照(JSON) */
    private String oldSnapshot;

    /** 变更后快照(JSON) */
    private String newSnapshot;

    /** 本条变更影响的用户ID数组，PostgreSQL BIGINT[] 存为逗号分隔或 JSON */
    @TableField(typeHandler = PgLongArrayToStringTypeHandler.class)
    private String affectedAbstractUserIds;

    /** 本条变更影响的角色ID数组 */
    @TableField(typeHandler = PgLongArrayToStringTypeHandler.class)
    private String affectedAbstractRoleIds;

    /** 变更原因说明 */
    private String changeReason;

    /** 变更来源：ADMIN/MQ_SYNC/API/SYSTEM */
    private String changeSource;

    /** 请求/追踪ID */
    private String requestId;

    /** 执行变更的操作人ID */
    private Long createdBy;

    /** 变更时间 */
    private LocalDateTime createdAt;
}
