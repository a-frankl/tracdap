/*
 * Licensed to the Fintech Open Source Foundation (FINOS) under one or
 * more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * FINOS licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.common.storage;

import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.common.data.IDataContext;
import org.finos.tracdap.metadata.StorageCopy;

import io.netty.channel.EventLoopGroup;
import org.apache.arrow.vector.types.pojo.Schema;

import java.util.concurrent.CompletableFuture;


public interface IDataStorage extends AutoCloseable {

    void start(EventLoopGroup eventLoopGroup);

    void stop();

    @Override
    default void close() { stop(); }

    default DataPipeline pipelineReader(
            StorageCopy storageCopy,
            Schema requiredSchema,
            IDataContext dataContext) {

        return pipelineReader(storageCopy, requiredSchema, dataContext, 0, 0);
    }

    DataPipeline pipelineReader(
            StorageCopy storageCopy,
            Schema requiredSchema,
            IDataContext dataContext,
            long offset, long limit);

    DataPipeline pipelineWriter(
            StorageCopy storageCopy,
            Schema requiredSchema,
            IDataContext dataContext,
            DataPipeline pipeline,
            CompletableFuture<Long> signal);
}
