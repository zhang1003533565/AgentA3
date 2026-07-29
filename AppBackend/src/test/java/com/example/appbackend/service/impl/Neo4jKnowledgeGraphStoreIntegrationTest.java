package com.example.appbackend.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "NEO4J_INTEGRATION_TEST", matches = "true")
class Neo4jKnowledgeGraphStoreIntegrationTest {

    @Test
    void syncsAndLoadsTheRealPythonTopology() {
        String uri = System.getenv().getOrDefault("NEO4J_URI", "bolt://localhost:7687");
        String username = System.getenv().getOrDefault("NEO4J_USERNAME", "neo4j");
        String password = System.getenv().getOrDefault("NEO4J_PASSWORD", "neo4j-dev-password");

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password))) {
            driver.verifyConnectivity();
            Neo4jKnowledgeGraphStore store =
                    new Neo4jKnowledgeGraphStore(Neo4jClient.create(driver));

            List<PythonKnowledgeGraphServiceImpl.CatalogNode> topology =
                    store.syncAndLoad("python", PythonKnowledgeGraphServiceImpl.defaultTopology());

            assertThat(topology).hasSameSizeAs(PythonKnowledgeGraphServiceImpl.defaultTopology());
            assertThat(topology).anySatisfy(node -> {
                assertThat(node.id()).isEqualTo("python.data_type.collection");
                assertThat(node.prerequisites()).containsExactly("python.expression.arithmetic");
            });
        }
    }
}
