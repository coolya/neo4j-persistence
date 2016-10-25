package com.mbeddr.modelroot.neo4j;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.mps.openapi.persistence.ModelRoot;
import org.jetbrains.mps.openapi.ui.persistence.ModelRootEntry;
import org.jetbrains.mps.openapi.ui.persistence.ModelRootEntryEditor;
import org.jetbrains.mps.openapi.ui.persistence.ModelRootEntryExt;

import javax.swing.*;
import java.awt.*;

/**
 * Created by kdummann on 25/10/2016.
 */
public class Neo4JModelRootEntry implements ModelRootEntry, ModelRootEntryExt {
    private final Neo4JModelRoot myModelRoot;
    public Neo4JModelRootEntry(ModelRoot modelRoot) {
        if(!(modelRoot instanceof Neo4JModelRoot)) {
            throw new ClassCastException("Can't convert " + modelRoot.getClass().getCanonicalName() + " to a " + Neo4JModelRoot.class.getCanonicalName());
        }
        this.myModelRoot = (Neo4JModelRoot) modelRoot;
    }

    @Override
    public ModelRoot getModelRoot() {
        return myModelRoot;
    }

    @Override
    public String getDetailsText() {
        return "Neo4J Modelroot";
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public ModelRootEntryEditor getEditor() {
        return null;
    }

    @Override
    public void addModelRootEntryListener(ModelRootEntryListener modelRootEntryListener) {

    }

    @Override
    public void dispose() {

    }

    @Nullable
    @Override
    public JComponent getDetailsComponent() {
        return null;
    }

    @Override
    public void setForegroundColor(Color color) {

    }

    @Override
    public void resetForegroundColor() {

    }
}
