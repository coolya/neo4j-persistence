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
package com.mbeddr.persistence;

import com.intellij.openapi.components.ApplicationComponent;
import jetbrains.mps.extapi.model.GeneratableSModel;
import jetbrains.mps.extapi.model.SModelBase;
import jetbrains.mps.extapi.model.SModelData;
import jetbrains.mps.generator.ModelDigestUtil;
import jetbrains.mps.logging.Logger;
import jetbrains.mps.persistence.*;
import jetbrains.mps.persistence.MetaModelInfoProvider.RegularMetaModelInfo;
import jetbrains.mps.persistence.MetaModelInfoProvider.StuffedMetaModelInfo;
import com.mbeddr.persistence.neo4j.Neo4JPersistence;
import jetbrains.mps.project.MPSExtentions;
import jetbrains.mps.smodel.DefaultSModelDescriptor;
import jetbrains.mps.smodel.SModelHeader;
import jetbrains.mps.smodel.loading.ModelLoadResult;
import jetbrains.mps.smodel.loading.ModelLoadingState;
import jetbrains.mps.smodel.persistence.def.ModelReadException;
import org.apache.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mps.openapi.model.SModel;
import org.jetbrains.mps.openapi.persistence.DataSource;
import org.jetbrains.mps.openapi.persistence.ModelFactory;
import org.jetbrains.mps.openapi.persistence.PersistenceFacade;
import org.jetbrains.mps.openapi.persistence.StreamDataSource;
import org.jetbrains.mps.openapi.persistence.UnsupportedDataSourceException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * evgeny, 11/20/12
 */
public class Neo4JModelPersistence implements ApplicationComponent, ModelFactory, IndexAwareModelFactory {
  private static final String MODEL_NEO4J = "neo4j";
  private static final Logger LOG = Logger.wrap(LogManager.getLogger(Neo4JModelPersistence.class));

  @NotNull
  @Override
  public SModel load(@NotNull DataSource dataSource, @NotNull Map<String, String> options) throws IOException {
    if (!(dataSource instanceof StreamDataSource)) {
      throw new UnsupportedDataSourceException(dataSource);
    }

    LOG.debug("loading model");

    StreamDataSource source = (StreamDataSource) dataSource;
    SModelHeader header;
    try {
      header = Neo4JPersistence.readHeader(source);
    } catch (ModelReadException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IOException(e.getMessageEx(), e);
    }
    if (Boolean.parseBoolean(options.get(MetaModelInfoProvider.OPTION_KEEP_READ_METAINFO))) {
      header.setMetaInfoProvider(new StuffedMetaModelInfo(new RegularMetaModelInfo(header.getModelReference())));
    }
    return new DefaultSModelDescriptor(new Neo4JFacility(this, source), header);
  }

  @NotNull
  @Override
  public SModel create(DataSource dataSource, @NotNull Map<String, String> options) throws IOException {
    if (!(dataSource instanceof StreamDataSource)) {
      throw new UnsupportedDataSourceException(dataSource);
    }

    LOG.debug("Creating model");

    StreamDataSource source = (StreamDataSource) dataSource;
    String modelName = options.get(OPTION_MODELNAME);
    if (modelName == null) {
      throw new IOException("modelName is not provided");
    }
    String modulRef = options.get(OPTION_MODULEREF);
    if (modulRef == null) {
      throw new IOException("moduleRef is not provided");
    }

    final SModelHeader header = new SModelHeader();
    header.setModelReference(PersistenceFacade.getInstance().createModelReference(null, jetbrains.mps.smodel.SModelId.generate(), modelName));
    return new DefaultSModelDescriptor(new Neo4JFacility(this, source), header);
  }

  @Override
  public boolean canCreate(DataSource dataSource, @NotNull Map<String, String> options) {
    return dataSource instanceof StreamDataSource;
  }

  @Override
  public boolean needsUpgrade(DataSource dataSource) throws IOException {
    return false;
  }

  @Override
  public void upgrade(DataSource dataSource) throws IOException {
    // no-op
  }

