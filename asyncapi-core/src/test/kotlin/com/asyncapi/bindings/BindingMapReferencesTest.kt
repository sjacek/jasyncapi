package com.asyncapi.bindings

import com.asyncapi.bindings.amqp.*
import com.asyncapi.schemas.asyncapi.Reference
import com.asyncapi.v3._0_0.model.channel.Channel
import com.asyncapi.v3._0_0.model.channel.message.Message
import com.asyncapi.v3._0_0.model.channel.message.MessageTrait
import com.asyncapi.v3._0_0.model.component.Components
import com.asyncapi.v3._0_0.model.operation.Operation
import com.asyncapi.v3._0_0.model.operation.OperationTrait
import com.asyncapi.v3._0_0.model.server.Server
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Mapping regressions only: references remain unresolved and all Map-based getters stay usable.
 * URI validity, target existence, kind compatibility and cycles belong to a document resolver.
 * Reflection here deliberately verifies the unchanged getter contract across all owner classes.
 */
class BindingMapReferencesTest {
    private val mapper = ObjectMapper().setDefaultPropertyInclusion(
        JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)
    )

    @ParameterizedTest(name = "whole-map reference: {0}")
    @MethodSource("owners")
    fun `whole bindings references survive without resolving or changing getter types`(name: String, owner: Class<*>) {
        val source = """{"bindings":{"${'$'}ref":"#/components/channelBindings/Alias"}}"""
        val model = mapper.readValue(source, owner)
        val bindings = owner.getMethod("getBindings").invoke(model) as Map<*, *>
        assertEquals(mapOf("\$ref" to "#/components/channelBindings/Alias"), bindings, name)
        assertEquals(mapper.readTree(source).get("bindings"), mapper.valueToTree<JsonNode>(model).get("bindings"))
    }

    @ParameterizedTest(name = "named maps and aliases: {0}")
    @MethodSource("kinds")
    fun `components keep named maps typed protocol entries and reference aliases`(field: String, bindingType: Class<*>) {
        // AMQP defaults an omitted channel type to routingKey; include it for exact round-trip equality.
        val channelDefault = if (field == "channelBindings") "\"is\":\"routingKey\"," else ""
        val source = """{"$field":{
            "Shared":{"amqp":{$channelDefault"bindingVersion":"0.3.0"}},
            "Alias":{"${'$'}ref":"#/components/$field/Shared"},
            "Alias2":{"${'$'}ref":"#/components/$field/Alias"},"Empty":{}}}"""
        val model = mapper.readValue(source, Components::class.java)
        val entries = Components::class.java.getMethod("get" + field.replaceFirstChar { it.uppercase() }).invoke(model) as Map<*, *>
        val shared = assertInstanceOf(Map::class.java, entries["Shared"])
        assertInstanceOf(bindingType, shared["amqp"])
        assertEquals("#/components/$field/Shared", assertInstanceOf(Reference::class.java, entries["Alias"]).ref)
        assertEquals("#/components/$field/Alias", assertInstanceOf(Reference::class.java, entries["Alias2"]).ref)
        assertEquals(emptyMap<String, Any>(), entries["Empty"], "An empty named map must not become null")
        assertEquals(mapper.readTree(source).get(field), mapper.valueToTree<JsonNode>(model).get(field))
    }

    @ParameterizedTest(name = "invalid reference value: {0}")
    @MethodSource("invalidReferences")
    fun `invalid reference values propagate with the complete source path`(literal: String) {
        val failure = assertThrows(JsonMappingException::class.java) {
            mapper.readValue("""{"bindings":{"${'$'}ref":$literal}}""", Channel::class.java)
        }
        assertEquals(listOf("bindings", "\$ref"), failure.path.map { it.fieldName })
    }

    @ParameterizedTest(name = "nested binding failure: {0}")
    @MethodSource("kinds")
    fun `invalid protocol data is not swallowed inside component maps`(field: String, bindingType: Class<*>) {
        val failure = assertThrows(JsonMappingException::class.java) {
            mapper.readValue("""{"$field":{"Shared":{"amqp":{"bindingVersion":"0.3.0","unexpected":true}}}}""",
                Components::class.java)
        }
        assertEquals(listOf(field, "Shared", "amqp", "unexpected"), failure.path.map { it.fieldName }, bindingType.name)
    }

    /** Null is a nullable owner property; an explicit empty object has a different round-trip contract. */
    @ParameterizedTest(name = "empty owner map: {0}")
    @MethodSource("owners")
    fun `empty owner binding maps remain present and empty`(name: String, owner: Class<*>) {
        val model = mapper.readValue("""{"bindings":{}}""", owner)
        assertEquals(emptyMap<String, Any>(), owner.getMethod("getBindings").invoke(model), name)
        assertEquals(mapper.readTree("{}"), mapper.valueToTree<JsonNode>(model).get("bindings"), name)
    }

    /** Even an external/encoded URI is just text to the mapper; malformed ignored siblings are not decoded. */
    @ParameterizedTest(name = "unresolved URI and ignored siblings: {0}")
    @MethodSource("owners")
    fun `reference URI is retained exactly and its protocol siblings are ignored`(name: String, owner: Class<*>) {
        val uri = "other.json#/components/channelBindings/a~1b~0%20c+d"
        val source = """{"bindings":{"${'$'}ref":"$uri","amqp":{"unexpected":true}}}"""
        val model = mapper.readValue(source, owner)
        assertEquals(mapOf("\$ref" to uri), owner.getMethod("getBindings").invoke(model), name)
        assertEquals(mapper.readTree("""{"${'$'}ref":"$uri"}"""),
            mapper.valueToTree<JsonNode>(model).get("bindings"), name)
    }

    /** Component aliases have an extra name layer that must be retained in every Jackson failure path. */
    @ParameterizedTest(name = "invalid component reference: {0}")
    @MethodSource("kinds")
    fun `invalid named reference values retain their component path`(field: String, bindingType: Class<*>) {
        for (literal in listOf("17", "null", "{}", "[]")) {
            val failure = assertThrows(JsonMappingException::class.java) {
                mapper.readValue("""{"$field":{"Alias":{"${'$'}ref":$literal}}}""", Components::class.java)
            }
            assertEquals(listOf(field, "Alias", "\$ref"), failure.path.map { it.fieldName }, "${bindingType.name}: $literal")
        }
    }

    @ParameterizedTest(name = "invalid named map shape: {0}")
    @MethodSource("kinds")
    fun `named binding entries require objects rather than scalars arrays or null`(field: String, bindingType: Class<*>) {
        for (literal in listOf("17", "[]", "\"wrong\"", "null")) {
            val failure = assertThrows(JsonMappingException::class.java) {
                mapper.readValue("""{"$field":{"Shared":$literal}}""", Components::class.java)
            }
            assertEquals(listOf(field, "Shared"), failure.path.map { it.fieldName }, "${bindingType.name}: $literal")
        }
    }

    @ParameterizedTest(name = "invalid component collection shape: {0}")
    @MethodSource("kinds")
    fun `binding component collections require objects`(field: String, bindingType: Class<*>) {
        for (literal in listOf("17", "[]", "\"wrong\"")) {
            val failure = assertThrows(JsonMappingException::class.java) {
                mapper.readValue("""{"$field":$literal}""", Components::class.java)
            }
            assertEquals(listOf(field), failure.path.map { it.fieldName }, "${bindingType.name}: $literal")
        }
    }

    companion object {
        @JvmStatic fun owners(): Stream<Arguments> = Stream.of(
            Arguments.of("server", Server::class.java), Arguments.of("channel", Channel::class.java),
            Arguments.of("operation", Operation::class.java), Arguments.of("message", Message::class.java),
            Arguments.of("operation trait", OperationTrait::class.java), Arguments.of("message trait", MessageTrait::class.java)
        )

        @JvmStatic fun kinds(): Stream<Arguments> = Stream.of(
            Arguments.of("serverBindings", AMQPServerBinding::class.java),
            Arguments.of("channelBindings", AMQPChannelBinding::class.java),
            Arguments.of("operationBindings", AMQPOperationBinding::class.java),
            Arguments.of("messageBindings", AMQPMessageBinding::class.java)
        )

        @JvmStatic fun invalidReferences(): Stream<Arguments> = Stream.of("17", "null", "{}", "[]")
            .map { Arguments.of(it) }
    }
}
