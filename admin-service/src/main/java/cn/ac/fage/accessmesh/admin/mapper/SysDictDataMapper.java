package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysDictData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统字典数据访问接口
 * <p>
 * 提供字典数据表的基础CRUD操作。
 * 字典数据归属于字典类型，包含标签、值、排序等属性。
 * </p>
 */
@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {
}