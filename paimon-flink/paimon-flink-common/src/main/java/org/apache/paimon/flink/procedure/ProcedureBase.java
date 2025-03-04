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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.flink.procedure;

import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.factories.Factory;
import org.apache.paimon.flink.action.ActionBase;
import org.apache.paimon.flink.utils.StreamExecutionEnvironmentUtils;
import org.apache.paimon.utils.StringUtils;

import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.procedure.ProcedureContext;
import org.apache.flink.table.procedures.Procedure;

import javax.annotation.Nullable;

import static org.apache.flink.table.api.config.TableConfigOptions.TABLE_DML_SYNC;

/** Base implementation for flink {@link Procedure}. */
public abstract class ProcedureBase implements Procedure, Factory {

    protected Catalog catalog;

    public ProcedureBase withCatalog(Catalog catalog) {
        this.catalog = catalog;
        return this;
    }

    @Nullable
    protected String nullable(String arg) {
        return StringUtils.isBlank(arg) ? null : arg;
    }

    protected String[] execute(
            ProcedureContext procedureContext, ActionBase action, String defaultJobName)
            throws Exception {
        StreamExecutionEnvironment env = procedureContext.getExecutionEnvironment();
        action.withStreamExecutionEnvironment(env);
        action.build();

        return execute(env, defaultJobName);
    }

    protected String[] execute(ProcedureContext procedureContext, JobClient jobClient) {
        StreamExecutionEnvironment env = procedureContext.getExecutionEnvironment();
        ReadableConfig conf = StreamExecutionEnvironmentUtils.getConfiguration(env);
        return execute(jobClient, conf.get(TABLE_DML_SYNC));
    }

    protected String[] execute(StreamExecutionEnvironment env, String defaultJobName)
            throws Exception {
        ReadableConfig conf = StreamExecutionEnvironmentUtils.getConfiguration(env);
        String name = conf.getOptional(PipelineOptions.NAME).orElse(defaultJobName);
        return execute(env.executeAsync(name), conf.get(TABLE_DML_SYNC));
    }

    private String[] execute(JobClient jobClient, boolean dmlSync) {
        String jobId = jobClient.getJobID().toString();
        if (dmlSync) {
            try {
                jobClient.getJobExecutionResult().get();
            } catch (Exception e) {
                throw new TableException(String.format("Failed to wait job '%s' finish", jobId), e);
            }
            return new String[] {"Success"};
        } else {
            return new String[] {"JobID=" + jobId};
        }
    }
}
