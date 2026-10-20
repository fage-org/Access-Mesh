package cn.ac.fage.accessmesh.example.task.mapper;

import cn.ac.fage.accessmesh.example.task.entity.DemoTask;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DemoTaskMapper {
    String COLUMNS="id,tenant_id,name,cron,selection::text,enabled,version,next_scheduled_time,created_by";
    @Select("SELECT "+COLUMNS+" FROM demo_task WHERE tenant_id=#{tenant} AND id=#{id} AND delete_flag=0")
    DemoTask get(@Param("tenant") long tenant,@Param("id") long id);
    @Select("SELECT "+COLUMNS+" FROM demo_task WHERE tenant_id=#{tenant} AND id=#{id} AND delete_flag=0 FOR UPDATE")
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    DemoTask lock(@Param("tenant") long tenant,@Param("id") long id);
    @Insert("""
        INSERT INTO demo_task(tenant_id,name,cron,selection,created_by,updated_by)
        VALUES(#{tenantId},#{name},#{cron},CAST(#{selection} AS jsonb),#{createdBy},#{createdBy})
        """)
    @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id")
    int insert(DemoTask task);
    @Update("""
        UPDATE demo_task SET name=#{name},cron=#{cron},selection=CAST(#{selection} AS jsonb),
          next_scheduled_time=#{nextScheduledTime},version=version+1,updated_by=#{createdBy},updated_at=now()
        WHERE tenant_id=#{tenantId} AND id=#{id} AND created_by=#{createdBy} AND version=#{version} AND delete_flag=0
        """)
    int update(DemoTask task);
    @Update("""
        UPDATE demo_task SET enabled=#{enabled},next_scheduled_time=#{nextScheduledTime},
          version=version+1,updated_by=#{createdBy},updated_at=now()
        WHERE tenant_id=#{tenantId} AND id=#{id} AND created_by=#{createdBy} AND version=#{version} AND delete_flag=0
        """)
    int enable(DemoTask task);
    @Select("SELECT "+COLUMNS+" FROM demo_task WHERE tenant_id=#{tenant} AND created_by=#{actor} AND delete_flag=0 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DemoTask> page(@Param("tenant") long tenant,@Param("actor") long actor,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT count(*) FROM demo_task WHERE tenant_id=#{tenant} AND created_by=#{actor} AND delete_flag=0")
    long count(@Param("tenant") long tenant,@Param("actor") long actor);
    @Select("SELECT "+COLUMNS+" FROM demo_task WHERE enabled AND delete_flag=0 AND (next_scheduled_time IS NULL OR next_scheduled_time<=clock_timestamp()) ORDER BY next_scheduled_time NULLS FIRST,id LIMIT 1")
    DemoTask due();
    @Select("SELECT clock_timestamp()")
    LocalDateTime now();
    @Update("UPDATE demo_task SET next_scheduled_time=#{next},updated_at=now() WHERE tenant_id=#{task.tenantId} AND id=#{task.id} AND version=#{task.version} AND enabled AND delete_flag=0")
    int schedule(@Param("task") DemoTask task,@Param("next") LocalDateTime next);
    @Update("UPDATE demo_task SET next_scheduled_time=NULL,updated_at=now() WHERE enabled AND delete_flag=0")
    int resetSchedule();
}
