package com.example.appbackend.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "knowledge.graph.neo4j", name = "enabled", havingValue = "true")
public class Neo4jKnowledgeGraphStore {

    private static final String CREATE_CONSTRAINT = """
            CREATE CONSTRAINT knowledge_point_id IF NOT EXISTS
            FOR (node:KnowledgePoint) REQUIRE node.id IS UNIQUE
            """;
    private static final String DELETE_STALE_NODES = """
            MATCH (node:KnowledgePoint {courseKey: $courseKey})
            WHERE node.managedBy = 'seed' AND NOT node.id IN $nodeIds
            DETACH DELETE node
            """;
    private static final String UPSERT_NODES = """
            UNWIND $nodes AS item
            MERGE (node:KnowledgePoint {id: item.id})
            SET node.courseKey = $courseKey,
                node.title = item.title,
                node.description = item.description,
                node.group = item.group,
                node.level = item.level,
                node.displayOrder = item.displayOrder,
                node.managedBy = 'seed'
            """;
    private static final String REPLACE_RELATIONSHIPS = """
            MATCH (:KnowledgePoint {courseKey: $courseKey})-[relation:PREREQUISITE {managedBy: 'seed'}]->
                  (:KnowledgePoint {courseKey: $courseKey})
            DELETE relation
            """;
    private static final String UPSERT_RELATIONSHIPS = """
            UNWIND $edges AS item
            MATCH (source:KnowledgePoint {id: item.source, courseKey: $courseKey})
            MATCH (target:KnowledgePoint {id: item.target, courseKey: $courseKey})
            MERGE (source)-[relation:PREREQUISITE]->(target)
            SET relation.managedBy = 'seed'
            """;
    private static final String LOAD_TOPOLOGY = """
            MATCH (node:KnowledgePoint {courseKey: $courseKey})
            OPTIONAL MATCH (source:KnowledgePoint {courseKey: $courseKey})-[:PREREQUISITE]->(node)
            RETURN node.id AS id,
                   node.title AS title,
                   node.description AS description,
                   node.group AS nodeGroup,
                   node.level AS level,
                   node.displayOrder AS displayOrder,
                   collect(source.id) AS prerequisites
            ORDER BY level, displayOrder, id
            """;

    private final Neo4jClient neo4jClient;
    private final Set<String> synchronizedCourses = ConcurrentHashMap.newKeySet();

    public Neo4jKnowledgeGraphStore(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public List<PythonKnowledgeGraphServiceImpl.CatalogNode> syncAndLoad(
            String courseKey,
            List<PythonKnowledgeGraphServiceImpl.CatalogNode> topology) {
        if (!synchronizedCourses.contains(courseKey)) {
            synchronized (this) {
                if (!synchronizedCourses.contains(courseKey)) {
                    synchronizeTopology(courseKey, topology);
                    synchronizedCourses.add(courseKey);
                }
            }
        }
        Collection<Map<String, Object>> rows = neo4jClient.query(LOAD_TOPOLOGY)
                .bind(courseKey).to("courseKey")
                .fetch()
                .all();
        return rows.stream().map(this::toCatalogNode).toList();
    }

    private void synchronizeTopology(
            String courseKey,
            List<PythonKnowledgeGraphServiceImpl.CatalogNode> topology) {
        List<Map<String, Object>> nodes = topology.stream().map(this::nodeParameters).toList();
        List<Map<String, Object>> edges = topology.stream()
                .flatMap(target -> target.prerequisites().stream()
                        .map(source -> Map.<String, Object>of("source", source, "target", target.id())))
                .toList();
        List<String> nodeIds = topology.stream()
                .map(PythonKnowledgeGraphServiceImpl.CatalogNode::id)
                .toList();

        neo4jClient.query(CREATE_CONSTRAINT).run();
        neo4jClient.query(DELETE_STALE_NODES)
                .bind(courseKey).to("courseKey")
                .bind(nodeIds).to("nodeIds")
                .run();
        neo4jClient.query(UPSERT_NODES)
                .bind(courseKey).to("courseKey")
                .bind(nodes).to("nodes")
                .run();
        neo4jClient.query(REPLACE_RELATIONSHIPS)
                .bind(courseKey).to("courseKey")
                .run();
        if (!edges.isEmpty()) {
            neo4jClient.query(UPSERT_RELATIONSHIPS)
                    .bind(courseKey).to("courseKey")
                    .bind(edges).to("edges")
                    .run();
        }
    }

    private Map<String, Object> nodeParameters(PythonKnowledgeGraphServiceImpl.CatalogNode node) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", node.id());
        values.put("title", node.title());
        values.put("description", node.description());
        values.put("group", node.group());
        values.put("level", node.level());
        values.put("displayOrder", node.order());
        return values;
    }

    private PythonKnowledgeGraphServiceImpl.CatalogNode toCatalogNode(Map<String, Object> row) {
        return new PythonKnowledgeGraphServiceImpl.CatalogNode(
                text(row.get("id")),
                text(row.get("title")),
                text(row.get("description")),
                text(row.get("nodeGroup")),
                number(row.get("level")),
                number(row.get("displayOrder")),
                stringList(row.get("prerequisites"))
        );
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(item -> item != null)
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
