package cn.ac.fage.accessmesh.example.data.mapper;

import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Identifiers come only from DemoField; every input and predicate value is a bound parameter. */
public final class DemoDataSql {
    private static final String FROM=" FROM demo_record r JOIN demo_import_batch b ON b.id=r.batch_id AND b.tenant_id=r.tenant_id"
        +" JOIN demo_report source ON source.id=b.report_id AND source.tenant_id=b.tenant_id";
    private static final String GUARD="r.tenant_id=#{plan.tenantId} AND b.tenant_id=#{plan.tenantId} AND source.tenant_id=#{plan.tenantId}"
        +" AND source.dataset_id=#{plan.datasetId} AND r.delete_flag=0 AND b.delete_flag=0 AND b.status='IMPORTED' AND source.delete_flag=0";
    public String page(Map<String,Object> args) {
        var plan=plan(args,6);
        return "SELECT "+columns(plan)+FROM+where(plan)+" ORDER BY "+plan.orderBy()+" LIMIT #{limit} OFFSET #{offset}";
    }
    public String count(Map<String,Object> args) {
        var plan=plan(args,4); return "SELECT count(*)"+FROM+where(plan);
    }
    public String one(Map<String,Object> args) {
        var plan=plan(args,5);
        return "SELECT "+columns(plan)+FROM+where(plan)+" AND r.record_code=#{code} LIMIT 1";
    }
    public String batch(Map<String,Object> args) {
        var codes=(List<?>)args.get("codes");
        var plan=plan(args,4+codes.size());
        StringBuilder values=new StringBuilder();
        for(int i=0;i<codes.size();i++) {
            if(i>0) values.append(',');
            values.append("(CAST(#{codes[").append(i).append("]} AS varchar),").append(i).append(')');
        }
        return "SELECT requested.request_index AS \"requestIndex\","+columns(plan)+FROM
            +" JOIN (VALUES "+values+") requested(record_code,request_index) ON requested.record_code=r.record_code"
            +where(plan)+" ORDER BY requested.request_index";
    }
    public String update(Map<String,Object> args) {
        var changes=(List<?>)args.get("changes");
        var plan=plan(args,5+changes.size()*2);
        StringBuilder values=new StringBuilder();
        for(int i=0;i<changes.size();i++) {
            if(i>0) values.append(',');
            values.append("(CAST(#{changes[").append(i).append("].recordCode} AS varchar),CAST(#{changes[")
                .append(i).append("].remark,jdbcType=VARCHAR} AS varchar))");
        }
        return "UPDATE demo_record r SET remark=changed.remark,updated_by=#{actor},updated_at=now()"
            +" FROM demo_import_batch b JOIN demo_report source ON source.id=b.report_id AND source.tenant_id=b.tenant_id"
            +" CROSS JOIN (VALUES "+values+") changed(record_code,remark)"
            +" WHERE r.batch_id=b.id AND r.record_code=changed.record_code AND "+GUARD+" AND ("+plan.predicate()+")";
    }
    private static String columns(DataSqlPlan plan) {
        if(plan.columns().isEmpty()) throw new IllegalStateException("Cannot query business records without authorized columns");
        return plan.columns().stream().map(c -> (c.field().kind()==DemoField.Kind.DATE?"to_char(r."+c.field().column()+",'YYYY-MM-DD')":"r."+c.field().column())
            +" AS \""+c.field().key()+"\"").collect(Collectors.joining(","));
    }
    private static String where(DataSqlPlan plan) { return " WHERE "+GUARD+" AND ("+plan.predicate()+")"; }
    private static DataSqlPlan plan(Map<String,Object> args,int fixedParameters) {
        var plan=(DataSqlPlan)args.get("plan");
        if(plan.parameters().size()+fixedParameters>4096) throw new BizException(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getMessage());
        return plan;
    }
}
