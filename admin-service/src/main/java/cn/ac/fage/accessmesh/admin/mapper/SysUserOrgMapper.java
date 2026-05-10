package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户组织关联数据访问接口
 * <p>
 * 提供用户组织关联表的基础CRUD操作。
 * 用户组织关联定义用户与组织的绑定关系，支持用户加入多个组织。
 * </p>
 */
@Mapper
public interface SysUserOrgMapper extends BaseMapper<SysUserOrg> {
}