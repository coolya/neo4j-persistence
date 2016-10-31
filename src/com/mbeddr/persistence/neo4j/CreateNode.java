package com.mbeddr.persistence.neo4j;

import jetbrains.mps.smodel.adapter.ids.MetaIdHelper;
import jetbrains.mps.smodel.adapter.ids.SConceptId;
import org.jetbrains.mps.openapi.language.SConcept;
import org.jetbrains.mps.openapi.model.SNodeId;
import org.neo4j.driver.v1.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by kdummann on 26/10/2016.
 */
public class CreateNode implements CypherStatement{
    private String label;
    private final List<CypherAttribute> attributes = new ArrayList<CypherAttribute>();

    public CreateNode() {
        this(null);
    }

    public CreateNode(String label) {
        this.label = label;
    }

    public void addInt(String key, long value) {
        this.attributes.add(new CypherAttribute(key, value, CypherAttribute.Type.INT));
    }
    public void addString(String key, String value) {
        this.attributes.add(new CypherAttribute(key, value, CypherAttribute.Type.STRING));
    }
    public void addFloat(String key, double value) {
        this.attributes.add(new CypherAttribute(key, value, CypherAttribute.Type.FLOAT));
    }
    public void addBoolean(String key, boolean value) {
        this.attributes.add(new CypherAttribute(key, value, CypherAttribute.Type.BOOL));
    }
    public void addMap(String key, Map<String, ? extends Object> value) {
        this.attributes.add(new CypherAttribute(key, value, CypherAttribute.Type.MAP));
    }

    public void addConcept(String key, SConcept value) {
        SConceptId id = MetaIdHelper.getConcept(value);
        addString(key, id.serialize());
    }

    public void addNodeId(SNodeId nodeId) {
        if(nodeId instanceof jetbrains.mps.smodel.SNodeId.Regular)
        {
            addInt("NodeId", ((jetbrains.mps.smodel.SNodeId.Regular) nodeId).getId());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public Statement toStatement() {
        StringBuilder builder = new StringBuilder();

        builder.append("CREATE (e");
        if(this.label != null && !this.label.isEmpty()) {
            builder.append(":" + label + " ");
        }
        builder.append("{ ");

        boolean first = true;
        for (CypherAttribute attr : attributes) {
            if(!first) {
                builder.append(",");
            }
            builder.append("`");
            builder.append(attr.getKey());
            builder.append("`");
            builder.append(":");
            builder.append("{`");
            builder.append(attr.getKey());
            builder.append("`}");
            first = false;
        }

        builder.append("})");

        return new Statement(builder.toString(), this.attributes
                .stream()
                .collect(Collectors.toMap(x ->  x.getKey(), CypherAttribute::getValue)));
    }
}
