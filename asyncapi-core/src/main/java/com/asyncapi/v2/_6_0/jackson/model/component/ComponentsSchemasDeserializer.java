package com.asyncapi.v2._6_0.jackson.model.component;

import com.asyncapi.schemas.asyncapi.AsyncAPISchema;
import com.asyncapi.schemas.asyncapi.Reference;
import com.asyncapi.schemas.asyncapi.multiformat.MultiFormatSchema;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ComponentsSchemasDeserializer extends JsonDeserializer<Object> {

    public Class<AsyncAPISchema> objectTypeClass() {
        return AsyncAPISchema.class;
    }

    public Class<?> referenceClass() {
        return Reference.class;
    }

    @Override
    public Map<String, Object> deserialize(JsonParser jsonParser,
                                           DeserializationContext deserializationContext
    ) throws IOException, JsonProcessingException {
        ObjectCodec objectCodec = jsonParser.getCodec();
        JsonNode map = objectCodec.readTree(jsonParser);

        Map<String, Object> parameters = new HashMap<>();

        for (Map.Entry<String, JsonNode> entry : map.properties()) {
            String fieldName = entry.getKey();
            try {
                Object value;
                try {
                    value = chooseKnownPojo(entry.getValue(), objectCodec);
                } catch (UnrecognizedPropertyException referenceMismatch) {
                    // A schema can contain $ref alongside fields not supported by Reference.
                    value = readAsObject(entry.getValue(), objectCodec);
                }
                parameters.put(fieldName, value);
            } catch (IOException exception) {
                throw JsonMappingException.wrapWithPath(exception, parameters, fieldName);
            }
        }

        return parameters;
    }

    private Object chooseKnownPojo(JsonNode jsonNode, ObjectCodec objectCodec) throws IOException {
        JsonNode ref = jsonNode.get("$ref");
        try (JsonParser jsonParser = jsonNode.traverse(objectCodec)) {
            if (isMultiFormatSchema(jsonNode)) {
                return jsonParser.readValueAs(MultiFormatSchema.class);
            }

            if (ref != null) {
                return jsonParser.readValueAs(referenceClass());
            } else {
                return jsonParser.readValueAs(objectTypeClass());
            }
        }
    }

    private Object readAsObject(JsonNode jsonNode, ObjectCodec objectCodec) throws IOException {
        try (JsonParser jsonParser = jsonNode.traverse(objectCodec)) {
            return jsonParser.readValueAs(objectTypeClass());
        }
    }

    private boolean isMultiFormatSchema(@NotNull JsonNode jsonNode) {
        JsonNode schemaFormat = jsonNode.get("schemaFormat");
        JsonNode schema = jsonNode.get("schema");

        return (schemaFormat != null) && (schema != null);
    }

}
