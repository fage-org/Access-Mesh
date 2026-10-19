package cn.ac.fage.accessmesh.example.data.domain;

import cn.ac.fage.accessmesh.example.data.entity.DemoPolicy;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration;

public interface DemoPolicyDomainService {
    DemoPolicy current(long tenant);
    DemoPolicy save(long tenant,long actor,long expectedVersion,PolicyConfiguration configuration);
}
