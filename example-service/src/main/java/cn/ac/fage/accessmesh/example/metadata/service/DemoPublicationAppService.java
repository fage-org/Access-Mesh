package cn.ac.fage.accessmesh.example.metadata.service;

import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.metadata.dto.PublicationDtos.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

public interface DemoPublicationAppService {
    void publishNext();
    PublicationResp get(Caller caller, PublicationGetReq req);
    PublicationResp retry(Caller caller, PublicationGetReq req);
    PageResp<PublicationResp> page(Caller caller, PublicationPageReq req);
}
