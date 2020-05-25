/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.iotdb.db.index.algorithm.elb;

import static org.apache.iotdb.db.index.common.IndexConstant.DEFAULT_DISTANCE;
import static org.apache.iotdb.db.index.common.IndexConstant.DEFAULT_ELB_CALC_PARAM;
import static org.apache.iotdb.db.index.common.IndexConstant.DEFAULT_ELB_TYPE;
import static org.apache.iotdb.db.index.common.IndexConstant.DISTANCE;
import static org.apache.iotdb.db.index.common.IndexConstant.ELB_CALC_PARAM;
import static org.apache.iotdb.db.index.common.IndexConstant.ELB_CALC_PARAM_SINGLE;
import static org.apache.iotdb.db.index.common.IndexConstant.ELB_DEFAULT_THRESHOLD_RATIO;
import static org.apache.iotdb.db.index.common.IndexConstant.ELB_THRESHOLD_BASE;
import static org.apache.iotdb.db.index.common.IndexConstant.ELB_THRESHOLD_RATIO;
import static org.apache.iotdb.db.index.common.IndexConstant.ELB_TYPE;
import static org.apache.iotdb.db.index.common.IndexConstant.PATTERN;
import static org.apache.iotdb.db.index.common.IndexConstant.THRESHOLD;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.iotdb.db.index.algorithm.MBRIndex;
import org.apache.iotdb.db.index.algorithm.elb.ELBFeatureExtractor.ELBType;
import org.apache.iotdb.db.index.algorithm.elb.pattern.CalcParam;
import org.apache.iotdb.db.index.algorithm.elb.pattern.SingleParamSchema;
import org.apache.iotdb.db.index.common.IndexInfo;
import org.apache.iotdb.db.index.common.IndexQueryException;
import org.apache.iotdb.db.index.common.IndexUtils;
import org.apache.iotdb.db.index.common.UnsupportedIndexQueryException;
import org.apache.iotdb.db.index.distance.Distance;
import org.apache.iotdb.db.index.preprocess.Identifier;
import org.apache.iotdb.db.index.read.func.IndexFuncFactory;
import org.apache.iotdb.db.index.read.func.IndexFuncResult;
import org.apache.iotdb.db.rescon.TVListAllocator;
import org.apache.iotdb.db.utils.datastructure.TVList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Use PAA as the feature of MBRIndex.</p>
 */

public class ELBIndex extends MBRIndex {

  private static final Logger logger = LoggerFactory.getLogger(ELBIndex.class);
  private Distance distance;
  private ELBType elbType;
  private CalcParam calcParam;


  private ELBCountFixedPreprocessor elbTimeFixedPreprocessor;

  // Only for query
  private Map<Integer, Identifier> identifierMap = new HashMap<>();

  public ELBIndex(String path, IndexInfo indexInfo) {
    super(path, indexInfo, false);
    initELBParam();
  }

  private void initELBParam() {
    this.distance = Distance.getDistance(props.getOrDefault(DISTANCE, DEFAULT_DISTANCE));
    elbType = ELBType.valueOf(props.getOrDefault(ELB_TYPE, DEFAULT_ELB_TYPE));
    String calcParamName = props.getOrDefault(ELB_CALC_PARAM, DEFAULT_ELB_CALC_PARAM);
    if (ELB_CALC_PARAM_SINGLE.equals(calcParamName)) {
      double thresholdBase = Double.parseDouble(props.getOrDefault(ELB_THRESHOLD_BASE, "-1"));
      double thresholdRatio = Double.parseDouble(props.getOrDefault(ELB_THRESHOLD_RATIO, "-1"));
      if (thresholdBase == -1 && thresholdRatio == -1) {
        thresholdRatio = ELB_DEFAULT_THRESHOLD_RATIO;
      }
      this.calcParam = new SingleParamSchema(thresholdBase, thresholdRatio, windowRange);
    }
  }

