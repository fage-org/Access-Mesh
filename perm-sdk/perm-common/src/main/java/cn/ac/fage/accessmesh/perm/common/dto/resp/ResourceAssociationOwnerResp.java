package cn.ac.fage.accessmesh.perm.common.dto.resp;

/** 只读归属显示；不用显示名替代业务键，不暴露 IAM 内部身份。 */
public record ResourceAssociationOwnerResp(String resourceTypeCode, String code, String codeType,
                                           String name, Integer status) {}
