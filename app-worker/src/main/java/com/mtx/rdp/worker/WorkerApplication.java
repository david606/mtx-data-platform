package com.mtx.rdp.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        WorkerRuntimeProperties.class,
        WorkerDevInfrastructureProperties.class
})
public class WorkerApplication {

    public static final String APPLICATION_NAME = "mtx-rdp-worker";

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
