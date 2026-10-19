package cn.ac.fage.accessmesh.example.metadata.publication;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableFeignClients(clients=DemoResourceSyncClient.class)
public class DemoPublicationConfiguration {}
