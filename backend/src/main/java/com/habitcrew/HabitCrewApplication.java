package com.habitcrew;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HabitCrewApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabitCrewApplication.class, args);
    }
}
