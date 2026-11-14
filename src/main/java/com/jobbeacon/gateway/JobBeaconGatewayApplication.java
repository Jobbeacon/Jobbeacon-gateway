package com.jobbeacon.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobBeaconGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(JobBeaconGatewayApplication.class, args);
  }
}
