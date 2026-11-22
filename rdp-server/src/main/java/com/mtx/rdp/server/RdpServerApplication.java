package com.mtx.rdp.server;

import com.mtx.rdp.Platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RdpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RdpServerApplication.class, args);
    }

    public String platformName() {
        return Platform.NAME;
    }
}
