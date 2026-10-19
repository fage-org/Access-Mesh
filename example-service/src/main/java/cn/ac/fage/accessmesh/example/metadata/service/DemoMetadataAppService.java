package cn.ac.fage.accessmesh.example.metadata.service;

import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

public interface DemoMetadataAppService {
    ReportResp create(Caller caller, ReportCreateReq req);
    ReportResp get(Caller caller, ReportGetReq req);
    ReportResp update(Caller caller, ReportUpdateReq req);
    FieldRegistrationResp register(Caller caller, FieldRegisterReq req);
    PageResp<FieldResp> fields(Caller caller, FieldListReq req);
    void updateField(Caller caller, FieldUpdateReq req);
    void removeFields(Caller caller, FieldRemoveReq req);
    ScopeResp saveScope(Caller caller, ScopeSaveReq req);
}
