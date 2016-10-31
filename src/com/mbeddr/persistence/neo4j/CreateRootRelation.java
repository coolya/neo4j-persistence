package com.mbeddr.persistence.neo4j;

import org.jetbrains.mps.openapi.model.SModelReference;
import org.jetbrains.mps.openapi.model.SNode;
import org.neo4j.driver.v1.Statement;

import java.util.HashMap;

/**
 * Created by kdummann on 27/10/2016.
 */
public class CreateRootRelation implements CypherStatement {

    private SNode root;
    private SModelReference modelReference;

    public CreateRootRelation(SNode root, SModelReference modelReference) {

        this.root = root;
        this.modelReference = modelReference;
    }

    @Override
    public Statement toStatement() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("idRoot", NodeUtils.getNodeId(root.getNodeId()));
        values.put("idModel", NodeUtils.getModelId(modelReference.getModelId()));

        return new Statement(
                "MATCH (a:SModel),(b:SNode) WHERE a.Id = {idModel} AND b.NodeId = {idRoot} CREATE (a)-[r:ROOT]->(b)",
                values);
    }
}
