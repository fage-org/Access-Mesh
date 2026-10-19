package cn.ac.fage.accessmesh.example.metadata.mapper;

import cn.ac.fage.accessmesh.example.metadata.entity.DemoPublication;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DemoPublicationMapper {
    @Select("""
        SELECT greatest(clock_timestamp(),coalesce(max(occurred_at),clock_timestamp()))
        FROM demo_publication WHERE tenant_id=#{tenant}
        """)
    LocalDateTime eventTime(@Param("tenant") long tenant);

    @Insert("""
        <script>
        INSERT INTO demo_publication(tenant_id,resource_type_code,resource_code,code_type,source_version,
          occurred_at,payload,created_by,updated_by)
        VALUES
        <foreach collection="events" item="e" separator=",">
          (#{e.tenantId},#{e.resourceTypeCode},#{e.resourceCode},#{e.codeType},#{e.sourceVersion},
           #{e.occurredAt},CAST(#{e.payload} AS jsonb),#{e.createdBy},#{e.createdBy})
        </foreach>
        </script>
        """)
    int insertBatch(@Param("events") List<DemoPublication> events);

    @Select("""
        SELECT id,tenant_id,resource_type_code,resource_code,code_type,source_version,occurred_at,payload::text,status,attempt,next_attempt_at,failure_reason,delete_flag,created_by,updated_by,deleted_by,created_at,updated_at,deleted_at FROM demo_publication WHERE status='PENDING' AND delete_flag=0
          AND (next_attempt_at IS NULL OR next_attempt_at<=clock_timestamp())
        ORDER BY next_attempt_at NULLS FIRST,id LIMIT 1
        """)
    DemoPublication next();

    @Select("SELECT id,tenant_id,resource_type_code,resource_code,code_type,source_version,occurred_at,payload::text,status,attempt,next_attempt_at,failure_reason,delete_flag,created_by,updated_by,deleted_by,created_at,updated_at,deleted_at FROM demo_publication WHERE tenant_id=#{tenant} AND id=#{id} AND delete_flag=0")
    DemoPublication byId(@Param("tenant") long tenant, @Param("id") long id);

    @Select("""
        SELECT id,tenant_id,resource_type_code,resource_code,code_type,source_version,occurred_at,payload::text,status,attempt,next_attempt_at,failure_reason,delete_flag,created_by,updated_by,deleted_by,created_at,updated_at,deleted_at FROM demo_publication WHERE tenant_id=#{tenant} AND resource_type_code=#{type}
          AND resource_code=#{code} AND code_type=#{codeType} AND delete_flag=0
        ORDER BY source_version DESC LIMIT 1
        """)
    DemoPublication latest(@Param("tenant") long tenant, @Param("type") String type,
                           @Param("code") String code, @Param("codeType") String codeType);

    @Select("""
        SELECT p.id,p.tenant_id,p.resource_type_code,p.resource_code,p.code_type,p.source_version,p.occurred_at,p.payload::text,p.status,p.attempt,p.next_attempt_at,p.failure_reason,p.delete_flag,p.created_by,p.updated_by,p.deleted_by,p.created_at,p.updated_at,p.deleted_at FROM demo_publication p WHERE tenant_id=#{tenant} AND delete_flag=0
          AND (CAST(#{status} AS text) IS NULL OR status=#{status})
          AND NOT EXISTS(SELECT 1 FROM demo_publication newer WHERE newer.tenant_id=p.tenant_id
            AND newer.resource_type_code=p.resource_type_code AND newer.resource_code=p.resource_code
            AND newer.code_type=p.code_type AND newer.source_version>p.source_version AND newer.delete_flag=0)
        ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
        """)
    List<DemoPublication> page(@Param("tenant") long tenant, @Param("status") String status,
                               @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
        SELECT count(*) FROM demo_publication p WHERE tenant_id=#{tenant} AND delete_flag=0
          AND (CAST(#{status} AS text) IS NULL OR status=#{status})
          AND NOT EXISTS(SELECT 1 FROM demo_publication newer WHERE newer.tenant_id=p.tenant_id
            AND newer.resource_type_code=p.resource_type_code AND newer.resource_code=p.resource_code
            AND newer.code_type=p.code_type AND newer.source_version>p.source_version AND newer.delete_flag=0)
        """)
    long count(@Param("tenant") long tenant, @Param("status") String status);

    @Update("""
        UPDATE demo_publication SET attempt=attempt+1, next_attempt_at=clock_timestamp()+
            CASE WHEN attempt=0 THEN interval '10 seconds' ELSE interval '30 seconds' END,updated_at=now()
        WHERE tenant_id=#{tenantId} AND id=#{id} AND status='PENDING' AND attempt=#{attempt}
          AND (next_attempt_at IS NULL OR next_attempt_at<=clock_timestamp()) AND delete_flag=0
        """)
    int claim(DemoPublication event);

    @Update("""
        UPDATE demo_publication SET status=#{status},failure_reason=#{reason},updated_at=now(),
          next_attempt_at=CASE WHEN #{status}='PENDING' THEN clock_timestamp()+
            CASE WHEN #{event.attempt}=1 THEN interval '10 seconds' ELSE interval '30 seconds' END ELSE NULL END
        WHERE tenant_id=#{event.tenantId} AND id=#{event.id} AND attempt=#{event.attempt}
          AND status='PENDING' AND delete_flag=0
        """)
    int finish(@Param("event") DemoPublication event, @Param("status") String status, @Param("reason") String reason);

    @Update("""
        UPDATE demo_publication SET status='PENDING',attempt=0,next_attempt_at=NULL,failure_reason=NULL,
          updated_by=#{actor},updated_at=now()
        WHERE tenant_id=#{event.tenantId} AND id=#{event.id} AND status='FAILED' AND delete_flag=0
        """)
    int retry(@Param("event") DemoPublication event, @Param("actor") long actor);

    @Update("""
        UPDATE demo_report SET publication_status=#{status}
        WHERE tenant_id=#{event.tenantId} AND report_code=#{event.resourceCode} AND version=#{event.sourceVersion}
          AND #{event.resourceTypeCode}='DEMO_REPORT'
        """)
    int markReport(@Param("event") DemoPublication event, @Param("status") String status);

    @Update("""
        UPDATE demo_report_field f SET publication_status=#{status}
        FROM demo_report r WHERE f.tenant_id=#{event.tenantId} AND f.resource_code=#{event.resourceCode}
          AND f.version=#{event.sourceVersion} AND r.id=f.report_id AND r.tenant_id=f.tenant_id
          AND r.field_resource_type_code=#{event.resourceTypeCode} AND #{event.codeType}='default'
        """)
    int markField(@Param("event") DemoPublication event, @Param("status") String status);

    @Update("""
        UPDATE demo_scope SET publication_status=#{status}
        WHERE tenant_id=#{event.tenantId} AND resource_type_code=#{event.resourceTypeCode}
          AND resource_code=#{event.resourceCode} AND code_type=#{event.codeType} AND version=#{event.sourceVersion}
        """)
    int markScope(@Param("event") DemoPublication event, @Param("status") String status);
}
