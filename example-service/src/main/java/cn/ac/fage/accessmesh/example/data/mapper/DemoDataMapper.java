package cn.ac.fage.accessmesh.example.data.mapper;

import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.Change;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface DemoDataMapper {
    @Select("SELECT set_config('statement_timeout','5000',true)")
    String setTimeout();
    @SelectProvider(type=DemoDataSql.class,method="page")
    List<Map<String,Object>> page(@Param("plan") DataSqlPlan plan,@Param("limit") int limit,@Param("offset") long offset);
    @SelectProvider(type=DemoDataSql.class,method="count")
    long count(@Param("plan") DataSqlPlan plan);
    @SelectProvider(type=DemoDataSql.class,method="one")
    List<Map<String,Object>> one(@Param("plan") DataSqlPlan plan,@Param("code") String code);
    @SelectProvider(type=DemoDataSql.class,method="batch")
    List<Map<String,Object>> batch(@Param("plan") DataSqlPlan plan,@Param("codes") List<String> codes);
    @UpdateProvider(type=DemoDataSql.class,method="update")
    int update(@Param("plan") DataSqlPlan plan,@Param("actor") long actor,@Param("changes") List<Change> changes);
}
