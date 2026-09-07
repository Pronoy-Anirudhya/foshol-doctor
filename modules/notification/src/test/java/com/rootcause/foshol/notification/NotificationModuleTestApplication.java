package com.rootcause.foshol.notification;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        scanBasePackages = "com.rootcause.foshol.notification",
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EntityScan(basePackages = "com.rootcause.foshol.notification")
@EnableJpaRepositories(basePackages = "com.rootcause.foshol.notification")
public class NotificationModuleTestApplication {}