  @Override
  public void initPreprocessor(ByteBuffer previous, boolean inQueryMode) {
    if (this.indexPreprocessor != null) {
      this.indexPreprocessor.clear();
    }
    this.elbTimeFixedPreprocessor = new ELBCountFixedPreprocessor(tsDataType, windowRange,
        slideStep, featureDim, distance, calcParam, elbType, true, false, false);
    elbTimeFixedPreprocessor.setInQueryMode(inQueryMode);
    this.indexPreprocessor = elbTimeFixedPreprocessor;
    indexPreprocessor.deserializePrevious(previous);
  }


  /**
   * Fill {@code currentCorners} and the optional {@code currentRanges}, and return the current idx
   *
   * @return the current idx
   */
  @Override
  protected int fillCurrentFeature() {
    elbTimeFixedPreprocessor.copyFeature(currentCorners, currentRanges);
    return elbTimeFixedPreprocessor.getSliceNum() - 1;
  }

  @Override
  protected BiConsumer<Integer, OutputStream> getSerializeFunc() {
    return (idx, outputStream) -> {
      try {
        elbTimeFixedPreprocessor.serializeIdentifier(idx, outputStream);
      } catch (IOException e) {
        logger.error("serialized error.", e);
      }
    };
  }


  @Override
  public void delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void initQuery(Map<String, String> queryConditions, List<IndexFuncResult> indexFuncResults)
      throws UnsupportedIndexQueryException {
    for (IndexFuncResult result : indexFuncResults) {
      switch (result.getIndexFunc()) {
        case TIME_RANGE:
        case SIM_ST:
        case SIM_ET:
        case SERIES_LEN:
        case ED:
        case DTW:
          result.setIsTensor(true);
          break;
        default:
          throw new UnsupportedIndexQueryException(indexFuncResults.toString());
      }
      result.setIndexFuncDataType(result.getIndexFunc().getType());
    }
    if (queryConditions.containsKey(THRESHOLD)) {
      this.threshold = Double.parseDouble(queryConditions.get(THRESHOLD));
    } else {
      this.threshold = Double.MAX_VALUE;
    }
    if (queryConditions.containsKey(PATTERN)) {
      this.patterns = IndexUtils.parseNumericPattern(queryConditions.get(PATTERN));
    } else {
      throw new UnsupportedIndexQueryException("missing parameter: " + PATTERN);
    }
  }


  @Override
  protected BiConsumer<Integer, ByteBuffer> getDeserializeFunc() {
    return (idx, input) -> {
      Identifier identifier = Identifier.deserialize(input);
      identifierMap.put(idx, identifier);
    };
  }

  @Override
  protected List<Identifier> getQueryCandidates(List<Integer> candidateIds) {
    List<Identifier> res = new ArrayList<>(candidateIds.size());
    candidateIds.forEach(i->res.add(identifierMap.get(i)));
    this.identifierMap.clear();
    return res;
  }

  @Override
  protected double calcLowerBoundThreshold(double queryThreshold) {
    return 0;
  }

  /**
   * Same as PAAIndex, the query of ELB is in form of PAA
   */
  @Override
  protected void calcAndFillQueryFeature() {
    Arrays.fill(currentCorners, 0);
    Arrays.fill(currentRanges, 0);
    int intervalWidth = windowRange / featureDim;
    for (int i = 0; i < featureDim; i++) {
      for (int j = 0; j < intervalWidth; j++) {
        currentCorners[i] += patterns[i * intervalWidth + j];
      }
      currentCorners[i] /= intervalWidth;
    }
  }

  @Override
  public int postProcessNext(List<IndexFuncResult> funcResult) throws IndexQueryException {
    TVList aligned = (TVList) indexPreprocessor.getCurrent_L2_AlignedSequence();
    double ed = IndexFuncFactory.calcEuclidean(aligned, patterns);
//    System.out.println(String.format(
//        "ELB Process: ed:%.3f: %s", ed, IndexUtils.tvListToStr(aligned)));
    int reminding = funcResult.size();
    if (ed <= threshold) {
      for (IndexFuncResult result : funcResult) {
        IndexFuncFactory.basicSimilarityCalc(result, indexPreprocessor, patterns);
      }
    }
    TVListAllocator.getInstance().release(aligned);
    return reminding;
  }
}
