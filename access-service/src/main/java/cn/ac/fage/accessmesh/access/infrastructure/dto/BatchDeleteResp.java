package cn.ac.fage.accessmesh.access.infrastructure.dto;

import java.util.List;

/** 请求目标去重后的删除结果；级联后代仅在被请求时列入明细。 */
public record BatchDeleteResp<T>(List<T> deleted, List<T> denied, List<T> notFound) {}
