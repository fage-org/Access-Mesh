package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysUserNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户通知关联数据访问接口
 * <p>
 * 提供用户通知关联表的基础CRUD操作。
 * 用户通知关联定义用户与通知公告的阅读关系。
 * </p>
 */
@Mapper
public interface SysUserNoticeMapper extends BaseMapper<SysUserNotice> {
}