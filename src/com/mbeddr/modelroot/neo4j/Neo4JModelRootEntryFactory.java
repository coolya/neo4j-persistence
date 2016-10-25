package com.mbeddr.modelroot.neo4j;

import org.jetbrains.mps.openapi.persistence.ModelRoot;
import org.jetbrains.mps.openapi.ui.persistence.ModelRootEntry;
import org.jetbrains.mps.openapi.ui.persistence.ModelRootEntryFactory;

/**
 * Created by kdummann on 25/10/2016.
 */
public class Neo4JModelRootEntryFactory implements ModelRootEntryFactory {
    @Override
    public ModelRootEntry getModelRootEntry(ModelRoot modelRoot) {
        return new Neo4JModelRootEntry(modelRoot);
    }
}
