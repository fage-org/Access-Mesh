package cn.ac.fage.accessmesh.example.data.domain;

import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.*;
import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import java.util.List;
import java.util.Map;

public interface DemoDataDomainService {
    DataSqlPlan authorizeRead(Caller caller,Selection selection);
    DataSqlPlan authorizeEdit(Caller caller,String reportCode);
    void verify(DataSqlPlan plan);
    PageResp<Map<String,Object>> page(DataSqlPlan plan,int page,int size);
    Map<String,Object> get(DataSqlPlan plan,String code);
    Map<Integer,Map<String,Object>> batch(DataSqlPlan plan,List<String> codes);
    int update(DataSqlPlan plan,long actor,List<Change> changes);
}
