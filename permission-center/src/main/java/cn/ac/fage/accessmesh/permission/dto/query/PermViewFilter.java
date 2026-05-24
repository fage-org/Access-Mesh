package cn.ac.fage.accessmesh.permission.dto.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

/**
 * 权限视图过滤条件
 * <p>
 * 包含视图展示层的过滤和分页参数。
 * 引擎不消费此类，仅 {@code AppService} 层使用。
 * 具体过滤/分页逻辑将在 Phase 4 由 {@code PermViewAssembler} 实现。
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermViewFilter {

    /**
     * 按资源类型编码过滤（可选）
     */
    private Set<String> resourceTypes;

    /**
     * 按操作编码过滤（可选）
     */
    private Set<String> operationCodes;

    /**
     * 按资源关键字搜索（可选）
     */
    private String resourceKeyword;

    /**
     * 按业务域编码过滤（可选）
     */
    private String domainCode;

    /**
     * 是否排除 API 类型资源
     */
    private boolean excludeApiResources;

    /**
     * 是否包含全范围权限条目（scopeAll）
     */
    private boolean includeScopePermissions;

    /**
     * 是否包含来源角色信息
     */
    private boolean includeSourceRoles;

    /**
     * 来源角色数量上限
     */
    private int sourceRoleLimit;

    /**
     * 页码，从 1 开始
     */
    private int pageNum;

    /**
     * 每页条数
     */
    private int pageSize;
}