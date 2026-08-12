package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 资源依赖更新请求体
 * <p>
 * 用于更新资源依赖关系的信息。
 * ID用于定位记录，操作编码使用稳定的业务键。
 * </p>
 *
 * @param id                    依赖关系ID，必填
 * @param sourceOperationCodes  源操作编码列表，可选
 * @param sourceResourceTypeCode 源资源类型编码，可选
 * @param requiredOperationCodes 要求操作编码列表，可选
 * @param targetResourceTypeCode 目标资源类型编码，可选
 * @param autoGrant             是否自动授权，可选
 * @param description           依赖描述，可选
 */
public record ResourceDependencyUpdateReq(
    @NotNull Long id,
    List<String> sourceOperationCodes,
    String sourceResourceTypeCode,
    List<String> requiredOperationCodes,
    String targetResourceTypeCode,
    Boolean autoGrant,
    String description
) {}