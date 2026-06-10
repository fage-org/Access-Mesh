package cn.ac.fage.accessmesh.admin.security;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin模块权限验证器实现类
 * <p>
 * 通过Feign客户端调用权限中心进行权限校验。
 * 实例级校验依赖 permission-center 中已存在的 resource_entity：
 * ADMIN_USER 使用 sys_user.id 字符串作为 resourceCode，
 * ADMIN_ORG 使用 sys_org.id 字符串作为 resourceCode。
 * 如果同步端使用组织编码、用户名或 abstract_user/abstract_role ID，
 * 这里的实例级权限会无法稳定解析。
 * </p>
 */
@Service
public class AdminPermissionValidatorImpl implements AdminPermissionValidator {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionValidatorImpl.class);

    /**
     * Admin模块用户的主体类型码
     */
    private static final String SUBJECT_TYPE_CODE = "ADMIN_USER";

    private final PermissionFeignClient permissionFeignClient;

    /**
     * 构造函数
     *
     * @param permissionFeignClient 权限中心Feign客户端
     */
    public AdminPermissionValidatorImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

    /**
     * 类型级权限校验（用于CREATE操作）
     * <p>
     * 通过权限中心校验用户是否有权限在资源类型上执行操作。
     * </p>
     *
     * @param resourceTypeCode 资源类型码
     * @param operationCode    操作码
     */
    @Override
    public void checkTypeLevel(String resourceTypeCode, String operationCode) {
        String subjectExternalId = String.valueOf(StpUtil.getLoginIdAsLong());

        AuthCheckReq req = new AuthCheckReq(
            SUBJECT_TYPE_CODE,
            subjectExternalId,
            resourceTypeCode,
            null,  // 类型级权限（CREATE）时为null
            operationCode,
            null,  // domainCode
            null,  // codeType
            null,  // inheritMode
            null   // context
        );

        checkAndThrow(req, resourceTypeCode, "*", operationCode);
    }

    /**
     * 实例级权限校验（用于UPDATE/DELETE操作）
     * <p>
     * 通过权限中心校验用户是否有权限在具体资源实例上执行操作。
     * </p>
     *
     * @param resourceTypeCode 资源类型码
     * @param resourceCode     资源实例码
     * @param operationCode    操作码
     */
    @Override
    public void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode) {
        String subjectExternalId = String.valueOf(StpUtil.getLoginIdAsLong());

        AuthCheckReq req = new AuthCheckReq(
            SUBJECT_TYPE_CODE,
            subjectExternalId,
            resourceTypeCode,
            resourceCode,
            operationCode,
            null,
            null,
            null,
            null
        );

        checkAndThrow(req, resourceTypeCode, resourceCode, operationCode);
    }

    /**
     * 批量实例级权限校验
     * <p>
     * 通过权限中心批量校验多个资源实例的权限。
     * 任一资源权限拒绝时抛出SecurityException。
     * </p>
     *
     * @param resourceTypeCode 资源类型码
     * @param resourceCodes    资源实例码列表
     * @param operationCode    操作码
     */
    @Override
    public void checkBatchInstanceLevel(String resourceTypeCode, List<String> resourceCodes, String operationCode) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            return;
        }

        String subjectExternalId = String.valueOf(StpUtil.getLoginIdAsLong());

        List<BatchAuthCheckReq.AuthCheckItem> items = resourceCodes.stream()
            .map(code -> new BatchAuthCheckReq.AuthCheckItem(
                resourceTypeCode,
                code,
                operationCode,
                null,
                null,
                null
            ))
            .collect(Collectors.toList());

        BatchAuthCheckReq req = new BatchAuthCheckReq(
            SUBJECT_TYPE_CODE,
            subjectExternalId,
            items,
            null
        );

        PermResult<BatchAuthCheckResp> result = permissionFeignClient.batchCheckAuth(req);

        if (!isSuccess(result)) {
            log.warn("批量权限校验失败: subject={}, resourceType={}, operation={}",
                subjectExternalId, resourceTypeCode, operationCode);
            throw new SecurityException("权限校验失败: " + result.getMessage());
        }

        BatchAuthCheckResp resp = result.getData();
        if (resp == null || resp.items() == null) {
            throw new SecurityException("权限校验返回空响应");
        }

        // 检查所有项目的拒绝权限
        List<BatchAuthCheckResp.AuthCheckItemResult> deniedItems = resp.items().stream()
            .filter(item -> !item.allowed())
            .collect(Collectors.toList());

        if (!deniedItems.isEmpty()) {
            String deniedCodes = deniedItems.stream()
                .map(BatchAuthCheckResp.AuthCheckItemResult::resourceCode)
                .collect(Collectors.joining(", "));
            log.warn("批量操作权限被拒绝: resourceType={}, resourceCodes={}, operation={}, reason={}",
                resourceTypeCode, deniedCodes, operationCode,
                deniedItems.get(0).reason());
            throw new SecurityException(
                String.format("权限被拒绝: %s:%s 的 %s 操作", operationCode, deniedCodes, resourceTypeCode));
        }
    }

    /**
     * 执行权限校验并在拒绝时抛出异常
     *
     * @param req               权限校验请求
     * @param resourceTypeCode  资源类型码
     * @param resourceCode      资源实例码
     * @param operationCode     操作码
     */
    private void checkAndThrow(AuthCheckReq req, String resourceTypeCode, String resourceCode, String operationCode) {
        PermResult<AuthCheckResp> result = permissionFeignClient.checkAuth(req);

        if (!isSuccess(result)) {
            log.warn("权限校验请求失败: subject={}, resourceType={}, resourceCode={}, operation={}",
                req.subjectExternalId(), resourceTypeCode, resourceCode, operationCode);
            throw new SecurityException("权限校验失败: " + result.getMessage());
        }

        AuthCheckResp resp = result.getData();
        if (resp == null || !resp.allowed()) {
            String reason = resp != null ? resp.reason() : "NO_PERMISSION";
            log.warn("权限被拒绝: subject={}, resourceType={}, resourceCode={}, operation={}, reason={}",
                req.subjectExternalId(), resourceTypeCode, resourceCode, operationCode, reason);
            throw new SecurityException(
                String.format("权限被拒绝: 无法在 %s:%s 上执行 %s 操作。原因: %s",
                    operationCode, resourceTypeCode, resourceCode, reason));
        }
    }

    /**
     * 检查响应是否成功
     *
     * @param result 权限结果
     * @return 成功返回true
     */
    private boolean isSuccess(PermResult<?> result) {
        return result != null && result.getCode() == 200;
    }
}
