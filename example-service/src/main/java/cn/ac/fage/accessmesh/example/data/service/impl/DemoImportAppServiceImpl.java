package cn.ac.fage.accessmesh.example.data.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.data.domain.DemoImportDomainService;
import cn.ac.fage.accessmesh.example.data.domain.DemoDataDomainService;
import cn.ac.fage.accessmesh.example.data.entity.DemoImportBatch;
import cn.ac.fage.accessmesh.example.data.dto.ImportDtos.*;
import cn.ac.fage.accessmesh.example.data.csv.DemoCsvReader;
import cn.ac.fage.accessmesh.example.data.service.DemoImportAppService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataAccessException;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.HexFormat;
import java.security.MessageDigest;

@Service
public class DemoImportAppServiceImpl implements DemoImportAppService {
    private final DemoImportDomainService imports;
    private final DemoDataDomainService data;
    private final DemoMetadataDomainService metadata;
    private final BusinessPermChecker permissions;
    private final TransactionTemplate write;
    private final DemoCsvReader csv=new DemoCsvReader();
    public DemoImportAppServiceImpl(DemoImportDomainService imports,DemoDataDomainService data,
        DemoMetadataDomainService metadata,BusinessPermChecker permissions,PlatformTransactionManager transactions) {
        this.imports=imports; this.data=data; this.metadata=metadata; this.permissions=permissions; write=new TransactionTemplate(transactions);
    }
    @Override public ImportResp submit(Caller caller,ImportReq request,MultipartFile file) {
        require(caller);
        byte[] bytes;
        try(var input=file.getInputStream()) { bytes=input.readNBytes(DemoCsvReader.MAX_BYTES+1); }
        catch(java.io.IOException e) { throw new cn.ac.fage.accessmesh.common.exception.SystemException(cn.ac.fage.accessmesh.common.enums.GlobalErrorCode.SYSTEM_ERROR.code(),"读取上传内容失败",e); }
        var parsed=csv.read(bytes);
        var plan=data.authorizeImport(caller,request.reportCode(),new HashSet<>(parsed.fields()));
        String hash;
        try { hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
        try {
            return write.execute(s -> view(imports.apply(plan,caller.userId(),UUID.fromString(request.importKey()),hash,parsed)));
        } catch(DataAccessException e) { throw translate(e); }
    }
    @Override public ImportResp status(Caller caller,KeyReq request) {
        require(caller); return view(imports.owned(caller.tenantId(),caller.userId(),UUID.fromString(request.importKey())));
    }
    @Override public ImportResp revoke(Caller caller,KeyReq request) {
        require(caller);
        var key=UUID.fromString(request.importKey());
        var batch=imports.owned(caller.tenantId(),caller.userId(),key);
        var report=metadata.getReportById(caller.tenantId(),batch.getReportId());
        var plan=data.authorizeImport(caller,report.getReportCode(),imports.originalFields(batch));
        try { return write.execute(s -> view(imports.revoke(plan,caller.userId(),key))); }
        catch(DataAccessException e) { throw translate(e); }
    }
    private void require(Caller caller) {
        if(!permissions.check(Long.toString(caller.tenantId()),Long.toString(caller.userId()),caller.clientIp(),
            BusinessPermChecker.Target.of(DemoPermission.DATASET_MANAGE.type(),null,DemoPermission.DATASET_MANAGE.operation())).allowed()) {
            throw new BizException(ExampleErrorCode.PERMISSION_DENIED.getCode(),ExampleErrorCode.PERMISSION_DENIED.getMessage());
        }
    }
    private static ImportResp view(DemoImportBatch batch) { return new ImportResp(batch.getImportKey().toString(),batch.getStatus(),batch.getRecordCount()); }
    private static RuntimeException translate(DataAccessException error) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
            if(cause instanceof org.postgresql.util.PSQLException pg && pg.getServerErrorMessage()!=null
                && pg.getServerErrorMessage().getConstraint()!=null
                && Set.of("uniq_demo_record_tenant_code","uniq_demo_import_batch_tenant_key").contains(pg.getServerErrorMessage().getConstraint())) {
                return new BizException(ExampleErrorCode.DEMO_IMPORT_CONFLICT.getCode(),ExampleErrorCode.DEMO_IMPORT_CONFLICT.getMessage());
            }
            if(cause instanceof java.sql.SQLException sql && "57014".equals(sql.getSQLState())) return new BizException(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),"导入或撤销超过 SQL 期限");
        }
        return error;
    }
}
