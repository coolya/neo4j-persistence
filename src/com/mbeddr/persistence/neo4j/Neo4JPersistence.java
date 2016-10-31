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

import jetbrains.mps.lang.smodel.generator.smodelAdapter.SModelOperations;
import jetbrains.mps.persistence.IndexAwareModelFactory.Callback;
import jetbrains.mps.persistence.MetaModelInfoProvider;
import jetbrains.mps.persistence.MetaModelInfoProvider.BaseMetaModelInfo;
import jetbrains.mps.persistence.MetaModelInfoProvider.RegularMetaModelInfo;
import jetbrains.mps.persistence.MetaModelInfoProvider.StuffedMetaModelInfo;
import jetbrains.mps.project.ModuleId;
import jetbrains.mps.smodel.DefaultSModel;
import jetbrains.mps.smodel.SModel;
import jetbrains.mps.smodel.SModelHeader;
import jetbrains.mps.smodel.loading.ModelLoadResult;
import jetbrains.mps.smodel.loading.ModelLoadingState;
import jetbrains.mps.smodel.persistence.def.ModelReadException;
import jetbrains.mps.util.FileUtil;
import jetbrains.mps.util.IterableUtil;
import jetbrains.mps.util.io.ModelInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.mps.openapi.model.SModelId;
import org.jetbrains.mps.openapi.model.SModelReference;
import org.jetbrains.mps.openapi.model.SNode;
import org.jetbrains.mps.openapi.model.SNodeId;
import org.jetbrains.mps.openapi.module.SModuleId;
import org.jetbrains.mps.openapi.module.SModuleReference;
import org.jetbrains.mps.openapi.persistence.StreamDataSource;
import org.neo4j.driver.v1.Statement;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @author evgeny, 11/21/12
 * @author Artem Tikhomirov
 */
public final class Neo4JPersistence {

  private final MetaModelInfoProvider myMetaInfoProvider;
  private final SModel myModelData;
  private static final ExecutorService threadExecutor = Executors.newSingleThreadExecutor();

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
    SModelHeader result = new SModelHeader();
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
    BoltCypherExecutor executor = new BoltCypherExecutor("bolt://localhost:7687", "neo4j", "hanshans");
    executor.query(new Statement("MATCH (n) DETACH DELETE n"));
    executor.query(new Statement("CREATE CONSTRAINT ON (n:SNode) ASSERT n.NodeId IS UNIQUE;"));

    Neo4JPersistence bp = new Neo4JPersistence(mmiProvider, model);
    CreateNode record = bp.saveModelProperties();
    executor.query(record.toStatement());
    Collection<SNode> roots = IterableUtil.asCollection(model.getRootNodes());


    List<CypherStatement> cypherRecords = new BareNodeWriter(model.getReference()).writeRoots(roots);

    try {
      final List<Statement> firstBatch = cypherRecords.stream().filter(x -> !x.needsNodesToBeCreated()).map(CypherStatement::toStatement).collect(Collectors.toList());
      final List<Statement> secondBatch = cypherRecords.stream().filter(x -> x.needsNodesToBeCreated()).map(CypherStatement::toStatement).collect(Collectors.toList());
      threadExecutor.submit(() -> executor.excec(firstBatch));
      threadExecutor.submit(() -> executor.excec(secondBatch));
      } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Neo4JPersistence(@NotNull MetaModelInfoProvider mmiProvider, SModel modelData) {
    myMetaInfoProvider = mmiProvider;
    myModelData = modelData;
  }


  private CreateNode saveModelProperties() throws IOException {
    // header
    CreateNode record = new CreateNode("SModel");


    SModelReference reference = myModelData.getReference();

    writeModelReference(record, reference);
    if (myModelData instanceof DefaultSModel) {
      SModelHeader mh = ((DefaultSModel) myModelData).getSModelHeader();
      record.addBoolean("DoNotGenerate", mh.isDoNotGenerate());
      Map<String, String> props = new HashMap<String, String>(mh.getOptionalProperties());
      //todo
      //record.addMap("props", props);
    }

    return record;
  }

  private void writeModelReference(CreateNode record, SModelReference reference) {
    record.addString("Name",reference.getModelName());
    SModelId modelId = reference.getModelId();
    if(modelId instanceof jetbrains.mps.smodel.SModelId.RegularSModelId) {
      record.addString("Id", ((jetbrains.mps.smodel.SModelId.RegularSModelId) modelId).getId().toString());
    } else {
      throw new UnsupportedOperationException("Can't serialise Ids of type " + modelId.getClass().getCanonicalName());
    }
    SModuleReference moduleReference = reference.getModuleReference();
    if (moduleReference != null) {
      record.addString("ModuleName", moduleReference.getModuleName());
      SModuleId moduleId = moduleReference.getModuleId();
      if (moduleId instanceof ModuleId.Regular) {
        record.addString("ModuleId", ((ModuleId.Regular) moduleId).getUUID().toString());
      } else {
        throw new UnsupportedOperationException("Can't save id of type " + moduleId.getClass().getCanonicalName());
      }
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
