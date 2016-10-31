package com.mbeddr.persistence.neo4j;

/**
 * Created by kdummann on 26/10/2016.
 */
public class CypherAttribute {
    private final String key;
    private final Object value;
    private final Type type;

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public enum Type {
        STRING,
        INT,
        FLOAT,
        BOOL,
        MAP
    }

    public CypherAttribute(String key, Object value, Type type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }
}
