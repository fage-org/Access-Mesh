package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * 依赖循环检测结果响应体
 * <p>
 * 返回资源依赖循环检测的结果。
 * hasCycle=true 表示添加此依赖关系会创建循环引用。
 * 循环依赖会导致权限判定死循环，需要避免。
 * </p>
 *
 * @param hasCycle             是否存在循环依赖
 * @param sourceResourceTypeCode 源资源类型编码
 * @param sourceResourceCode   源资源编码
 * @param targetResourceTypeCode 目标资源类型编码
 * @param targetResourceCode   目标资源编码
 */
public record DependencyCycleCheckResp(
    boolean hasCycle,
    String sourceResourceTypeCode,
    String sourceResourceCode,
    String targetResourceTypeCode,
    String targetResourceCode
) {}