package com.example.agentflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({"com.example.agentflow.mapper", "com.agentflow.gateway.memory"})
@ComponentScan(basePackages = {"com.example.agentflow", "com.agentflow"})
public class AgentFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentFlowApplication.class, args);
    }

}
