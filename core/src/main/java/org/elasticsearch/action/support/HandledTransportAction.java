/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.action.support;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportChannel;
import org.elasticsearch.transport.TransportRequestHandler;
import org.elasticsearch.transport.TransportService;

import java.util.function.Supplier;

/**
 * A TransportAction that self registers a handler into the transport service
 */
public abstract class HandledTransportAction<Request extends ActionRequest, Response extends ActionResponse>
        extends TransportAction<Request, Response> {
    protected HandledTransportAction(Settings settings, String actionName, ThreadPool threadPool, TransportService transportService,
                                     ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                     Supplier<Request> request) {
        this(settings, actionName, true, threadPool, transportService, actionFilters, indexNameExpressionResolver, request);
    }

    protected HandledTransportAction(Settings settings, String actionName, ThreadPool threadPool, TransportService transportService,
                                     ActionFilters actionFilters, Writeable.Reader<Request> requestReader,
                                     IndexNameExpressionResolver indexNameExpressionResolver) {
        this(settings, actionName, true, threadPool, transportService, actionFilters, indexNameExpressionResolver, requestReader);
    }

    protected HandledTransportAction(Settings settings, String actionName, boolean canTripCircuitBreaker, ThreadPool threadPool,
                                     TransportService transportService, ActionFilters actionFilters,
                                     IndexNameExpressionResolver indexNameExpressionResolver, Supplier<Request> request) {
        super(settings, actionName, threadPool, actionFilters, indexNameExpressionResolver, transportService.getTaskManager());
        transportService.registerRequestHandler(actionName, request, ThreadPool.Names.SAME, false, canTripCircuitBreaker,
            new TransportHandler());
    }

    protected HandledTransportAction(Settings settings, String actionName, boolean canTripCircuitBreaker, ThreadPool threadPool,
                                     TransportService transportService, ActionFilters actionFilters,
                                     IndexNameExpressionResolver indexNameExpressionResolver, Writeable.Reader<Request> requestReader) {
        super(settings, actionName, threadPool, actionFilters, indexNameExpressionResolver, transportService.getTaskManager());
        transportService.registerRequestHandler(actionName, ThreadPool.Names.SAME, false, canTripCircuitBreaker, requestReader,
            new TransportHandler());
    }

    class TransportHandler implements TransportRequestHandler<Request> {

        @Override
        public final void messageReceived(Request request, TransportChannel channel) throws Exception {
            throw new UnsupportedOperationException("the task parameter is required for this operation");
        }

        @Override
        public final void messageReceived(final Request request, final TransportChannel channel, Task task) throws Exception {
            // We already got the task created on the network layer - no need to create it again on the transport layer
            execute(task, request, new ActionListener<Response>() {
                @Override
                public void onResponse(Response response) {
                    try {
                        channel.sendResponse(response);
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    try {
                        channel.sendResponse(e);
                    } catch (Exception e1) {
                        logger.warn(
                            (org.apache.logging.log4j.util.Supplier<?>)
                                () -> new ParameterizedMessage(
                                    "Failed to send error response for action [{}] and request [{}]",
                                    actionName,
                                    request),
                            e1);
                    }
                }
            });
        }
    }

}
