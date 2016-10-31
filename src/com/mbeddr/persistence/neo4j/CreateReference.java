package com.mbeddr.persistence.neo4j;

import jetbrains.mps.smodel.StaticReference;
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper;
import org.jetbrains.mps.openapi.language.SReferenceLink;
import org.jetbrains.mps.openapi.model.SNode;
import org.jetbrains.mps.openapi.model.SReference;
import org.neo4j.driver.v1.Statement;

import java.util.HashMap;

/**
 * Created by kdummann on 27/10/2016.
 */
public class CreateReference implements CypherStatement {

    private final SReference ref;

    public CreateReference(SReference ref) {
        this.ref = ref;
    }

    @Override
    public boolean needsNodesToBeCreated() {
        return true;
    }

    @Override
    public Statement toStatement() {
        if(ref instanceof StaticReference) {
            StaticReference staticReference = (StaticReference) this.ref;
            if (staticReference.getSourceNode().getModel() == staticReference.getTargetSModel()) {
                HashMap<String, Object> values = new HashMap<>();
                values.put("idA", NodeUtils.getNodeId(staticReference.getSourceNode().getNodeId()));
                values.put("idB", NodeUtils.getNodeId(staticReference.getTargetNode().getNodeId()));
                values.put("idLink", MetaIdHelper.getAssociation(staticReference.getLink()).serialize());
                return new Statement(
                            "MATCH (a:SNode),(b:SNode) WHERE a.NodeId = {idA} AND b.NodeId = {idB} CREATE (a)-[r:REFERENCE {Id : {idLink}}]->(b)",
                        values);
                //same model we can create the reference to that node here
            } else if (staticReference.getSourceNode().getModel().getModelRoot() == staticReference.getTargetSModel().getModelRoot()) {
                // todo: then custom model root is implemented we can set the reference directly because its in the same
                // database.
                throw new UnsupportedOperationException();
            } else {
                HashMap<String, Object> values = new HashMap<>();
                values.put("idSource", NodeUtils.getNodeId(staticReference.getSourceNode().getNodeId()));
                values.put("idTarget", NodeUtils.getNodeId(staticReference.getTargetNode().getNodeId()));
                values.put("modelId", NodeUtils.getModelId(staticReference.getTargetSModel().getModelId()));
                values.put("idLink", MetaIdHelper.getAssociation(staticReference.getLink()).serialize());
                return new Statement("MATCH (source:SNode) WHERE source.NodeId = {idSource} CREATE (source)-[r:REFERENCE { Id : {idLink}}]-> (proxy:SReferenceProxy {ModelId: {modelId}, NodeId: {idTarget}})"
                                    , values);
            }

        } else {
            throw new UnsupportedOperationException();
        }
    }
}
