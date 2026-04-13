package org.dromara.system.api.event;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 权限版本变更事件
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
public class PermissionVersionChangedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 版本号
     */
    private Long versionNo;

    /**
     * 版本标识
     */
    private String versionToken;

    /**
     * 触发实体类型
     */
    private String triggerEntityType;

    /**
     * 触发实体ID
     */
    private Long triggerEntityId;

    /**
     * 受影响的用户ID列表
     */
    private java.util.List<Long> affectedUserIds;

    /**
     * 受影响的角色ID列表
     */
    private java.util.List<Long> affectedRoleIds;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 变更来源
     */
    private String changeSource;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 时间戳
     */
    private Instant timestamp;

    /**
     * 创建事件
     */
    public static PermissionVersionChangedEvent of(String tenantId, Long versionNo, String versionToken) {
        PermissionVersionChangedEvent event = new PermissionVersionChangedEvent();
        event.setTenantId(tenantId);
        event.setVersionNo(versionNo);
        event.setVersionToken(versionToken);
        event.setTimestamp(Instant.now());
        return event;
    }
}
