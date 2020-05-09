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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.db.index.algorithm.elb.ELBFeatureExtractor.ELBType;
import org.apache.iotdb.db.index.algorithm.elb.pattern.CalcParam;
import org.apache.iotdb.db.index.common.IllegalIndexParamException;
import org.apache.iotdb.db.index.common.IndexRuntimeException;
import org.apache.iotdb.db.index.distance.Distance;
import org.apache.iotdb.db.index.preprocess.CountFixedPreprocessor;
import org.apache.iotdb.db.index.preprocess.Identifier;
import org.apache.iotdb.db.utils.datastructure.primitive.PrimitiveList;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;

/**
 * ELB (Equal-Length Block), a feature for efficient adjacent sequence pruning. <p>
 *
 * Refer to: Kang R, et al. Matching Consecutive Subpatterns over Streaming Time Series[C]
 * APWeb-WAIM Joint International Conference. Springer, Cham, 2018: 90-105.
 */
public class ELBCountFixedPreprocessor extends CountFixedPreprocessor {

  private final int blockNum;
  /**
   * A list of MBRs. Every MBR contains {@code b} upper/lower bounds, i.e. {@code 2*b} doubles.<p>
   *
   * Format: {@code {u_11, l_11, ..., u_1b, l_1b; u_21, l_21, ..., u_2b, l_2b; ...}}
   */
  private final PrimitiveList mbrs;
  private final ELBFeatureExtractor elbFeatureExtractor;
  private final boolean storeFeature;
  private final PrimitiveList currentMBR;

  /**
   * ELB divides the aligned sequence into {@code b} equal-length blocks. For each block, ELB
   * calculates a float number pair as the upper and lower bounds.<p>
   *
   * A block contains {@code windowRange/b} points. A list of blocks ({@code b} blocks) cover
   * adjacent {@code windowRange/b} sequence.
   */
  public ELBCountFixedPreprocessor(TSDataType tsDataType, int windowRange, int slideStep,
      int blockNum, Distance distance, CalcParam calcParam, ELBType elbType,
      boolean storeIdentifier, boolean storeAligned, boolean storeFeature) {
    super(tsDataType, windowRange, slideStep, storeIdentifier, storeAligned);
    this.storeFeature = storeFeature;
    if (blockNum > windowRange) {
      throw new IllegalIndexParamException(String
          .format("In PAA, blockNum %d cannot be larger than windowRange %d", blockNum,
              windowRange));
    }
    this.blockNum = blockNum;
    this.mbrs = PrimitiveList.newList(TSDataType.DOUBLE);
    this.currentMBR = PrimitiveList.newList(TSDataType.DOUBLE);
    elbFeatureExtractor = new ELBFeatureExtractor(srcData, distance, windowRange, calcParam,
        blockNum, elbType);
  }

  public ELBCountFixedPreprocessor(TSDataType tsDataType, int windowRange, int slideStep,
      int blockNum, Distance distance, CalcParam calcParam, ELBType elbType) {
    this(tsDataType, windowRange, slideStep, blockNum, distance, calcParam, elbType, false, false,
        true);
  }

  @Override
  public void processNext() {
    super.processNext();
    if (!inQueryMode) {
      currentMBR.clearButNotRelease();
      elbFeatureExtractor.calcELBFeature(currentStartTimeIdx, currentMBR);
      if (storeFeature) {
        mbrs.putAllDouble(currentMBR);
      }
    }
  }

  private double[][] formatELBFeature(PrimitiveList list, int idx) {
    double[][] res = new double[blockNum][2];
    for (int i = 0; i < blockNum; i++) {
      res[i][0] = list.getDouble(2 * blockNum * idx + 2 * i);
      res[i][1] = list.getDouble(2 * blockNum * idx + 2 * i + 1);
    }
    return res;
  }

  /**
   * if not store feature, we can only return at most one feature
   *
   * @param latestN: try my best to return, but maybe not enough
   * @return maybe less than specified latestN
   */
  @Override
  public List<Object> getLatestN_L3_Features(int latestN) {
    latestN = Math.min(getCurrentChunkSize(), latestN);
    List<Object> res = new ArrayList<>(latestN);
    if (latestN == 0) {
      return res;
    }
    if (!storeFeature) {
      res.add(formatELBFeature(currentMBR, 0));
    } else {
      int startIdx = Math.max(flushedOffset, sliceNum - latestN);
      for (int i = startIdx; i < sliceNum; i++) {
        res.add(formatELBFeature(mbrs, i - flushedOffset));
      }
    }
    return res;
  }

  @Override
  public long clear() {
    long toBeReleased = super.clear();
    if (storeFeature) {
      toBeReleased += getCurrentChunkSize() * elbFeatureExtractor.getAmortizedSize();
      mbrs.clearAndRelease();
    }
    return toBeReleased;
  }

  @Override
  public int getAmortizedSize() {
    int res = super.getAmortizedSize();
    if (storeFeature) {
      res += elbFeatureExtractor.getAmortizedSize();
    }
    return res;
  }


  /**
   * custom for {@linkplain ELBIndex}
   *
   * @param currentCorners current corners
   */
  void copyFeature(float[] currentCorners, float[] currentRanges) {
    if (blockNum != currentCorners.length || blockNum != currentRanges.length) {
      throw new IndexRuntimeException("blockDim != currentCorners or currentRanges length");
    }
    for (int i = 0; i < blockNum; i++) {
      currentCorners[i] = (float) currentMBR.getDouble(2 * i + 1);
      currentRanges[i] = (float) currentMBR.getDouble(2 * i) - currentCorners[i];
    }
  }

  /**
   * custom for {@linkplain ELBIndex}
   *
   * @param idx the idx-th identifiers
   * @param outputStream to output
   */
  void serializeIdentifier(Integer idx, OutputStream outputStream) throws IOException {
    int actualIdx = idx - flushedOffset;
    if (actualIdx * 3 + 2 >= identifierList.size()) {
      throw new IOException(String.format("ELB serialize: idx %d*3+2 > identifiers size %d", idx,
          identifierList.size()));
    }
    if (!storeIdentifier) {
      throw new IOException("In ELB index, must store the identifier list");
    }
    Identifier identifier = new Identifier(identifierList.getLong(actualIdx * 3),
        identifierList.getLong(actualIdx * 3 + 1),
        (int) identifierList.getLong(actualIdx * 3 + 2));
    identifier.serialize(outputStream);
  }
}
