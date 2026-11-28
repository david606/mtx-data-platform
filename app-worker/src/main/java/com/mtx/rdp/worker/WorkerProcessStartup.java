package com.mtx.rdp.worker;

import com.mtx.rdp.common.ApplicationRole;
import com.mtx.rdp.common.DevelopmentPlacement;
import com.mtx.rdp.common.DevelopmentProfile;
import com.mtx.rdp.common.PlatformConfigurationException;
import com.mtx.rdp.common.StartupChecklist;
import com.mtx.rdp.common.StartupReport;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class WorkerProcessStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkerProcessStartup.class);

    private final Environment environment;
    private final WorkerRuntimeProperties properties;
    private final WorkerDevInfrastructureProperties devInfrastructure;
    private final HealthEndpoint healthEndpoint;

    public WorkerProcessStartup(
            Environment environment,
            WorkerRuntimeProperties properties,
            WorkerDevInfrastructureProperties devInfrastructure,
            HealthEndpoint healthEndpoint) {
        this.environment = environment;
        this.properties = properties;
        this.devInfrastructure = devInfrastructure;
        this.healthEndpoint = healthEndpoint;
    }

    @Override
    public void run(ApplicationArguments args) {
        StartupReport report = StartupChecklist.check(
                environment.getProperty("spring.application.name"),
                WorkerApplication.APPLICATION_NAME,
                properties.getRole(),
                ApplicationRole.WORKER,
                List.of(environment.getActiveProfiles()));
        DevelopmentPlacement placement = DevelopmentProfile.requireSingle(List.of(environment.getActiveProfiles()));
        devInfrastructure.requireFor(placement);
        Status status = healthEndpoint.health().getStatus();
        if (!Status.UP.equals(status)) {
            throw new PlatformConfigurationException("进程健康状态不是 UP: " + status.getCode());
        }
        log.info(
                "进程启动完成 applicationName={} role={} environment={} health={} {}",
                report.applicationName(),
                report.role().configValue(),
                report.environmentLabel(),
                status.getCode(),
                devInfrastructure.publicDescription());
    }
}
