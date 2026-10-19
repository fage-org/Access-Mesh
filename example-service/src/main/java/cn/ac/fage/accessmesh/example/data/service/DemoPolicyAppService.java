package cn.ac.fage.accessmesh.example.data.service;

import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.data.dto.PolicyDtos.*;

public interface DemoPolicyAppService {
    PolicyResp get(Caller caller,GetReq request);
    PolicyResp save(Caller caller,SaveReq request);
}
