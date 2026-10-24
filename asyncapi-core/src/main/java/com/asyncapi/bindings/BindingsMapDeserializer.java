package com.asyncapi.bindings;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Deserializes a protocol binding map or a reference to the whole map.
 * Concrete maps retain protocol names and the values selected by {@link #chooseKnownPojo}.
 * A whole-map reference uses a single {@code "$ref" -> URI string} entry so existing
 * {@code Map<String, Object>} getters keep their signatures. Its siblings are ignored.
 * The URI is neither decoded nor resolved here; document validation and resolution belong
 * to the caller. A nullable model property may still be absent or null through Jackson's
 * usual null handling.
 *
 * @author Pavel Bodiachevskii
 * @since 1.0.0-RC2
 */
public abstract class BindingsMapDeserializer extends JsonDeserializer<Map<String, Object>> {

    /**
     * Maps one protocol value using the owner's server/channel/operation/message decoder.
     * Unknown protocol behavior is defined by the concrete decoder, which may return null;
     * this mapper does not invent a generic replacement or silently catch mapping failures.
     * @param bindingKey protocol name, not a reusable component name
     * @param binding protocol-specific source object
     * @param objectCodec configured codec, including caller-provided model customizations
     * @return typed protocol binding, or the concrete decoder's unknown-protocol value
     * @throws IOException if the protocol value cannot be mapped
     */
    public abstract Object chooseKnownPojo(String bindingKey, JsonNode binding, ObjectCodec objectCodec) throws IOException;

    /**
     * Reads a non-null object without following references or returning partial results.
     * @param p parser for the binding-map value
     * @param ctxt Jackson context supplied to the decoder
     * @return protocol map, an empty map, or a single-entry whole-map reference
     * @throws IOException if the value is not an object, a reference is not a string,
     *                     or a protocol value fails mapping; paths retain the protocol key
     */
    @Override
    public Map<String, Object> deserialize(
            JsonParser p,
            DeserializationContext ctxt
    ) throws IOException {
        ObjectCodec objectCodec = p.getCodec();
        JsonNode node = objectCodec.readTree(p);

        Map<String, Object> bindings = new HashMap<>();

        if (!node.isObject()) {
            throw JsonMappingException.from(p, "Expected an object for bindings");
        }
        // A whole-map Reference Object is not a protocol entry. Keep the existing Map getter
        // contract and its original URI; resolving it belongs to the caller's separate resolver.
        // Do not map siblings: they are not part of the referenced map, even when malformed.
        if (node.has("$ref")) {
            JsonNode reference = node.get("$ref");
            if (!reference.isTextual()) {
                throw JsonMappingException.wrapWithPath(
                        JsonMappingException.from(p, "Binding map $ref must be a string"), bindings, "$ref");
            }
            bindings.put("$ref", reference.textValue());
            return bindings;
        }

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
