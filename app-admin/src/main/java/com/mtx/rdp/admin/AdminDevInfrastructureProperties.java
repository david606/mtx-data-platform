package com.mtx.rdp.admin;

import com.mtx.rdp.common.DevInfrastructure;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mtx.rdp.dev")
public class AdminDevInfrastructureProperties extends DevInfrastructure {
}
