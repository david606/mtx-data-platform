package com.mtx.rdp.worker;

import com.mtx.rdp.common.DevInfrastructure;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mtx.rdp.dev")
public class WorkerDevInfrastructureProperties extends DevInfrastructure {
}
