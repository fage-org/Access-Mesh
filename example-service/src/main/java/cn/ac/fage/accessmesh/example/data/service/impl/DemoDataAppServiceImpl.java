package cn.ac.fage.accessmesh.example.data.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.data.domain.DemoDataDomainService;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.*;
import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan;
import cn.ac.fage.accessmesh.example.data.service.DemoDataAppService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

@Service
public class DemoDataAppServiceImpl implements DemoDataAppService {
    private final DemoDataDomainService data;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    public DemoDataAppServiceImpl(DemoDataDomainService data,PlatformTransactionManager transactions) {
        this.data=data; read=new TransactionTemplate(transactions); write=new TransactionTemplate(transactions);
        read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @Override
    public FieldsResp fields(Caller c,FieldsReq r) {
        var plan=data.authorizeRead(c,new Selection(r.reportCode(),null,null,null));
        return sql(() -> read.execute(s -> {
            data.verify(plan);
            return new FieldsResp(plan.policyVersion(),plan.catalogVersion(),plan.columns().stream().map(column ->
                new FieldDescription(column.id(),column.field().key(),column.field().kind().name(),column.filterable(),column.sortable())).toList());
        }));
    }
    @Override
    public PageDataResp page(Caller c,PageReq r) {
        var plan=data.authorizeRead(c,r.selection());
        return sql(() -> read.execute(s -> {
            var page=data.page(plan,r.pageNum()==null?1:r.pageNum(),r.pageSize()==null?20:r.pageSize());
            return new PageDataResp(state(plan,page.total()>0),plan.policyVersion(),plan.catalogVersion(),columns(plan),page);
        }));
    }
    @Override
    public RecordResp get(Caller c,GetReq r) {
        var plan=data.authorizeRead(c,r.selection());
        return sql(() -> read.execute(s -> {
            var row=data.get(plan,r.recordCode());
            return new RecordResp(state(plan,row!=null),plan.policyVersion(),plan.catalogVersion(),columns(plan),row);
        }));
    }
    @Override
    public BatchResp batch(Caller c,BatchGetReq r) {
        var plan=data.authorizeRead(c,r.selection());
        return sql(() -> read.execute(s -> {
            var rows=data.batch(plan,r.recordCodes());
            List<BatchItem> items=new ArrayList<>();
            for(int index=0;index<r.recordCodes().size();index++) items.add(new BatchItem(index,rows.containsKey(index),rows.get(index)));
            return new BatchResp(state(plan,!rows.isEmpty()),plan.policyVersion(),plan.catalogVersion(),columns(plan),List.copyOf(items));
        }));
    }
    @Override
    public UpdatedResp update(Caller c,UpdateReq r) { return change(c,r.reportCode(),List.of(r.change())); }
    @Override
    public UpdatedResp batchUpdate(Caller c,BatchUpdateReq r) { return change(c,r.reportCode(),r.changes()); }
    private UpdatedResp change(Caller c,String report,List<Change> changes) {
        var plan=data.authorizeEdit(c,report);
        return sql(() -> write.execute(s -> new UpdatedResp(data.update(plan,c.userId(),changes))));
    }
    private static List<Column> columns(DataSqlPlan plan) {
        return plan.columns().stream().map(c -> new Column(c.id(),c.field().key(),c.field().kind().name())).toList();
    }
    private static String state(DataSqlPlan plan,boolean rows) { return plan.columns().isEmpty()?"EMPTY_FIELDS":rows?"DATA":"EMPTY_ROWS"; }
    private static <T> T sql(Supplier<T> operation) {
        try { return operation.get(); }
        catch(DataAccessException e) {
            for(Throwable cause=e;cause!=null;cause=cause.getCause()) {
                if(cause instanceof BizException business) throw business;
                if(cause instanceof java.sql.SQLException database && "57014".equals(database.getSQLState())) {
                    throw new BizException(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),"SQL 执行超过期限，请缩小筛选范围");
                }
            }
            throw e;
        }
    }
}
