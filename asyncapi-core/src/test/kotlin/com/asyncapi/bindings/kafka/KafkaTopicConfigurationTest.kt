package com.asyncapi.bindings.kafka

import com.asyncapi.schemas.asyncapi.Reference
import com.asyncapi.v3._0_0.model.AsyncAPI
import com.asyncapi.v3._0_0.model.channel.Channel
import com.asyncapi.v3._0_0.model.component.Components
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.Min
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.lang.reflect.InvocationTargetException
import java.util.stream.Stream

/**
 * Kafka model regressions for both topic-configuration versions. Mapping is lossless data access,
 * not broker validation: Bean Validation minima remain metadata and no references are resolved.
 * Reflection shares the cases without introducing a new common public model superclass.
 */
class KafkaTopicConfigurationTest {
    // Mirror the host's precision and NON_NULL policy: explicit JSON null nodes must still survive.
    private val mapper = ObjectMapper()
        .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))

    @ParameterizedTest(name = "{0}: {2} keeps the long range")
    @MethodSource("retentionFields")
    fun `retention fields round trip long values without narrowing`(
        version: String, type: Class<*>, wire: String, property: String, minimum: Long
    ) {
        val getter = type.getMethod("get$property")
        assertEquals(Long::class.javaObjectType, getter.returnType, version)
        // Long.MIN_VALUE is representable data. The host, not Jackson, enforces the annotated minimum.
        for (value in listOf(Long.MIN_VALUE, minimum, 0L, 1L, Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong() + 1, 5_000_000_000L, Long.MAX_VALUE)) {
            val source = """{"$wire":$value}"""
            val model = mapper.readValue(source, type)
            assertEquals(value, getter.invoke(model), "$version: $source")
            assertEquals(mapper.readTree(source), mapper.valueToTree<JsonNode>(model))
            assertEquals(value, getter.invoke(mapper.readValue(mapper.writeValueAsBytes(model), type)))
        }
    }

    @ParameterizedTest(name = "{0}: {2} rejects long overflow")
    @MethodSource("retentionFields")
    fun `values outside long range fail at the typed field`(
        version: String, type: Class<*>, wire: String, property: String, minimum: Long
    ) {
        for (literal in listOf("9223372036854775808", "-9223372036854775809", "{}", "[]", "\"not a number\"")) {
            val error = assertThrows(JsonMappingException::class.java) {
                mapper.readValue("""{"$wire":$literal}""", type)
            }
            assertEquals(listOf(wire), error.path.map { it.fieldName }, "$version $property minimum $minimum: $literal")
        }
    }

    /** Null/absent typed fields stay optional and cannot migrate into the extra-value map. */
    @ParameterizedTest(name = "{0}: {2} stays nullable")
    @MethodSource("retentionFields")
    fun `nullable fields builder and setters keep their explicit long API`(
        version: String, type: Class<*>, wire: String, property: String, minimum: Long
    ) {
        for (source in listOf("{}", """{"$wire":null}""")) {
            val model = mapper.readValue(source, type)
            assertNull(type.getMethod("get$property").invoke(model))
            assertTrue(extras(type, model).isEmpty())
        }
        val bean = type.getConstructor().newInstance()
        val setter = type.getMethod("set$property", Long::class.javaObjectType)
        setter.invoke(bean, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, type.getMethod("get$property").invoke(bean))
        setter.invoke(bean, null)
        assertNull(type.getMethod("get$property").invoke(bean))
        val builder = type.getMethod("builder").invoke(null)
        val name = property.replaceFirstChar { it.lowercase() }
        builder.javaClass.getMethod(name, Long::class.javaObjectType).invoke(builder, Long.MAX_VALUE)
        val model = builder.javaClass.getMethod("build").invoke(builder)
        assertEquals(Long.MAX_VALUE, type.getMethod("get$property").invoke(model), version)
        assertEquals(minimum, type.getDeclaredField(name).getAnnotation(Min::class.java).value)
    }

    @ParameterizedTest(name = "{0}: arbitrary JSON configuration")
    @MethodSource("versions")
    fun `additional configuration stays flat and preserves JSON values including reference like data`(
        version: String, type: Class<*>
    ) {
        val source = """
            {
              "retention.ms":3000000000,
              "retention.bytes":5000000000,
              "delete.retention.ms":3000000000,
              "max.message.bytes":2147483647,
              "compression.type":"zstd",
              "large.option":922337203685477580812345,
              "precise.option":0.12345678901234567890123456789,
              "nullable.option":null,
              "array.option":[1,true,null,{"${'$'}ref":"not-a-reference.json"}],
              "object.option":{"key":{"${'$'}ref":"https://example.invalid/no-fetch"},"schema":{"type":"string"}},
              "additionalProperties":{"retention.ms":7},
              "${'$'}ref":"literal-value"
            }
        """.trimIndent()
        val expected = mapper.readTree(source)
        val model = mapper.readValue(source, type)
        assertEquals(expected, mapper.valueToTree<JsonNode>(model), version)
        val properties = extras(type, model)
        assertEquals(expected.size() - 4, properties.size)
        assertFalse(properties.containsKey("retention.ms"))
        assertEquals("zstd", (properties["compression.type"] as JsonNode).textValue())
        assertTrue((properties["nullable.option"] as JsonNode).isNull)
        assertEquals(expected.path("additionalProperties"), properties["additionalProperties"])
        assertEquals(expected, mapper.valueToTree<JsonNode>(mapper.readValue(mapper.writeValueAsBytes(model), type)))
    }

    /** Auto-detection must not reinterpret ordinary wire keys as Java field names or accessors. */
    @ParameterizedTest(name = "{0}: Java names remain JSON data")
    @MethodSource("versions")
    fun `bean like and escaped property names do not override typed wire fields`(version: String, type: Class<*>) {
        val source = """
            {"retention.ms":3000000000,"retentionMs":7,"getRetentionMs":8,"cleanupPolicy":"literal",
             "maxMessageBytes":9,"":null,"a/b~c%20+":"żółć","additionalProperties":{"retention.ms":11}}
        """.trimIndent()
        val model = mapper.readValue(source, type)
        val properties = extras(type, model)
        assertEquals(3_000_000_000L, type.getMethod("getRetentionMs").invoke(model), version)
        assertNull(type.getMethod("getMaxMessageBytes").invoke(model))
        assertNull(type.getMethod("getCleanupPolicy").invoke(model))
        assertEquals(mapper.readTree(source), mapper.valueToTree<JsonNode>(model))
        assertEquals(7, (properties["retentionMs"] as JsonNode).intValue())
        assertTrue((properties[""] as JsonNode).isNull)
        assertEquals("żółć", (properties["a/b~c%20+"] as JsonNode).textValue())
    }

    /** Null is a valid value but not a valid property name; failure must leave the map unchanged. */
    @ParameterizedTest(name = "{0}: null name is rejected atomically")
    @MethodSource("versions")
    fun `null property name fails without discarding existing data`(version: String, type: Class<*>) {
        val model = type.getConstructor().newInstance()
        val put = type.getMethod("putAdditionalProperty", String::class.java, JsonNode::class.java)
        put.invoke(model, "compression.type", mapper.nodeFactory.textNode("zstd"))
        val before = mapper.valueToTree<JsonNode>(model).deepCopy<JsonNode>()
        val wrapped = assertThrows(InvocationTargetException::class.java) { put.invoke(model, null, null) }
        assertInstanceOf(NullPointerException::class.java, wrapped.cause)
        assertEquals(before, mapper.valueToTree<JsonNode>(model), version)
    }

    /** Lombok default initialization is bypassed when a caller explicitly supplies a null map. */
    @ParameterizedTest(name = "{0}: explicitly null containers recover")
    @MethodSource("versions")
    fun `null maps from builder and all arguments constructor stay independent and serializable`(
        version: String, type: Class<*>
    ) {
        val builder = type.getMethod("builder").invoke(null)
        builder.javaClass.getMethod("additionalProperties", Map::class.java).invoke(builder, null)
        val first = builder.javaClass.getMethod("build").invoke(builder)
        val second = builder.javaClass.getMethod("build").invoke(builder)
        val constructor = type.constructors.single { it.parameterCount > 0 }
        val constructed = constructor.newInstance(*arrayOfNulls<Any>(constructor.parameterCount))
        for (model in listOf(first, second, constructed)) {
            // Serialize before requesting the public getter to exercise lazy recovery in the any-getter.
            assertEquals(mapper.createObjectNode(), mapper.valueToTree<JsonNode>(model), version)
            assertTrue(extras(type, model).isEmpty())
        }
        val put = type.getMethod("putAdditionalProperty", String::class.java, JsonNode::class.java)
        put.invoke(first, "nullable", null)
        assertEquals(mapper.readTree("""{"nullable":null}"""), mapper.valueToTree<JsonNode>(first))
        assertTrue(extras(type, second).isEmpty())
        assertTrue(extras(type, constructed).isEmpty())
    }

    /** The API exposes live values; replacement, explicit null and removal have distinct wire effects. */
    @ParameterizedTest(name = "{0}: live map mutation")
    @MethodSource("versions")
    fun `live map and supplied nodes reflect replacements removals and caller changes`(version: String, type: Class<*>) {
        val mutable = linkedMapOf<String, JsonNode>("first" to mapper.readTree("1"))
        val builder = type.getMethod("builder").invoke(null)
        builder.javaClass.getMethod("additionalProperties", Map::class.java).invoke(builder, mutable)
        val model = builder.javaClass.getMethod("build").invoke(builder)
        assertSame(mutable, extras(type, model), version)
        val nested = mapper.createObjectNode().put("value", "before")
        val put = type.getMethod("putAdditionalProperty", String::class.java, JsonNode::class.java)
        put.invoke(model, "custom", nested)
        nested.put("value", "after")
        put.invoke(model, "first", mapper.readTree("2"))
        put.invoke(model, "nullable", null)
        assertEquals(mapper.readTree("""{"first":2,"custom":{"value":"after"},"nullable":null}"""),
            mapper.valueToTree<JsonNode>(model))
        mutable.remove("first")
        mutable.remove("nullable")
        val actual = mapper.valueToTree<JsonNode>(model)
        assertEquals(mapper.readTree("""{"custom":{"value":"after"}}"""), actual)
        assertEquals(listOf("custom"), actual.fieldNames().asSequence().toList())
    }

    /** Root-level deserializers must retain both inline maps and URI aliases without a resolver. */
    @ParameterizedTest(name = "{0}: AsyncAPI document preserves references")
    @MethodSource("versions")
    fun `whole document retains Kafka configuration and binding map aliases`(version: String, type: Class<*>) {
        val source = """
            {"asyncapi":"3.0.0","info":{"title":"Kafka configuration","version":"1"},
             "channels":{
               "inline":{"address":"orders","bindings":{"kafka":{"bindingVersion":"$version",
                 "topicConfiguration":{"retention.ms":3000000000,"custom":{"${'$'}ref":"literal.json"}}}}},
               "alias":{"address":"orders","bindings":{"${'$'}ref":"#/components/channelBindings/Alias"}}},
             "components":{"channelBindings":{
               "Shared":{"kafka":{"bindingVersion":"$version",
                 "topicConfiguration":{"retention.ms":5000000000,"compression.type":"zstd"}}},
               "Alias":{"${'$'}ref":"#/components/channelBindings/Shared"}}}}
        """.trimIndent()
        val document = mapper.readValue(source, AsyncAPI::class.java)
        val channels = requireNotNull(document.channels)
        val inline = assertInstanceOf(Channel::class.java, channels["inline"])
        val binding = requireNotNull(inline.bindings)["kafka"]!!
        val configuration = binding.javaClass.getMethod("getTopicConfiguration").invoke(binding)
        assertInstanceOf(type, configuration)
        assertEquals(3_000_000_000L, type.getMethod("getRetentionMs").invoke(configuration), version)
        assertEquals("literal.json", (extras(type, configuration)["custom"] as JsonNode).path("${'$'}ref").textValue())
        val alias = assertInstanceOf(Channel::class.java, channels["alias"])
        assertEquals(mapOf("${'$'}ref" to "#/components/channelBindings/Alias"), alias.bindings)
        val aliases = requireNotNull(requireNotNull(document.components).channelBindings)
        assertEquals("#/components/channelBindings/Shared", assertInstanceOf(Reference::class.java, aliases["Alias"]).ref)
        val expected = mapper.readTree(source)
        val serialized = mapper.valueToTree<JsonNode>(document)
        assertEquals(expected.path("channels"), serialized.path("channels"))
        assertEquals(expected.path("components"), serialized.path("components"))
    }

    /** The map name must never become a bean setter that swallows a legitimate wire property. */
    @ParameterizedTest(name = "{0}: additionalProperties is also a wire key")
    @MethodSource("versions")
    fun `container named properties and default mappers retain unknown keys`(version: String, type: Class<*>) {
        val plain = ObjectMapper()
        for (key in listOf("additionalProperties", "additionalValuesForSerialization", "getAdditionalProperties")) {
            for (literal in listOf("\"literal\"", "null", "7", "[]", """{"${'$'}ref":"literal.json"}""")) {
                val source = """{"compression.type":"lz4","$key":$literal,"last.option":true}"""
                val model = plain.readValue(source, type)
                val actual = plain.valueToTree<JsonNode>(model)
                assertEquals(plain.readTree(literal), actual.path(key), "$version $key")
                assertEquals("lz4", actual.path("compression.type").textValue())
                assertTrue(actual.path("last.option").booleanValue())
                assertEquals(setOf(key, "compression.type", "last.option"), extras(type, model).keys)
            }
        }
    }

    @ParameterizedTest(name = "{0}: builder extras and empty maps")
    @MethodSource("versions")
    fun `constructors builders equality and API additions retain extra values`(version: String, type: Class<*>) {
        val empty = type.getConstructor().newInstance()
        assertTrue(extras(type, empty).isEmpty())
        assertEquals(mapper.createObjectNode(), mapper.valueToTree<JsonNode>(empty))
        val first = type.getMethod("builder").invoke(null)
        val second = type.getMethod("builder").invoke(null)
        val left = first.javaClass.getMethod("build").invoke(first)
        val right = second.javaClass.getMethod("build").invoke(second)
        val extra = mapper.readTree("""{"${'$'}ref":"literal.json"}""")
        type.getMethod("putAdditionalProperty", String::class.java, JsonNode::class.java).invoke(left, "custom", extra)
        assertNotEquals(left, right)
        assertTrue(extras(type, right).isEmpty(), "Builder defaults must not share a map")
        type.getMethod("putAdditionalProperty", String::class.java, JsonNode::class.java).invoke(right, "custom", extra)
        assertEquals(left, right, version)
        assertEquals(left.hashCode(), right.hashCode())
        type.getMethod("putAdditionalProperty", String::class.java, JsonNode::class.java).invoke(left, "nullable.option", null)
        assertTrue(mapper.valueToTree<JsonNode>(left).path("nullable.option").isNull)
        val builder = type.getMethod("builder").invoke(null)
        builder.javaClass.getMethod("additionalProperties", Map::class.java).invoke(builder, mapOf("custom" to extra))
        assertEquals(mapper.readTree("""{"custom":{"${'$'}ref":"literal.json"}}"""),
            mapper.valueToTree<JsonNode>(builder.javaClass.getMethod("build").invoke(builder)))
    }

    @ParameterizedTest(name = "{0}: named bindings and aliases")
    @MethodSource("versions")
    fun `component maps keep typed Kafka configuration and unresolved aliases`(version: String, type: Class<*>) {
        val source = """{"channelBindings":{
            "Shared":{"kafka":{"bindingVersion":"$version","topicConfiguration":{"retention.ms":3000000000,"compression.type":"zstd"}}},
            "Alias":{"${'$'}ref":"#/components/channelBindings/Shared"}}}"""
        val components = mapper.readValue(source, Components::class.java)
        val maps = requireNotNull(components.channelBindings)
        val shared = maps["Shared"] as Map<*, *>
        val binding = shared["kafka"]!!
        val configuration = binding.javaClass.getMethod("getTopicConfiguration").invoke(binding)
        assertInstanceOf(type, configuration)
        assertEquals(3_000_000_000L, type.getMethod("getRetentionMs").invoke(configuration))
        assertEquals("zstd", (extras(type, configuration)["compression.type"] as JsonNode).textValue())
        assertEquals("#/components/channelBindings/Shared", assertInstanceOf(Reference::class.java, maps["Alias"]).ref)
        val error = assertThrows(JsonMappingException::class.java) {
            mapper.readValue(source.replace("3000000000", "9223372036854775808"), Components::class.java)
        }
        assertEquals(listOf("channelBindings", "Shared", "kafka", "topicConfiguration", "retention.ms"),
            error.path.map { it.fieldName })
        val bindingType = Class.forName("com.asyncapi.bindings.kafka.v0._${version.split('.')[1]}_0.channel.KafkaChannelBinding")
        assertThrows(JsonMappingException::class.java) {
            mapper.readValue("""{"bindingVersion":"$version","unexpected":true}""", bindingType)
        }
    }

    /** Kafka's message-size option stays int32; only retention fields change API type. */
    @ParameterizedTest(name = "{0}: message size stays int32")
    @MethodSource("versions")
    fun `max message bytes retains its integer type and overflow boundary`(version: String, type: Class<*>) {
        assertEquals(Int::class.javaObjectType, type.getMethod("getMaxMessageBytes").returnType)
        for (value in listOf(0, Int.MAX_VALUE)) {
            val model = mapper.readValue("""{"max.message.bytes":$value}""", type)
            assertEquals(value, type.getMethod("getMaxMessageBytes").invoke(model), version)
        }
        assertThrows(JsonMappingException::class.java) {
            mapper.readValue("""{"max.message.bytes":2147483648}""", type)
        }
    }

    private fun extras(type: Class<*>, model: Any): Map<*, *> =
        type.getMethod("getAdditionalProperties").invoke(model) as Map<*, *>

    companion object {
        @JvmStatic fun versions(): Stream<Arguments> = Stream.of(
            Arguments.of("0.4.0", com.asyncapi.bindings.kafka.v0._4_0.channel.KafkaChannelTopicConfiguration::class.java),
            Arguments.of("0.5.0", com.asyncapi.bindings.kafka.v0._5_0.channel.KafkaChannelTopicConfiguration::class.java)
        )

        @JvmStatic fun retentionFields(): Stream<Arguments> =
            listOf("0.4.0" to com.asyncapi.bindings.kafka.v0._4_0.channel.KafkaChannelTopicConfiguration::class.java,
                "0.5.0" to com.asyncapi.bindings.kafka.v0._5_0.channel.KafkaChannelTopicConfiguration::class.java)
                .flatMap { (version, type) -> listOf(
                    Arguments.of(version, type, "retention.ms", "RetentionMs", -1L),
                    Arguments.of(version, type, "retention.bytes", "RetentionBytes", -1L),
                    Arguments.of(version, type, "delete.retention.ms", "DeleteRetentionMs", 0L)
                ) }.stream()
    }
}
