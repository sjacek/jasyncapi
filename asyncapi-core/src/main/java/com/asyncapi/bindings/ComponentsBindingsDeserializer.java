package com.asyncapi.bindings;

import com.asyncapi.schemas.asyncapi.Reference;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads named reusable binding maps, rather than treating component names as protocol names.
 * The outer keys are component names, even if a name happens to match a protocol.
 * Each concrete entry contains a protocol map; aliases remain {@link Reference} objects.
 * Empty maps remain empty maps, rather than null or a protocol binding instance.
 * No URI is resolved or expanded, and all existing Map-based model getters are retained.
 * Collection nullability is handled by Jackson; null or scalar named entries are invalid.
 */
public abstract class ComponentsBindingsDeserializer extends JsonDeserializer<Map<String, Object>> {
    /** Decoder for protocol entries inside each named component, shared by inline owner maps. */
    private final BindingsMapDeserializer bindingsDeserializer;

    /**
     * Selects the owning binding kind once for all entries in this component collection.
     * @param bindingsDeserializer protocol-map decoder for the owning component field
     */
    protected ComponentsBindingsDeserializer(BindingsMapDeserializer bindingsDeserializer) {
        this.bindingsDeserializer = bindingsDeserializer;
    }

    /**
     * Reads named protocol maps and reference aliases in source order.
     * Each entry uses the parser's codec, preserving the caller's mapping configuration.
     * @param parser parser for a non-null component collection object
     * @param context Jackson context forwarded to the protocol-map decoder
     * @return component names mapped to protocol maps or unresolved references
     * @throws IOException if the collection, named map or reference has an invalid shape,
     *                     or a protocol cannot be mapped; the component name is retained in the path
     */
    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode node = codec.readTree(parser);
        if (!node.isObject()) {
            throw JsonMappingException.from(parser, "Expected an object for reusable binding maps");
        }
        Map<String, Object> components = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            try (JsonParser valueParser = entry.getValue().traverse(codec)) {
                Map<String, Object> bindings = bindingsDeserializer.deserialize(valueParser, context);
                // Owner fields keep Map getters; Object-valued component entries can use Reference.
                components.put(entry.getKey(), bindings.containsKey("$ref")
                        ? new Reference((String) bindings.get("$ref")) : bindings);
            } catch (IOException exception) {
                // Keep both layers: components.<kind>/<name>/<protocol>/<property>.
                throw JsonMappingException.wrapWithPath(exception, components, entry.getKey());
            }
        }
        return components;
    }

    /** Protocol decoder for components.serverBindings entries. */
    public static final class Server extends ComponentsBindingsDeserializer {
        /** Creates the decoder for named server binding maps and aliases. */
        public Server() { super(new ServerBindingsDeserializer()); }
    }

    /** Protocol decoder for components.channelBindings entries. */
    public static final class Channel extends ComponentsBindingsDeserializer {
        /** Creates the decoder for named channel binding maps and aliases. */
        public Channel() { super(new ChannelBindingsDeserializer()); }
    }

    /** Protocol decoder for components.operationBindings entries. */
    public static final class Operation extends ComponentsBindingsDeserializer {
        /** Creates the decoder for named operation binding maps and aliases. */
        public Operation() { super(new OperationBindingsDeserializer()); }
    }

    /** Protocol decoder for components.messageBindings entries. */
    public static final class Message extends ComponentsBindingsDeserializer {
        /** Creates the decoder for named message binding maps and aliases. */
        public Message() { super(new MessageBindingsDeserializer()); }
    }
}
