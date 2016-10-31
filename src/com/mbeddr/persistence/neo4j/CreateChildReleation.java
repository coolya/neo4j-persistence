package com.mbeddr.persistence.neo4j;


import jetbrains.mps.smodel.adapter.ids.MetaIdHelper;
import org.jetbrains.mps.openapi.language.SContainmentLink;
import org.jetbrains.mps.openapi.model.SNode;
import org.neo4j.driver.v1.Statement;

import java.util.HashMap;

/**
 * Created by kdummann on 27/10/2016.
 */
public class CreateChildReleation implements CypherStatement {

    private final SNode parent;
    private final SNode child;
    private final SContainmentLink link;

    public CreateChildReleation(SNode parent, SNode child, SContainmentLink link) {
        this.parent = parent;
        this.child = child;
        this.link = link;
    }



    @Override
    public Statement toStatement() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("ida", NodeUtils.getNodeId(parent.getNodeId()));
        values.put("idb", NodeUtils.getNodeId(child.getNodeId()));
        values.put("idLink", MetaIdHelper.getAggregation(link).serialize());


        return new Statement(
                "MATCH (a:SNode),(b:SNode) WHERE a.NodeId = {ida} AND b.NodeId = {idb} CREATE (a)-[r:CONTAINMENT {Id : {idLink}}]->(b)",
                values);
    }
}
