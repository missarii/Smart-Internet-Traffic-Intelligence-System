package com.traffic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart Internet Traffic Intelligence System
 *
 * Production-grade real-time web traffic monitoring, bypass detection,
 * and analytics platform inspired by Cloudflare / AWS / Datadog internals.
 */
@SpringBootApplication
@EnableScheduling
public class TrafficApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrafficApplication.class, args);
    }
}
