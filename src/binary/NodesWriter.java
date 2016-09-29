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

import jetbrains.mps.persistence.registry.ConceptInfo;
import jetbrains.mps.persistence.registry.IdInfoRegistry;
import jetbrains.mps.persistence.registry.PropertyInfo;
import jetbrains.mps.util.IterableUtil;
import jetbrains.mps.util.io.ModelOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mps.openapi.language.SContainmentLink;
import org.jetbrains.mps.openapi.language.SProperty;
import org.jetbrains.mps.openapi.model.SModelReference;
import org.jetbrains.mps.openapi.model.SNode;
import org.jetbrains.mps.openapi.model.SReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

public final class NodesWriter extends BareNodeWriter {
  private final IdInfoRegistry myInfo;

  public NodesWriter(@NotNull SModelReference modelReference, @NotNull ModelOutputStream os, @NotNull IdInfoRegistry idInfo) {
    super(modelReference, os);
    myInfo = idInfo;
  }

  @Override
  protected void writeNodePrim(SNode node) throws IOException {
    ConceptInfo conceptInfo = myInfo.find(node.getConcept());
    myOut.writeShort(conceptInfo.getIntIndex());
    myOut.writeNodeId(node.getNodeId());
    SContainmentLink roleInParent = node.getContainmentLink();
    myOut.writeShort(roleInParent == null ? -1 : myInfo.find(roleInParent).getIntIndex());
  }

  protected void writeReferences(SNode node) throws IOException {
    Collection<? extends SReference> refs = IterableUtil.asCollection(node.getReferences());
    myOut.writeShort(refs.size());
    for (SReference reference : refs) {
      myOut.writeShort(myInfo.find(reference.getLink()).getIntIndex());
      writeReferenceTarget(reference);
    }
  }

  @Override
  protected void writeProperties(SNode node) throws IOException {
    final ArrayList<PropertyInfo> propertyInfo = new ArrayList<PropertyInfo>();
    final ArrayList<String> propertyValue = new ArrayList<String>();
    for (SProperty id : node.getProperties()) {
      propertyInfo.add(myInfo.find(id));
      propertyValue.add(node.getProperty(id));
    }
    myOut.writeShort(propertyInfo.size());
    for (int i = 0, x = propertyInfo.size(); i < x; i++) {
      myOut.writeShort(propertyInfo.get(i).getIntIndex());
      myOut.writeString(propertyValue.get(i));
    }
  }
}
