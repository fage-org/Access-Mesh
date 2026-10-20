package cn.ac.fage.accessmesh.example.task.mapper;

import cn.ac.fage.accessmesh.example.task.entity.DemoExecution;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DemoExecutionMapper {
    String COLUMNS="id,tenant_id,execution_key,task_id,task_version,trigger_kind,trigger_key,scheduled_time,request_payload::text,status,attempt,next_attempt_at,policy_version,catalog_version,output_columns::text,result_row_count,expires_at,failure_reason,created_by";
    @Select("SELECT "+COLUMNS+" FROM demo_execution WHERE tenant_id=#{tenant} AND trigger_key=#{key} AND delete_flag=0")
    DemoExecution byTrigger(@Param("tenant") long tenant,@Param("key") UUID key);
    @Select("SELECT "+COLUMNS+" FROM demo_execution WHERE tenant_id=#{tenant} AND execution_key=#{key} AND delete_flag=0")
    DemoExecution byKey(@Param("tenant") long tenant,@Param("key") UUID key);
    @Insert("""
        INSERT INTO demo_execution(tenant_id,execution_key,trigger_kind,trigger_key,task_id,task_version,scheduled_time,request_payload,created_by,updated_by)
        VALUES(#{tenantId},#{executionKey},#{triggerKind},#{triggerKey},#{taskId},#{taskVersion},#{scheduledTime},CAST(#{requestPayload} AS jsonb),#{createdBy},#{createdBy})
        """)
    int insert(DemoExecution execution);
    @Select("SELECT "+COLUMNS+" FROM demo_execution WHERE tenant_id=#{tenant} AND task_id=#{task} AND scheduled_time=#{time} AND trigger_kind='CRON' AND delete_flag=0")
    DemoExecution bySchedule(@Param("tenant") long tenant,@Param("task") long task,@Param("time") java.time.LocalDateTime time);
    @Select("SELECT "+COLUMNS+" FROM demo_execution WHERE status='PENDING' AND attempt<3 AND delete_flag=0 AND (next_attempt_at IS NULL OR next_attempt_at<=clock_timestamp()) ORDER BY id LIMIT 1")
    DemoExecution next();
    @Update("""
        UPDATE demo_execution SET status='RUNNING',attempt=attempt+1,next_attempt_at=NULL,failure_reason=NULL,updated_at=now()
        WHERE tenant_id=#{tenantId} AND execution_key=#{executionKey} AND attempt=#{attempt} AND attempt<3
          AND status='PENDING' AND delete_flag=0 AND (next_attempt_at IS NULL OR next_attempt_at<=clock_timestamp())
        """)
    int claim(DemoExecution execution);
    @Update("""
        UPDATE demo_execution SET status=#{status},policy_version=#{policyVersion},catalog_version=#{catalogVersion},
          output_columns=CAST(#{outputColumns,jdbcType=VARCHAR} AS jsonb),result_row_count=#{resultRowCount},
          result_bytes=#{resultBytes,jdbcType=BINARY},expires_at=CASE WHEN #{status}='SUCCESS' THEN clock_timestamp()+interval '7 days' ELSE NULL END,
          failure_reason=#{failureReason},updated_at=now(),
          next_attempt_at=CASE WHEN #{status}='PENDING' THEN clock_timestamp()+CASE WHEN #{attempt}=1 THEN interval '10 seconds' ELSE interval '30 seconds' END ELSE NULL END
        WHERE tenant_id=#{tenantId} AND execution_key=#{executionKey} AND status='RUNNING' AND attempt=#{attempt} AND delete_flag=0
        """)
    int finish(DemoExecution execution);
    @Update("""
        UPDATE demo_execution SET status=CASE WHEN attempt<3 THEN 'PENDING' ELSE 'FAILED' END,
          failure_reason=CASE WHEN attempt<3 THEN NULL ELSE 'ATTEMPTS_EXHAUSTED' END,
          next_attempt_at=CASE WHEN attempt<3 THEN clock_timestamp()+CASE WHEN attempt=1 THEN interval '10 seconds' ELSE interval '30 seconds' END ELSE NULL END,
          updated_at=now() WHERE status IN ('PENDING','RUNNING') AND delete_flag=0 AND (status='RUNNING' OR attempt>=3)
        """)
    int recover();
    @Update("""
        UPDATE demo_execution SET status='EXPIRED',result_bytes=NULL,updated_at=now()
        WHERE id IN (SELECT id FROM demo_execution WHERE status='SUCCESS' AND expires_at<=clock_timestamp() AND delete_flag=0 ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED)
          AND status='SUCCESS' AND expires_at<=clock_timestamp()
        """)
    int expire();
    @Select("""
        SELECT result_bytes FROM demo_execution WHERE tenant_id=#{tenant} AND created_by=#{actor} AND execution_key=#{key}
          AND status='SUCCESS' AND expires_at>clock_timestamp() AND delete_flag=0
        """)
    DemoExecution download(@Param("tenant") long tenant,@Param("actor") long actor,@Param("key") UUID key);
    @Select("""
        SELECT EXISTS(SELECT 1 FROM demo_task WHERE tenant_id=#{tenant} AND id=#{task} AND created_by=#{actor} AND delete_flag=0)
        """)
    boolean ownsTask(@Param("tenant") long tenant,@Param("actor") long actor,@Param("task") long task);
    @Select("SELECT "+COLUMNS+" FROM demo_execution WHERE tenant_id=#{tenant} AND created_by=#{actor} AND delete_flag=0 AND (CAST(#{task} AS bigint) IS NULL OR task_id=#{task}) ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DemoExecution> page(@Param("tenant") long tenant,@Param("actor") long actor,@Param("task") Long task,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT count(*) FROM demo_execution WHERE tenant_id=#{tenant} AND created_by=#{actor} AND delete_flag=0 AND (CAST(#{task} AS bigint) IS NULL OR task_id=#{task})")
    long count(@Param("tenant") long tenant,@Param("actor") long actor,@Param("task") Long task);
}
