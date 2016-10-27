/*
 * Copyright 2003-2016 JetBrains s.r.o.
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

import jetbrains.mps.extapi.model.GeneratableSModel;
import jetbrains.mps.generator.ModelDigestUtil;
import jetbrains.mps.generator.ModelDigestUtil.DigestBuilderOutputStream;
import jetbrains.mps.persistence.IndexAwareModelFactory.Callback;
import jetbrains.mps.persistence.MetaModelInfoProvider;
import jetbrains.mps.persistence.MetaModelInfoProvider.BaseMetaModelInfo;
import jetbrains.mps.persistence.MetaModelInfoProvider.RegularMetaModelInfo;
import jetbrains.mps.persistence.MetaModelInfoProvider.StuffedMetaModelInfo;
import jetbrains.mps.persistence.registry.AggregationLinkInfo;
import jetbrains.mps.persistence.registry.AssociationLinkInfo;
import jetbrains.mps.persistence.registry.ConceptInfo;
import jetbrains.mps.persistence.registry.IdInfoRegistry;
import jetbrains.mps.persistence.registry.LangInfo;
import jetbrains.mps.persistence.registry.PropertyInfo;
import jetbrains.mps.project.ModuleId;
import jetbrains.mps.smodel.DefaultSModel;
import jetbrains.mps.smodel.SModel;
import jetbrains.mps.smodel.SModelHeader;
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper;
import jetbrains.mps.smodel.adapter.ids.SLanguageId;
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory;
import jetbrains.mps.smodel.loading.ModelLoadResult;
import jetbrains.mps.smodel.loading.ModelLoadingState;
import jetbrains.mps.smodel.persistence.def.ModelReadException;
import jetbrains.mps.smodel.persistence.def.v9.IdInfoCollector;
import jetbrains.mps.util.FileUtil;
import jetbrains.mps.util.IterableUtil;
import jetbrains.mps.util.NameUtil;
import jetbrains.mps.util.io.ModelInputStream;
import jetbrains.mps.util.io.ModelOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.mps.openapi.language.SLanguage;
import org.jetbrains.mps.openapi.model.SModelId;
import org.jetbrains.mps.openapi.model.SModelReference;
import org.jetbrains.mps.openapi.model.SNode;
import org.jetbrains.mps.openapi.model.SNodeId;
import org.jetbrains.mps.openapi.module.SModuleId;
import org.jetbrains.mps.openapi.module.SModuleReference;
import org.jetbrains.mps.openapi.persistence.StreamDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static jetbrains.mps.smodel.SModel.ImportElement;

/**
 * @author evgeny, 11/21/12
 * @author Artem Tikhomirov
 */
public final class Neo4JPersistence {

  private final MetaModelInfoProvider myMetaInfoProvider;
  private final SModel myModelData;

  public static SModelHeader readHeader(@NotNull StreamDataSource source) throws ModelReadException {
    ModelInputStream mis = null;
    try {
      mis = new ModelInputStream(source.openInputStream());
      return loadHeader(mis);
    } catch (IOException e) {
      throw new ModelReadException("Couldn't read model: " + e.getMessage(), e);
    } finally {
      FileUtil.closeFileSafe(mis);
    }
  }

  public static ModelLoadResult readModel(@NotNull SModelHeader header, @NotNull StreamDataSource source, boolean interfaceOnly) throws ModelReadException {
    final SModelReference desiredModelRef = header.getModelReference();
    try {
      ModelLoadResult rv = loadModel(source.openInputStream(), interfaceOnly, header.getMetaInfoProvider());
      SModelReference actualModelRef = rv.getModel().getReference();
      if (!actualModelRef.equals(desiredModelRef)) {
        throw new ModelReadException(String.format("Intended to read model %s, actually read %s", desiredModelRef, actualModelRef), null, actualModelRef);
      }
      return rv;
    } catch (IOException e) {
      throw new ModelReadException("Couldn't read model: " + e.toString(), e, desiredModelRef);
    }
  }

