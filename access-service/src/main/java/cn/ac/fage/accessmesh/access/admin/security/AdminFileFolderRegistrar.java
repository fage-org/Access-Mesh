package cn.ac.fage.accessmesh.access.admin.security;

/**
 * ADMIN_FILE 文件夹投影登记端口（T-ADMIN-025）。
 * <p>
 * admin 域禁止依赖 permission 域（AccessServiceArchitectureTest：跨域编排必须经
 * access.application），文件夹投影登记能力经本端口做依赖反转——与
 * {@link AdminPermissionValidator} 同款形态：接口落 admin 域，实现落
 * {@code access.application}（application.security.AdminFileFolderRegistrarImpl），
 * 委托 {@code LocalProjectionDomainService.ensureAdminFileFolder}。
 * 语义：insert-if-absent 登记文件夹投影（bootstrap 预置 default/avatar/document/image
 * 四文件夹 + 上传新 bizType 惰性登记两条事实链；bizType 即文件夹实例 = 
 * {@code sys_file.bucket_name} = {@code resource_entity(ADMIN_FILE).code}）。
 * </p>
 */
public interface AdminFileFolderRegistrar {

    /**
     * 登记文件夹投影（有效行已存在为 no-op，名称以首建为准）。
     *
     * @param tenantId   租户ID
     * @param folderCode 文件夹编码（= 上传 bizType，调用方保证格式白名单）
     * @param name       展示名（仅首次创建生效；惰性登记传 code 本身）
     * @return resource_entity.id
     */
    Long ensureFolder(Long tenantId, String folderCode, String name);
}