  @Override
  public void save(SModel model, DataSource dataSource) throws IOException {
    if (!(dataSource instanceof StreamDataSource)) {
      throw new UnsupportedDataSourceException(dataSource);
    }
    Neo4JPersistence.writeModel(((SModelBase) model).getSModel(), (StreamDataSource) dataSource);
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public String getFileExtension() {
    return "neo4j";
  }

  @Override
  public String getFormatTitle() {
    return "Neo4J persistence";
  }

  @Override
  public void index(@NotNull InputStream input, @NotNull Callback callback) throws IOException {
    Neo4JPersistence.index(input, callback);
  }

  public static Map<String, String> getDigestMap(@NotNull StreamDataSource source) {
    try {
      SModelHeader binaryModelHeader = Neo4JPersistence.readHeader(source);
      binaryModelHeader.setMetaInfoProvider(new StuffedMetaModelInfo(new RegularMetaModelInfo(binaryModelHeader.getModelReference())));
      final ModelLoadResult loadedModel = Neo4JPersistence.readModel(binaryModelHeader, source, false);
      Map<String, String> result = Neo4JPersistence.getDigestMap(loadedModel.getModel(), binaryModelHeader.getMetaInfoProvider());
      result.put(GeneratableSModel.FILE, ModelDigestUtil.hashBytes(source.openInputStream()));
      return result;
    } catch (ModelReadException ignored) {
      /* ignore */
    } catch (IOException e) {
      /* ignore */
    }
    return null;
  }

  /**
   * This is provisional workaround to deal with performance tuning in jps/plugin (see CachedRepositoryData, CachedModelData)
   * where header is serialized to get passed to another process, where model is instantiated without need to read model file.
   *
   * If there's real benefit in this optimization (commit comment suggests it's 0.5 second in process startup time, which doesn't look too much, imo)
   * this serialization shall be addressed with an object supplied by descriptor itself, rather than by external means, so that full control over
   * serialize/restore is inside implementation, and all the internal stuff (like model header) doesn't get exposed.
   * FIXME revisit, reconsider approach
   */
  public static SModel createFromHeader(@NotNull SModelHeader header, @NotNull StreamDataSource dataSource) {
    final ModelFactory modelFactory = PersistenceFacade.getInstance().getModelFactory(MPSExtentions.MODEL_BINARY);
    assert modelFactory instanceof Neo4JModelPersistence;
    return new DefaultSModelDescriptor(new Neo4JFacility((Neo4JModelPersistence) modelFactory, dataSource), header.createCopy());
  }

  @NotNull
  @Override
  public String getComponentName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void initComponent() {
    LOG.debug("initComponent");
    PersistenceFacade.getInstance().setModelFactory(MODEL_NEO4J, this);
  }

  @Override
  public void disposeComponent() {
    LOG.debug("disposeComponent");
    PersistenceFacade.getInstance().setModelFactory(MODEL_NEO4J, null);
  }

  private static class Neo4JFacility extends LazyLoadFacility {
    /*package*/ Neo4JFacility(Neo4JModelPersistence modelFactory, StreamDataSource dataSource) {
      super(modelFactory, dataSource);
    }

    @NotNull
    @Override
    public StreamDataSource getSource() {
      return (StreamDataSource) super.getSource();
    }

    @Override
    public Map<String, String> getGenerationHashes() {
      Map<String, String> generationHashes = ModelDigestHelper.getInstance().getGenerationHashes(getSource());
      if (generationHashes != null) return generationHashes;

      return Neo4JModelPersistence.getDigestMap(getSource());
    }

    @NotNull
    @Override
    public SModelHeader readHeader() throws ModelReadException {
      return Neo4JPersistence.readHeader(getSource());
    }

    @NotNull
    @Override
    public ModelLoadResult readModel(@NotNull SModelHeader header, @NotNull ModelLoadingState state) throws ModelReadException {
      return Neo4JPersistence.readModel(header, getSource(), state == ModelLoadingState.INTERFACE_LOADED);
    }

    @Override
    public boolean doesSaveUpgradePersistence(@NotNull SModelHeader header) {
      // binary persistence doesn't have versions yet
      return false;
    }

    @Override
    public void saveModel(@NotNull SModelHeader header, SModelData modelData) throws IOException {
      Neo4JPersistence.writeModel((jetbrains.mps.smodel.SModel) modelData, getSource());
    }
  }
}
