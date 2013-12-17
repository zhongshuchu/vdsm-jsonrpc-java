package org.ovirt.vdsm.jsonrpc.client.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcRequest;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcResponse;

/**
 * Utility class for json marshalling.
 *
 */
public class JsonUtils {
        private static Log log = LogFactory.getLog(JsonUtils.class);
        private static ObjectMapper mapper = new ObjectMapper();

        public static byte[] jsonToByteArray(JsonNode json, ObjectMapper mapper) {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                try (JsonGenerator gen = mapper.getJsonFactory().createJsonGenerator(os, JsonEncoding.UTF8)) {
                    gen.writeTree(json);
                }
            } catch (IOException e) {
                log.debug("Exception thrown during marshalling json", e);
            }
            return os.toByteArray();
        }

        public static byte[] jsonToByteArray(List<JsonRpcRequest> requests, ObjectMapper mapper) {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                try (JsonGenerator gen = mapper.getJsonFactory().createJsonGenerator(os, JsonEncoding.UTF8)) {
                    gen.writeStartArray();
                    for (final JsonRpcRequest request : requests) {
                        gen.writeTree(request.toJson());
                    }
                    gen.writeEndArray();
                }
            } catch (IOException e) {
                log.debug("Exception thrown during marshalling json", e);
            }
            return os.toByteArray();
        }

        public static JsonRpcResponse buildFailedResponse(JsonRpcRequest request) {
            String networkConnectivityIssueMessage =
                    "{\"jsonrpc\": \"2.0\", \"id\": \"" + request.getPlainId() + "\", \"error\": {\"message\":"
                            + " \"Message timeout which can be caused by communication issues'\", \"code\": 5022}}";
            JsonRpcResponse response = null;
            try {
                response = JsonRpcResponse.fromJsonNode(mapper.readTree(networkConnectivityIssueMessage));
            } catch (IOException e) {
                log.debug("Exception thrown during marshalling json", e);
            }
            return response;
        }
}