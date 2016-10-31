/*
 * Copyright 2003-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbeddr.persistence.neo4j;

import jetbrains.mps.smodel.adapter.ids.MetaIdHelper;
import jetbrains.mps.util.IterableUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mps.openapi.language.SProperty;
import org.jetbrains.mps.openapi.model.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Minimalistic binary persistence, straightforward, to serialize nodes individually.
 * Extracted as part of binary persistence refactoring, with the latter becoming full-fledged
 * persistence mechanism
 * @author Artem Tikhomirov
 */
public class BareNodeWriter {
  static final int USER_NODE_POINTER = 0;
  static final int USER_STRING = 1;
  static final int USER_NULL = 2;
  static final int USER_NODE_ID = 3;
  static final int USER_MODEL_ID = 4;
  static final int USER_MODEL_REFERENCE = 5;
  static final int USER_SERIALIZABLE = 6;
  static final byte REF_THIS_MODEL = 17;
  static final byte REF_OTHER_MODEL = 18;

  protected final SModelReference myModelReference;

  public BareNodeWriter(@NotNull SModelReference modelReference) {
    myModelReference = modelReference;
  }

  public List<CypherStatement> writeRoots(Collection<SNode> nodes){
    ArrayList<CypherStatement> cypherRecords = new ArrayList<CypherStatement>();
    for (SNode n : nodes) {
      cypherRecords.addAll(writeNode(n));
      cypherRecords.add(new CreateRootRelation(n, myModelReference));
    }
    return cypherRecords;
  }

  public final List<CypherStatement> writeNode(SNode node) {
    ArrayList<CypherStatement> nodes = new ArrayList<>();
    CreateNode createNode = new CreateNode("SNode");

    createNode.addConcept("concept", node.getConcept());
    createNode.addNodeId(node.getNodeId());

    Collection<SProperty> propertyCollection = IterableUtil.asCollection(node.getProperties());
    for (SProperty prop : propertyCollection)
    {
      createNode.addString("Property_" + MetaIdHelper.getProperty(prop).serialize(), node.getProperty(prop));
    }
    nodes.add(createNode);
    Collection<SNode> childNodes = IterableUtil.asCollection(node.getChildren());
    for (SNode child : childNodes)
    {
      nodes.addAll(writeNode(child));
      nodes.add(new CreateChildReleation(node, child, child.getContainmentLink()));
    }

    for (SReference ref : node.getReferences()) {
      nodes.add(new CreateReference(ref));
    }

    return nodes;
  }



  protected boolean isKnownUserObject(Object object) {
    return object == null
        || object instanceof SNodeReference
        || object instanceof Serializable
        || object instanceof SNodeId
        || object instanceof SModelId
        || object instanceof SModelReference;
  }
}
