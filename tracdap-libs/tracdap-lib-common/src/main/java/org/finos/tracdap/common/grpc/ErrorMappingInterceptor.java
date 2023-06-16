/*
 * Copyright 2023 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.common.grpc;

import io.grpc.*;

public class ErrorMappingInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        var errorHandler = new ErrorMappingServerCall<>(call);
        return next.startCall(errorHandler, headers);
    }


    private static class ErrorMappingServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {

        protected ErrorMappingServerCall(ServerCall<ReqT, RespT> delegate) {
            super(delegate);
        }

        @Override
        public void close(Status status, Metadata trailers) {

            // For an OK result, there is no need to apply error mapping
            if (status.isOk()) {
                delegate().close(status, trailers);
            }

            // If the cause of the failure is known, use the cause for error mapping
            // This will preserve the application stack trace
            else if (status.getCause() != null) {

                var mappedStatus  = GrpcErrorMapping.processErrorToStatus(status.getCause());
                delegate().close(mappedStatus, trailers);
            }

            // For a failure with no cause, create an exception from the status object
            // The application stack trace will not be preserved, because it is not present in the status object
            // Typically this happens when the original exception is a gRPC status exception
            // For better error reporting, application code should avoid raising gRPC status exceptions directly
            else {

                var mappedStatus = GrpcErrorMapping.processErrorToStatus(status.asRuntimeException());
                delegate().close(mappedStatus, trailers);
            }
        }
    }
}
