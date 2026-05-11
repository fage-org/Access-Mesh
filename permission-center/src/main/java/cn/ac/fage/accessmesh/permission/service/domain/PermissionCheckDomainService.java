package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;

import java.util.Map;

/**
 * 权限校验领域服务接口
 * <p>
 * 提供领域层权限校验逻辑，从PermissionServiceImpl中提取，
 * 避免调度层服务之间的交叉调用。
 * 提供单次校验、批量校验、接口校验和内部校验接口。
 * 内部校验接口供PermissionService和PermissionViewService共用。
 * </p>
 */
public interface PermissionCheckDomainService {

    /**
     * 单次权限校验
     * <p>
     * 检查用户对指定资源是否有指定操作的权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限校验请求，包含用户标识、资源编码、操作码等
     * @return 权限校验响应，包含是否允许、拒绝原因等信息
     */
    AuthCheckResp check(Long tenantId, AuthCheckReq req);

    /**
     * 批量权限校验
     * <p>
     * 批量检查用户对多个资源的权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量权限校验请求，包含用户标识和多个校验项
     * @return 批量权限校验响应，包含每个项的校验结果
     */
    BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req);

    /**
     * 接口权限校验
     * <p>
     * 校验用户是否有访问特定API接口的权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口校验请求
     * @return 接口校验响应
     */
    CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req);

    /**
     * 内部权限校验
     * <p>
     * 使用内部ID（而非外部编码）进行权限校验。
     * 供PermissionService和PermissionViewService共用，
     * 避免重复实现校验逻辑。
     * </p>
     *
     * @param tenantId              租户ID
     * @param userId                用户ID
     * @param resourceEntityId      资源实体ID
     * @param operationPermissionId 操作权限ID
     * @param inheritMode           继承模式
     * @param context               上下文参数
     * @return 权限校验响应
     */
    AuthCheckResp checkInternal(Long tenantId, Long userId, Long resourceEntityId,
                                Long operationPermissionId,
                                String inheritMode, Map<String, Object> context);
}