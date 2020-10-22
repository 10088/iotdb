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
package org.apache.iotdb.db.http.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpMethod;

import java.util.List;

import org.apache.iotdb.db.auth.AuthException;
import org.apache.iotdb.db.auth.AuthorityChecker;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.UnsupportedHttpMethod;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.metadata.StorageGroupNotSetException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.http.constant.HttpConstant;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.metadata.mnode.StorageGroupMNode;
import org.apache.iotdb.db.qp.physical.sys.SetStorageGroupPlan;
import org.apache.iotdb.db.service.IoTDB;

public class StorageGroupsHandlers extends Handler {

    public JsonElement handle(HttpMethod httpMethod, JsonElement json)
            throws QueryProcessException, StorageEngineException
            , StorageGroupNotSetException, AuthException
            , IllegalPathException, UnsupportedHttpMethod {
        checkLogin();
        if (HttpMethod.GET.equals(httpMethod)) {
            List<StorageGroupMNode> storageGroupMNodes = IoTDB.metaManager.getAllStorageGroupNodes();
            JsonArray result = new JsonArray();
            for (StorageGroupMNode storageGroupMNode : storageGroupMNodes) {
                if (storageGroupMNode.getDataTTL() > 0 && storageGroupMNode.getDataTTL() < Long.MAX_VALUE) {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty(HttpConstant.STORAGE_GROUP, storageGroupMNode.getFullPath());
                    jsonObject.addProperty(HttpConstant.TTL, storageGroupMNode.getDataTTL());
                    result.add(jsonObject);
                } else {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty(HttpConstant.STORAGE_GROUP, storageGroupMNode.getFullPath());
                    result.add(jsonObject);
                }
            }
            return result;
        } else if (HttpMethod.POST.equals(httpMethod)) {
            JsonArray jsonArray = json.getAsJsonArray();
            for (JsonElement object : jsonArray) {
                String storageGroup = object.getAsString();
                SetStorageGroupPlan plan = new SetStorageGroupPlan(new PartialPath(storageGroup));
                if (!AuthorityChecker.check(username, plan.getPaths(), plan.getOperatorType(), null)) {
                    throw new AuthException(String.format("%s can't be set by %s", storageGroup, username));
                }
                if (!executor.processNonQuery(plan)) {
                    throw new QueryProcessException(String.format("%s can't be set successfully", storageGroup));
                }
            }
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty(HttpConstant.RESULT, HttpConstant.SUCCESSFUL_OPERATION);
            return jsonObject;
        } else {
            throw new UnsupportedHttpMethod(httpMethod.toString());
        }
    }
}
