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

package org.apache.iotdb.confignode.consensus.request.write.storagegroup;

import org.apache.iotdb.commons.utils.BasicStructureSerDeUtil;
import org.apache.iotdb.confignode.consensus.request.ConfigPhysicalPlan;
import org.apache.iotdb.confignode.consensus.request.ConfigPhysicalPlanType;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PreDeleteStorageGroupPlan extends ConfigPhysicalPlan {
  private String storageGroup;
  private PreDeleteType preDeleteType;

  public PreDeleteStorageGroupPlan() {
    super(ConfigPhysicalPlanType.PreDeleteDatabase);
  }

  public PreDeleteStorageGroupPlan(String storageGroup, PreDeleteType preDeleteType) {
    this();
    this.storageGroup = storageGroup;
    this.preDeleteType = preDeleteType;
  }

  public String getStorageGroup() {
    return storageGroup;
  }

  public void setStorageGroup(String storageGroup) {
    this.storageGroup = storageGroup;
  }

  public PreDeleteType getPreDeleteType() {
    return preDeleteType;
  }

  public void setPreDeleteType(PreDeleteType preDeleteType) {
    this.preDeleteType = preDeleteType;
  }

  @Override
  protected void serializeImpl(DataOutputStream stream) throws IOException {
    stream.writeShort(getType().getPlanType());
    BasicStructureSerDeUtil.write(storageGroup, stream);
    stream.write(preDeleteType.getType());
  }

  @Override
  protected void deserializeImpl(ByteBuffer buffer) throws IOException {
    this.storageGroup = BasicStructureSerDeUtil.readString(buffer);
    this.preDeleteType = buffer.get() == (byte) 1 ? PreDeleteType.ROLLBACK : PreDeleteType.EXECUTE;
  }

  public enum PreDeleteType {
    EXECUTE((byte) 0),
    ROLLBACK((byte) 1);

    private final byte type;

    PreDeleteType(byte type) {
      this.type = type;
    }

    public byte getType() {
      return type;
    }
  }
}
