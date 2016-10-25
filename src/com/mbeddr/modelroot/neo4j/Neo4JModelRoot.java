package com.mbeddr.modelroot.neo4j;

import jetbrains.mps.extapi.persistence.ModelRootBase;
import org.jetbrains.mps.openapi.model.SModel;
import org.jetbrains.mps.openapi.model.SModelId;
import org.jetbrains.mps.openapi.module.SModule;
import org.jetbrains.mps.openapi.persistence.Memento;
import org.jetbrains.mps.openapi.persistence.ModelRoot;

/**
 * Created by kdummann on 25/10/2016.
 */
public class Neo4JModelRoot extends ModelRootBase {
    @Override
    public Iterable<SModel> loadModels() {
        return null;
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getPresentation() {
        return null;
    }

    @Override
    public SModel getModel(SModelId id) {
        return null;
    }

    @Override
    public boolean canCreateModel(String modelName) {
        return false;
    }

    @Override
    public SModel createModel(String modelName) {
        return null;
    }

    @Override
    public void save(Memento memento) {

    }

    @Override
    public void load(Memento memento) {

    }
}
