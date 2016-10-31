package com.mbeddr.persistence.neo4j;

import org.neo4j.driver.v1.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by kdummann on 27/10/2016.
 */
public class BoltCypherExecutor {
    private final org.neo4j.driver.v1.Driver driver;

    public BoltCypherExecutor(String url) {
        this(url, null, null);
    }

    public BoltCypherExecutor(String url, String username, String password) {
        boolean hasPassword = password != null && !password.isEmpty();
        AuthToken token = hasPassword ? AuthTokens.basic(username, password) : AuthTokens.none();
        driver = GraphDatabase.driver(url, token, Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig());
    }

    public Iterator<Map<String, Object>> query(String query, Map<String, Object> params) {
        try (Session session = driver.session()) {
            List<Map<String, Object>> list = session.run(query, params)
                    .list( r -> r.asMap(BoltCypherExecutor::convert));
            return list.iterator();
        }
    }

    public void excec(Iterable<Statement> statements) {
        try(Session session = driver.session()) {
            Transaction transaction = session.beginTransaction();
            for (Statement statement : statements) {
                transaction.run(statement);
            }
            transaction.success();
            transaction.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Iterator<Map<String, Object>> query(Statement query) {
        try (Session session = driver.session()) {
            List<Map<String, Object>> list = session.run(query)
                    .list( r -> r.asMap(BoltCypherExecutor::convert));
            return list.iterator();
        }
    }

    static Object convert(Value value) {
        switch (value.type().name()) {
            case "PATH":
                return value.asList(BoltCypherExecutor::convert);
            case "NODE":
            case "RELATIONSHIP":
                return value.asMap();
        }
        return value.asObject();
    }
}
