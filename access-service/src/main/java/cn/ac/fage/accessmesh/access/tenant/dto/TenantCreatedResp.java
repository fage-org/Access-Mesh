package cn.ac.fage.accessmesh.access.tenant.dto;

/** 初始密码仅在开通成功响应展示一次，禁止记录整个返回对象。 */
public record TenantCreatedResp(TenantResp tenant,String adminUsername,String initialPassword) {}
