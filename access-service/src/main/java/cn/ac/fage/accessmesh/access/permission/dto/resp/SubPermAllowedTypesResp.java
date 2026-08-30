package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 子权限允许类型只读查询响应体（api-contract §6.5.2）
 * <p>
 * 策略结果直接序列化（读写同源：与写链路 SUB_PERM fail-closed 校验同一解析器）；
 * 前端据 mode 过滤子权限配置器的资源类型选择器，不得硬编码允许集。
 * </p>
 *
 * @param parentResourceTypeCode        回显请求父资源类型
 * @param mode                          ALLOW_ALL / ALLOW_LIST / ALLOW_NONE
 * @param reason                        ALLOW_NONE 细分原因：CONFIG_MISSING / CONFIG_EMPTY /
 *                                      CONFIG_INVALID / PARENT_NOT_CONFIGURED / CHILD_TYPES_EMPTY；
 *                                      其余 mode 为 null
 * @param allowedChildResourceTypeCodes ALLOW_LIST 时的允许子类型集合（配置原文并集去重）；
 *                                      其余 mode 为空数组
 */
public record SubPermAllowedTypesResp(
    String parentResourceTypeCode,
    String mode,
    String reason,
    List<String> allowedChildResourceTypeCodes
) {}
