package com.asyncapi.serde;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class ListOfReferencesOrObjectsDeserializer<ObjectType> extends JsonDeserializer<List<Object>> {

    abstract public Class<ObjectType> objectTypeClass();

    abstract public Class<?> referenceClass();

    @Override
    public List<Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectCodec objectCodec = p.getCodec();
        JsonNode node = objectCodec.readTree(p);

        List<Object> traits = new ArrayList<>();

        int index = 0;
        for (JsonNode traitsValue : node) {
            try {
                traits.add(chooseKnownPojo(traitsValue, objectCodec));
            } catch (IOException exception) {
                throw JsonMappingException.wrapWithPath(exception, traits, index);
            }
            index++;
        }

        return traits;
    }

    private Object chooseKnownPojo(JsonNode jsonNode, ObjectCodec objectCodec) throws IOException {
        JsonNode ref = jsonNode.get("$ref");
        try (JsonParser jsonParser = jsonNode.traverse(objectCodec)) {
            if (ref != null) {
                return jsonParser.readValueAs(referenceClass());
            } else {
                return jsonParser.readValueAs(objectTypeClass());
            }
        }
    }

}