  public static void writeModel(@NotNull SModel model, @NotNull StreamDataSource dataSource) throws IOException {
    if (dataSource.isReadOnly()) {
      throw new IOException(String.format("`%s' is read-only", dataSource.getLocation()));
    }
    writeModel(model);
  }
  public static void writeModel(@NotNull SModel model) throws IOException {
      saveModel(model);
  }

  public static Map<String, String> getDigestMap(jetbrains.mps.smodel.SModel model, @Nullable MetaModelInfoProvider mmiProvider) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    IdInfoRegistry meta = null;
    DigestBuilderOutputStream os = ModelDigestUtil.createDigestBuilderOutputStream();
    try {
      Neo4JPersistence bp = new Neo4JPersistence(mmiProvider == null ? new RegularMetaModelInfo(model.getReference()) : mmiProvider, model);
      ModelOutputStream mos = new ModelOutputStream(os);
      meta = bp.saveModelProperties(mos);
      mos.flush();
    } catch (IOException ignored) {
      assert false;
      /* should never happen */
    }
    result.put(GeneratableSModel.HEADER, os.getResult());

    assert meta != null;
    // In fact, would be better to translate index attribute of any XXXInfo element into
    // a value not related to meta-element position in the registry. Otherwise, almost any change
    // in a model (e.g. addition of a new root or new property value) might affect all other root hashes
    // as the index of meta-model elements might change. However, as long as our binary models are not exposed
    // for user editing, we don't care.

    for (SNode node : model.getRootNodes()) {
      os = ModelDigestUtil.createDigestBuilderOutputStream();
      try {
        ModelOutputStream mos = new ModelOutputStream(os);
        new NodesWriter(model.getReference(), mos, meta).writeNode(node);
        mos.flush();
      } catch (IOException ignored) {
        assert false;
        /* should never happen */
      }
      SNodeId nodeId = node.getNodeId();
      if (nodeId != null) {
        result.put(nodeId.toString(), os.getResult());
      }
    }

