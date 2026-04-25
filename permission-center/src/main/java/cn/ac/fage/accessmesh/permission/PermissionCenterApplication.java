package cn.ac.fage.accessmesh.permission;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@MapperScan("cn.ac.fage.accessmesh.permission.mapper")
public class PermissionCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PermissionCenterApplication.class, args);
    }
}
