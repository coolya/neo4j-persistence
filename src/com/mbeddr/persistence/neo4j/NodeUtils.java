package com.mbeddr.persistence.neo4j;

import org.jetbrains.mps.openapi.model.SModelId;
import org.jetbrains.mps.openapi.model.SNodeId;

/**
 * Created by kdummann on 27/10/2016.
 */
public class NodeUtils {
    public static Object getNodeId(SNodeId id) {
        if (id instanceof jetbrains.mps.smodel.SNodeId.Regular) {
            return ((jetbrains.mps.smodel.SNodeId.Regular) id).getId();
        } else if (id instanceof jetbrains.mps.smodel.SNodeId.Foreign) {
            return ((jetbrains.mps.smodel.SNodeId.Foreign) id).getId();
        } else {
            throw new UnsupportedOperationException();
        }
    }
    public static Object getModelId(SModelId id) {
        if(id instanceof jetbrains.mps.smodel.SModelId.RegularSModelId) {
            return ((jetbrains.mps.smodel.SModelId.RegularSModelId) id).getId().toString();
        } else if(id instanceof jetbrains.mps.smodel.SModelId.ForeignSModelId) {
            return "Foreign_" + ((jetbrains.mps.smodel.SModelId.ForeignSModelId) id).getId();
        } else {
            return "String_" + id.toString();
        }
    }
}
