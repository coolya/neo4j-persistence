package com.mbeddr.persistence.neo4j;

import jetbrains.mps.project.ModuleId;
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper;
import jetbrains.mps.smodel.adapter.ids.SConceptId;
import org.jetbrains.mps.openapi.language.SConcept;
import org.jetbrains.mps.openapi.model.SNodeId;
import org.neo4j.driver.v1.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kdummann on 26/10/2016.
 */
public class CypherRecord {
    private String label;
    private final List<CypherAttribute> attributes = new ArrayList<CypherAttribute>();

    public CypherRecord() {
        this(null);
    }

    public CypherRecord(String label) {
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
        HashMap<String, String> map = new HashMap<String, String>();
        SConceptId id = MetaIdHelper.getConcept(value);
        map.put("langId", id.getLanguageId().getIdValue().toString());
        map.put("id", String.valueOf(id.getIdValue()));
        map.put("name", value.getQualifiedName());
        addMap(key, map);
    }

    public void addNodeId(String key, SNodeId nodeId) {
        if(nodeId instanceof jetbrains.mps.smodel.SNodeId.Regular)
        {
            addInt(key, ((jetbrains.mps.smodel.SNodeId.Regular) nodeId).getId());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public Statement toStatement() {
        return null;
    }
}
