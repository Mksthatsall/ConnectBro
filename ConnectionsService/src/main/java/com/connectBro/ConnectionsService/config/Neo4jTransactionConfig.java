package com.connectBro.ConnectionsService.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import com.connectBro.ConnectionsService.repository.ConnectionRequestRepository;
import com.connectBro.ConnectionsService.repository.GraphSyncEventRepository;

@Configuration
@EnableNeo4jRepositories(
        basePackages = "com.connectBro.ConnectionsService.repository",
        transactionManagerRef = "neo4jTransactionManager",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {ConnectionRequestRepository.class, GraphSyncEventRepository.class}))
public class Neo4jTransactionConfig {
    @Bean(name = "neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}
