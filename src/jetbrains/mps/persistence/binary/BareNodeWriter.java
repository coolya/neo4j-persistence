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
package jetbrains.mps.persistence.binary;

import jetbrains.mps.smodel.DynamicReference;
import jetbrains.mps.smodel.DynamicReference.DynamicReferenceOrigin;
import jetbrains.mps.smodel.StaticReference;
import jetbrains.mps.util.IterableUtil;
import jetbrains.mps.util.io.ModelOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mps.openapi.language.SProperty;
import org.jetbrains.mps.openapi.model.SModelId;
import org.jetbrains.mps.openapi.model.SModelReference;
import org.jetbrains.mps.openapi.model.SNode;
import org.jetbrains.mps.openapi.model.SNodeId;
import org.jetbrains.mps.openapi.model.SNodeReference;
import org.jetbrains.mps.openapi.model.SReference;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

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
  protected final ModelOutputStream myOut;

  public BareNodeWriter(@NotNull SModelReference modelReference, @NotNull ModelOutputStream os) {
    myModelReference = modelReference;
    myOut = os;
  }

  public void writeNodes(Collection<SNode> nodes) throws IOException {
    myOut.writeInt(nodes.size());
    for (SNode n : nodes) {
      writeNode(n);
    }
  }

  public final void writeNode(SNode node) throws IOException {
    writeNodePrim(node);

    myOut.writeByte('{');

    writeProperties(node);

    writeUserObjects(node);

    writeReferences(node);

    writeNodes(IterableUtil.asCollection(node.getChildren()));

    myOut.writeByte('}');
  }

  protected void writeNodePrim(SNode node) throws IOException {
    myOut.writeConcept(node.getConcept());
    myOut.writeNodeId(node.getNodeId());
    myOut.writeContainmentLink(node.getContainmentLink());
  }

  protected void writeReferences(SNode node) throws IOException {
    final Collection<SReference> refs = IterableUtil.asCollection(node.getReferences());
    myOut.writeShort(refs.size());
    for (SReference r : refs) {
      myOut.writeReferenceLink(r.getLink());
      writeReferenceTarget(r);
    }
  }

  protected void writeReferenceTarget(SReference reference) throws IOException {
    SModelReference targetModelReference = reference.getTargetSModelReference();
    if (reference instanceof StaticReference) {
      myOut.writeByte(1);
      myOut.writeNodeId(reference.getTargetNodeId());
    } else if (reference instanceof DynamicReference) {
      DynamicReferenceOrigin origin = ((DynamicReference) reference).getOrigin();
      if (origin != null) {
        myOut.writeByte(3);
        myOut.writeNodePointer(origin.getTemplate());
        myOut.writeNodePointer(origin.getInputNode());
      } else {
        myOut.writeByte(2);
      }
    } else {
      throw new IOException("cannot store reference: " + reference.toString());
    }
    if (targetModelReference != null && targetModelReference.equals(myModelReference)) {
      myOut.writeByte(REF_THIS_MODEL);
    } else {
      myOut.writeByte(REF_OTHER_MODEL);
      myOut.writeModelReference(targetModelReference);
    }
    // XXX Revisit ModelWriter9.genResolveInfo uses different approach to obtain resolveInfo
    myOut.writeString(((jetbrains.mps.smodel.SReference) reference).getResolveInfo());
  }

  protected void writeProperties(SNode node) throws IOException {
    final Collection<SProperty> props = IterableUtil.asCollection(node.getProperties());
    myOut.writeShort(props.size());
    for (SProperty p : props) {
      myOut.writeProperty(p);
      myOut.writeString(node.getProperty(p));
    }
  }


  protected void writeUserObjects(SNode node) throws IOException {
    final ArrayList<Object> knownUserObject = new ArrayList<Object>();
    for (Object key : node.getUserObjectKeys()) {
      Object value = node.getUserObject(key);
      if (isKnownUserObject(key) && isKnownUserObject(value)) {
        knownUserObject.add(key);
        knownUserObject.add(value);
      }
    }

    myOut.writeShort(knownUserObject.size());
    for (int i = 0; i < knownUserObject.size(); i += 2) {
      writeUserObject(knownUserObject.get(i));
      writeUserObject(knownUserObject.get(i + 1));
    }
  }

  protected void writeUserObject(Object object) throws IOException {
    if (object == null) {
      myOut.writeByte(USER_NULL);
    } else if (object instanceof SNodeReference) {
      myOut.writeByte(USER_NODE_POINTER);
      myOut.writeNodePointer((SNodeReference) object);
    } else if (object instanceof String) {
      myOut.writeByte(USER_STRING);
      myOut.writeString((String) object);
    } else if (object instanceof SNodeId) {
      myOut.writeByte(USER_NODE_ID);
      myOut.writeNodeId((SNodeId) object);
    } else if (object instanceof SModelId) {
      myOut.writeByte(USER_MODEL_ID);
      myOut.writeModelID((SModelId) object);
    } else if (object instanceof SModelReference) {
      myOut.writeByte(USER_MODEL_REFERENCE);
      myOut.writeModelReference((SModelReference) object);
    } else if (object instanceof Serializable) {
      // two-phase write
      try {
        ObjectOutputStream dummy = new ObjectOutputStream(new NullOutputStream());
        dummy.writeObject(object);
      } catch (NotSerializableException ignore) {
        object = null;
      }
      myOut.writeByte(USER_SERIALIZABLE);
      ObjectOutputStream objectOutput = new ObjectOutputStream(myOut);
      objectOutput.writeObject(object);
    }
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
