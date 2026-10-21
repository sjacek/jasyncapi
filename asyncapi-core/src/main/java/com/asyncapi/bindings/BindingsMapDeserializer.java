package com.asyncapi.bindings;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Deserializes AsyncAPI bindings map.
 *
 * @author Pavel Bodiachevskii
 * @since 1.0.0-RC2
 */
public abstract class BindingsMapDeserializer extends JsonDeserializer<Map<String, Object>> {

    public abstract Object chooseKnownPojo(String bindingKey, JsonNode binding, ObjectCodec objectCodec) throws IOException;

    @Override
    public Map<String, Object> deserialize(
            JsonParser p,
            DeserializationContext ctxt
    ) throws IOException, JsonProcessingException {
        ObjectCodec objectCodec = p.getCodec();
        JsonNode node = objectCodec.readTree(p);

        Map<String, Object> bindings = new HashMap<>();

        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String fieldName = entry.getKey();
            try {
                bindings.put(fieldName, chooseKnownPojo(fieldName, entry.getValue(), objectCodec));
            } catch (IOException exception) {
                throw JsonMappingException.wrapWithPath(exception, bindings, fieldName);
            }
        }

        return bindings;
    }

}
