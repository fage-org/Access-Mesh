package cn.ac.fage.accessmesh.access.auth.dto;

/** 密码重置的一次性返回，不得记录或持久化此对象。 */
public record IssuedPasswordResp(String password) {}
