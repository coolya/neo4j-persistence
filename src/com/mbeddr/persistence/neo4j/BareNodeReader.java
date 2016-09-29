/*
 * Copyright 2003-2015 JetBrains s.r.o.
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

import jetbrains.mps.extapi.model.SModelData;
import jetbrains.mps.smodel.DynamicReference;
import jetbrains.mps.smodel.DynamicReference.DynamicReferenceOrigin;
import jetbrains.mps.smodel.StaticReference;
import jetbrains.mps.util.io.ModelInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.mps.openapi.language.SConcept;
import org.jetbrains.mps.openapi.language.SContainmentLink;
import org.jetbrains.mps.openapi.language.SProperty;
import org.jetbrains.mps.openapi.language.SReferenceLink;
import org.jetbrains.mps.openapi.model.SModelReference;
import org.jetbrains.mps.openapi.model.SNode;
import org.jetbrains.mps.openapi.model.SNodeId;
import org.jetbrains.mps.openapi.model.SReference;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, straightforward binary serialization of individual {@link org.jetbrains.mps.openapi.model.SNode}s.
 * @see BareNodeWriter
 * @author Artem Tikhomirov
 */
public class BareNodeReader {
  protected final SModelReference myModelReference;
  protected final ModelInputStream myIn;

  public BareNodeReader(@NotNull SModelReference modelReference, @NotNull ModelInputStream is) {
    myModelReference = modelReference;
    myIn = is;
  }

  /**
   * Read nodes and register them as roots into supplied ModelData
   */
  public void readNodesInto(SModelData modelData) throws IOException {
    for (SNode r : readChildren(null)) {
      modelData.addRootNode(r);
    }
  }

  /**
   * Read nodes and register them as children into supplied parent SNode (if any)
   * @return list of nodes read
   */
  public List<SNode> readChildren(@Nullable SNode parent) throws IOException {
    int size = myIn.readInt();
    ArrayList<SNode> rv = new ArrayList<SNode>(size);
    while (size-- > 0) {
      rv.add(readNode(parent));
    }
    return rv;
  }

  /**
   * Read a single node and register it with optional parent
   */
  public final SNode readNode(@Nullable SNode parent) throws IOException {

    SNode node = instantiate(parent);

    if (myIn.readByte() != '{') {
      throw new IOException("bad stream, no '{'");
    }

    readProperties(node);

    readUserObjects(node);

    readReferences(node);

    readChildren(node);

    if (myIn.readByte() != '}') {
      throw new IOException("bad stream, no '}'");
    }
    return node;
  }

  protected SNode instantiate(@Nullable SNode parent) throws IOException {
    SConcept c = myIn.readConcept();
    SNodeId nid = myIn.readNodeId();
    SContainmentLink link = myIn.readContainmentLink();
    jetbrains.mps.smodel.SNode node = new jetbrains.mps.smodel.SNode(c, nid);
    if (parent != null && link != null) {
      parent.addChild(link, node);
    }
    return node;
  }

  protected void readProperties(SNode node) throws IOException {
    int properties = myIn.readShort();
    while (properties-- > 0) {
      final SProperty property = myIn.readProperty();
      final String value = myIn.readString();
      node.setProperty(property, value);
    }
  }
  protected void readReferences(SNode node) throws IOException {
    int refs = myIn.readShort();
    while (refs-- > 0) {
      readReference(myIn.readReferenceLink(), node);
    }
  }

  protected SReference readReference(SReferenceLink sref, SNode node) throws IOException {
    int kind = myIn.readByte();
    assert kind >= 1 && kind <= 3;
    SNodeId targetNodeId = kind == 1 ? myIn.readNodeId() : null;
    DynamicReferenceOrigin origin = kind == 3 ? new DynamicReferenceOrigin(myIn.readNodePointer(), myIn.readNodePointer()) : null;
    int targetModelKind = myIn.readByte();
    assert targetModelKind == BareNodeWriter.REF_OTHER_MODEL || targetModelKind == BareNodeWriter.REF_THIS_MODEL;
    final SModelReference modelRef;
    if (targetModelKind == BareNodeWriter.REF_OTHER_MODEL) {
      modelRef = myIn.readModelReference();
      externalNodeReferenceRead(modelRef, targetNodeId);
    } else {
      modelRef = myModelReference;
      localNodeReferenceRead(targetNodeId);
    }
    String resolveInfo = myIn.readString();
    if (kind == 1) {
      SReference reference = new StaticReference(
          sref,
          node,
          modelRef,
          targetNodeId,
          resolveInfo);
      node.setReference(reference.getLink(), reference);
      return reference;
    } else if (kind == 2 || kind == 3) {
      DynamicReference reference = new DynamicReference(
          sref,
          node,
          modelRef,
          resolveInfo);
      if (origin != null) {
        reference.setOrigin(origin);
      }
      node.setReference(sref, reference);
      return reference;
    } else {
      throw new IOException("unknown reference type");
    }
  }

  protected void localNodeReferenceRead(SNodeId nodeId) {
    // no-op, left for subclasses  to override
  }
  protected void externalNodeReferenceRead(SModelReference targetModel, SNodeId nodeId) {
    // no-op, left for subclasses  to override
  }

  protected void readUserObjects(SNode node) throws IOException {
    int userObjectCount = myIn.readShort();
    for (int i = 0; i < userObjectCount; i += 2) {
      Object key = readUserObject();
      Object value = readUserObject();
      if (key != null && value != null) {
        node.putUserObject(key, value);
      }
    }
  }

  private Object readUserObject() throws IOException {
    int id = myIn.readByte();
    switch (id) {
      case BareNodeWriter.USER_NODE_POINTER:
        return myIn.readNodePointer();
      case BareNodeWriter.USER_STRING:
        return myIn.readString();
      case BareNodeWriter.USER_NULL:
        return null;
      case BareNodeWriter.USER_NODE_ID:
        return myIn.readNodeId();
      case BareNodeWriter.USER_MODEL_ID:
        return myIn.readModelID();
      case BareNodeWriter.USER_MODEL_REFERENCE:
        return myIn.readModelReference();
      case BareNodeWriter.USER_SERIALIZABLE:
        ObjectInputStream stream = new ObjectInputStream(myIn);
        try {
          return stream.readObject();
        } catch (ClassNotFoundException ignore) {
          // class could be loaded by the other classloader
          return null;
        }
    }
    throw new IOException("Could not read user object");
  }
}
