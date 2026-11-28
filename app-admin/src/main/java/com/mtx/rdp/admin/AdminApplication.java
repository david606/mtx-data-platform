package com.mtx.rdp.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AdminRuntimeProperties.class,
        AdminDevInfrastructureProperties.class
})
public class AdminApplication {

    public static final String APPLICATION_NAME = "mtx-rdp-admin";

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
