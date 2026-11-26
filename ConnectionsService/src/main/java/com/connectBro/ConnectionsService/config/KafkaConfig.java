package com.connectBro.ConnectionsService.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class KafkaConfig {
    @Bean
    public NewTopic connectionRequestTopic() {
        return new NewTopic("connection_request_topic", 3, (short) 1);
    }

    @Bean
    public NewTopic connectionAcceptedTopic() {
        return new NewTopic("connection_accepted_topic", 3, (short) 1);
    }

    @Bean(name = "connectionNotificationExecutor")
    public Executor connectionNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("connection-event-");
        executor.initialize();
        return executor;
    }
}
