/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.qp.physical.sys;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.iotdb.db.index.common.IndexType;
import org.apache.iotdb.db.qp.logical.Operator.OperatorType;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.tsfile.read.common.Path;

public class DropIndexPlan extends PhysicalPlan {

  protected List<Path> paths;
  private IndexType indexType;

//  public CreateIndexPlan() {
//    super(false, OperatorType.CREATE_INDEX);
//    canbeSplit = false;
//  }

  public DropIndexPlan(List<Path> paths, IndexType indexType) {
    super(false, OperatorType.DROP_INDEX);
    this.paths = paths;
    this.indexType = indexType;
//    canbeSplit = false;
  }

  public void setPaths(List<Path> paths) {
    this.paths = paths;
  }

  @Override
  public List<Path> getPaths() {
    return paths;
  }

  public IndexType getIndexType() {
    return indexType;
  }

  public void setIndexType(IndexType indexType) {
    this.indexType = indexType;
  }

  @Override
  public void serializeTo(DataOutputStream stream) throws IOException {
    throw new IOException("when do we need serializeTo?");
//    stream.writeByte((byte) PhysicalPlanType.CREATE_TIMESERIES.ordinal());
//    byte[] pathBytes = path.getFullPath().getBytes();
//    stream.writeInt(pathBytes.length);
//    stream.write(pathBytes);
//    stream.write(dataType.ordinal());
//    stream.write(encoding.ordinal());
//    stream.write(compressor.ordinal());
  }

  @Override
  public void deserializeFrom(ByteBuffer buffer) {
    throw new RuntimeException("when do we need deserializeFrom?");
//    int length = buffer.getInt();
//    byte[] pathBytes = new byte[length];
//    buffer.get(pathBytes);
//    path = new Path(new String(pathBytes));
//    dataType = TSDataType.values()[buffer.get()];
//    encoding = TSEncoding.values()[buffer.get()];
//    compressor = CompressionType.values()[buffer.get()];
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DropIndexPlan that = (DropIndexPlan) o;
    return Objects.equals(paths, that.paths)
        && Objects.equals(indexType, that.indexType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(paths, indexType);
  }

  @Override
  public String toString() {
    return String.format("paths: %s, index type: %s",
        paths, indexType);
  }
}
