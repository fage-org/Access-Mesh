package cn.ac.fage.accessmesh.example.data.service;

import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.data.dto.ImportDtos.*;
import org.springframework.web.multipart.MultipartFile;

public interface DemoImportAppService {
    ImportResp submit(Caller caller,ImportReq request,MultipartFile file);
    ImportResp status(Caller caller,KeyReq request);
    ImportResp revoke(Caller caller,KeyReq request);
}
