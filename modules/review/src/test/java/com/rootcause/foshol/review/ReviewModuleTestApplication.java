package com.rootcause.foshol.review;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.rootcause.foshol.review",
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EntityScan(basePackages = "com.rootcause.foshol.review")
@EnableJpaRepositories(basePackages = "com.rootcause.foshol.review")
@EnableScheduling
public class ReviewModuleTestApplication {}
