package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.vo.RolePermEntry;

import java.util.List;
import java.util.Map;

/**
 * 权限条件领域服务接口
 * <p>
 * 提供权限条件的评估功能。权限条件定义了权限生效的附加约束规则，
 * 如时间范围、数据属性等。条件规则存储为JSON格式，支持复杂的条件表达式。
 * 评估时根据上下文参数判断每个权限条目是否满足条件约束。
 * </p>
 */
public interface PermissionConditionDomainService {

    /**
     * 评估权限条件
     * <p>
     * 对权限条目列表进行条件评估，过滤不符合条件的条目。
     * 根据上下文参数（如当前时间、用户属性等）判断条件是否满足。
     * 返回通过条件检查的权限条目列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param entries  待评估的权限条目列表
     * @param context  评估上下文，包含条件判断所需的参数
     * @return 通过条件检查的权限条目列表
     */
    List<RolePermEntry> evaluate(Long tenantId, List<RolePermEntry> entries,
                                                   Map<String, Object> context);
}