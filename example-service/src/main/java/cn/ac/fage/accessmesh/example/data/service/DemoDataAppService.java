package cn.ac.fage.accessmesh.example.data.service;

import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.*;

public interface DemoDataAppService {
    FieldsResp fields(Caller caller,FieldsReq request);
    PageDataResp page(Caller caller,PageReq request);
    RecordResp get(Caller caller,GetReq request);
    BatchResp batch(Caller caller,BatchGetReq request);
    UpdatedResp update(Caller caller,UpdateReq request);
    UpdatedResp batchUpdate(Caller caller,BatchUpdateReq request);
}