    return result;
  }


  private static final int HEADER_START   = 0x91ABABA9;
  private static final int STREAM_ID_V1   = 0x00000300;
  private static final int STREAM_ID_V2   = 0x00000400;
  private static final int STREAM_ID      = STREAM_ID_V2;
  private static final byte HEADER_ATTRIBUTES = 0x7e;
  private static final int HEADER_END     = 0xabababab;
  private static final int MODEL_START    = 0xbabababa;
  private static final int REGISTRY_START = 0x5a5a5a5a;
  private static final int REGISTRY_END   = 0xa5a5a5a5;
  private static final byte STUB_NONE     = 0x12;
  private static final byte STUB_ID       = 0x13;



  @NotNull
  private static SModelHeader loadHeader(ModelInputStream is) throws IOException {
    if (is.readInt() != HEADER_START) {
      throw new IOException("bad stream, no header");
    }

    int streamId = is.readInt();
    if (streamId == STREAM_ID_V1) {
      throw new IOException(String.format("Can't read old binary persistence version (%x), please re-save models", streamId));
    }
    if (streamId != STREAM_ID) {
      throw new IOException(String.format("bad stream, unknown version: %x", streamId));
    }

    SModelReference modelRef = is.readModelReference();
    SModelHeader result = new SModelHeader();
    result.setModelReference(modelRef);
    is.readInt(); //left for compatibility: old version was here
    is.mark(4);
    if (is.readByte() == HEADER_ATTRIBUTES) {
      result.setDoNotGenerate(is.readBoolean());
      int propsCount = is.readShort();
      for (; propsCount > 0; propsCount--) {
        String key = is.readString();
        String value = is.readString();
        result.setOptionalProperty(key, value);
      }
    } else {
      is.reset();
    }
    assertSyncToken(is, HEADER_END);
    return result;
  }
  @NotNull
  private static ModelLoadResult loadModel(InputStream is, boolean interfaceOnly, @Nullable MetaModelInfoProvider mmiProvider) throws IOException {
    ModelInputStream mis = null;
    try {
      mis = new ModelInputStream(is);
      SModelHeader modelHeader = loadHeader(mis);

      DefaultSModel model = new DefaultSModel(modelHeader.getModelReference(), modelHeader);
      Neo4JPersistence bp = new Neo4JPersistence(mmiProvider == null ? new RegularMetaModelInfo(modelHeader.getModelReference()) : mmiProvider, model);


      NodesReader reader = new NodesReader(modelHeader.getModelReference(), mis);
      reader.readNodesInto(model);
      return new ModelLoadResult((SModel) model, reader.hasSkippedNodes() ? ModelLoadingState.INTERFACE_LOADED : ModelLoadingState.FULLY_LOADED);
    } finally {
      FileUtil.closeFileSafe(mis);
    }
  }

  private static void saveModel(SModel model) throws IOException {
    final MetaModelInfoProvider mmiProvider;
    if (model instanceof DefaultSModel && ((DefaultSModel) model).getSModelHeader().getMetaInfoProvider() != null) {
      mmiProvider = ((DefaultSModel) model).getSModelHeader().getMetaInfoProvider();
    } else {
      mmiProvider = new RegularMetaModelInfo(model.getReference());
    }
    Neo4JPersistence bp = new Neo4JPersistence(mmiProvider, model);
    CypherRecord record = bp.saveModelProperties();

    Collection<SNode> roots = IterableUtil.asCollection(model.getRootNodes());
    new NodesWriter(model.getReference()).writeNodes(roots);
  }

  private Neo4JPersistence(@NotNull MetaModelInfoProvider mmiProvider, SModel modelData) {
    myMetaInfoProvider = mmiProvider;
    myModelData = modelData;
  }


  private CypherRecord saveModelProperties() throws IOException {
    // header
    CypherRecord record = new CypherRecord("SModel");


    SModelReference reference = myModelData.getReference();

    writeModelReference(record, reference);
    if (myModelData instanceof DefaultSModel) {
      SModelHeader mh = ((DefaultSModel) myModelData).getSModelHeader();
      record.addBoolean("DoNotGenerate", mh.isDoNotGenerate());
      Map<String, String> props = new HashMap<String, String>(mh.getOptionalProperties());
      record.addMap("props", props);
    }

    return record;
  }

  private void writeModelReference(CypherRecord record, SModelReference reference) {
    record.addString("Name",reference.getModelName());
    SModelId modelId = reference.getModelId();
    if(modelId instanceof jetbrains.mps.smodel.SModelId.RegularSModelId) {
      record.addString("Id", ((jetbrains.mps.smodel.SModelId.RegularSModelId) modelId).getId().toString());
    } else {
      throw new UnsupportedOperationException("Can't serialise Ids of type " + modelId.getClass().getCanonicalName());
    }
    SModuleReference moduleReference = reference.getModuleReference();
    record.addString("ModuleName", moduleReference.getModuleName());
    SModuleId moduleId = moduleReference.getModuleId();
    if(moduleId instanceof ModuleId.Regular) {
      record.addString("ModuleId", ((ModuleId.Regular) moduleId).getUUID().toString());
    } else {
      throw new UnsupportedOperationException("Can't save id of type " + moduleId.getClass().getCanonicalName());
    }
  }

  public static void index(InputStream content, final Callback consumer) throws IOException {
    ModelInputStream mis = null;
    try {
      mis = new ModelInputStream(content);
      SModelHeader modelHeader = loadHeader(mis);
      SModel model = new DefaultSModel(modelHeader.getModelReference(), modelHeader);
      Neo4JPersistence bp = new Neo4JPersistence(new StuffedMetaModelInfo(new BaseMetaModelInfo()), model);
      final NodesReader reader = new NodesReader(modelHeader.getModelReference(), mis);
      HashSet<SNodeId> externalNodes = new HashSet<SNodeId>();
      HashSet<SNodeId> localNodes = new HashSet<SNodeId>();
      reader.collectExternalTargets(externalNodes);
      reader.collectLocalTargets(localNodes);
      reader.readChildren(null);
      for (SNodeId n : externalNodes) {
        consumer.externalNodeRef(n);
      }
      for (SNodeId n : localNodes) {
        consumer.localNodeRef(n);
      }
    } finally {
      FileUtil.closeFileSafe(mis);
    }
  }

  private static void assertSyncToken(ModelInputStream is, int token) throws IOException {
    if (is.readInt() != token) {
      throw new IOException("bad stream, no sync token");
    }
  }
}
