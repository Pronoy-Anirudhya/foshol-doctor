package com.rootcause.foshol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(sharedModules = "common")
@SpringBootApplication
public class FosholDoctorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FosholDoctorApplication.class, args);
    }
}
