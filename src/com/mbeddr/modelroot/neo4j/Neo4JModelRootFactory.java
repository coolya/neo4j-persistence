package com.mbeddr.modelroot.neo4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.mps.openapi.persistence.ModelRoot;
import org.jetbrains.mps.openapi.persistence.ModelRootFactory;

/**
 * Created by kdummann on 25/10/2016.
 */
public class Neo4JModelRootFactory implements ModelRootFactory {
    @NotNull
    @Override
    public ModelRoot create() {
        return new Neo4JModelRoot();
    }
}
