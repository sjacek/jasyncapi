package com.asyncapi.serde

import com.asyncapi.bindings.amqp.AMQPChannelBinding
import com.asyncapi.schemas.asyncapi.AsyncAPISchema
import com.asyncapi.schemas.asyncapi.Reference
import com.asyncapi.v2._0_0.model.component.Components as ComponentsV20
import com.asyncapi.v2._6_0.model.component.Components as ComponentsV26
import com.asyncapi.v3._0_0.model.AsyncAPI
import com.asyncapi.v3._0_0.model.Tag
import com.asyncapi.v3._0_0.model.channel.Channel
import com.asyncapi.v3._0_0.model.component.Components as ComponentsV3
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.stream.Stream

/** Public, minimal regression cases: no application-specific contracts are required. */
@ResourceLock(Resources.SYSTEM_ERR)
class DeserializerErrorPropagationTest {
    private val mapper = ObjectMapper()

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDocuments")
    fun `invalid entries fail the entire mapping without printing to stderr`(
        description: String, target: Class<*>, document: String, expectedPath: List<String>
    ) {
        val captured = ByteArrayOutputStream()
        val previous = System.err
        val exception = try {
            PrintStream(captured, true, StandardCharsets.UTF_8).use { stream ->
                System.setErr(stream)
                assertThrows(JsonMappingException::class.java) { mapper.readValue(document, target) }
            }
        } finally {
            System.setErr(previous)
        }

        assertEquals("", captured.toString(StandardCharsets.UTF_8), description)
        val path = exception.path.map { it.fieldName ?: "[${it.index}]" }
        assertEquals(expectedPath, path, exception.message)
    }

    @Test
    fun `channel maps retain both objects and references`() {
        val model = mapper.readValue(
            """{"channels":{"base":{"address":"events"},"alias":{"${'$'}ref":"#/channels/base"}}}""",
            AsyncAPI::class.java
        )
        assertEquals(2, model.channels!!.size)
        assertEquals("events", (model.channels!!["base"] as Channel).address)
        assertEquals("#/channels/base", (model.channels!!["alias"] as Reference).ref)
    }

    @Test
    fun `lists retain object and reference order`() {
        val model = mapper.readValue(
            """{"tags":[{"name":"first"},{"${'$'}ref":"#/components/tags/shared"},{"name":"last"}]}""",
            Channel::class.java
        )
        val tags = model.tags!!
        assertEquals(3, tags.size)
        assertEquals("first", (tags[0] as Tag).name)
        assertEquals("#/components/tags/shared", (tags[1] as Reference).ref)
        assertEquals("last", (tags[2] as Tag).name)
    }

    @Test
    fun `AMQP binding remains typed`() {
        val model = mapper.readValue(
            """{"bindings":{"amqp":{"is":"routingKey","exchange":{"name":"events","type":"topic","durable":true},"bindingVersion":"0.3.0"}}}""",
            Channel::class.java
        )
        assertInstanceOf(AMQPChannelBinding::class.java, model.bindings!!["amqp"])
        assertEquals("events", mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(model)
            .at("/bindings/amqp/exchange/name").asText())
    }

    @ParameterizedTest
    @MethodSource("componentTypes")
    fun `schema maps retain references and the schema fallback`(target: Class<*>) {
        val document = """{"schemas":{
            "Base":{"type":"string"},
            "Alias":{"${'$'}ref":"#/components/schemas/Base"},
            "WithRefAndType":{"${'$'}ref":"#/components/schemas/Base","type":"string"}
        }}"""
        val model = mapper.readValue(document, target)
        val schemas = when (model) {
            is ComponentsV3 -> model.schemas!!
            is ComponentsV26 -> model.schemas!!
            is ComponentsV20 -> model.schemas!!
            else -> error("Unexpected components model")
        }
        assertEquals(3, schemas.size)
        assertInstanceOf(AsyncAPISchema::class.java, schemas["Base"])
        assertEquals("#/components/schemas/Base", (schemas["Alias"] as Reference).ref)
        val schema = schemas["WithRefAndType"] as AsyncAPISchema
        assertEquals("#/components/schemas/Base", schema.ref)
        assertEquals("string", schema.type)
    }

    @Test
    fun `operation and recursive schema references survive a round trip`() {
        val document = """{
            "channels":{"events":{"address":"events"}},
            "operations":{"send":{"action":"send","channel":{"${'$'}ref":"#/channels/events"}}},
            "components":{"schemas":{"Node":{"type":"object","properties":{
                "child":{"${'$'}ref":"#/components/schemas/Node"}
            }}}}
        }"""
        val model = mapper.readValue(document, AsyncAPI::class.java)
        val serialized = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(model)
        assertEquals("#/channels/events", serialized.at("/operations/send/channel/${'$'}ref").asText())
        assertEquals("#/components/schemas/Node",
            serialized.at("/components/schemas/Node/properties/child/${'$'}ref").asText())
        assertEquals(1, model.operations!!.size)
    }

    companion object {
        @JvmStatic
        fun componentTypes(): Stream<Arguments> = Stream.of(
            Arguments.of(ComponentsV20::class.java), Arguments.of(ComponentsV26::class.java),
            Arguments.of(ComponentsV3::class.java)
        )

        @JvmStatic
        fun invalidDocuments(): Stream<Arguments> = Stream.of(
            Arguments.of("channel map", AsyncAPI::class.java,
                """{"channels":{"valid":{"address":"events"},"broken":{"address":{}}}}""",
                listOf("channels", "broken", "address")),
            Arguments.of("operation with inlined channel", AsyncAPI::class.java,
                """{"operations":{"send":{"action":"send","channel":{"address":"events"}}}}""",
                listOf("operations", "send", "channel", "address")),
            Arguments.of("tag list", Channel::class.java,
                """{"tags":[{"name":"valid"},{"name":{}},{"name":"last"}]}""",
                listOf("tags", "[1]", "name")),
            Arguments.of("binding map", Channel::class.java,
                """{"bindings":{"amqp":{"is":"routingKey","exchange":{"name":"events","type":"topic","durable":"not-a-boolean"},"bindingVersion":"0.3.0"}}}""",
                listOf("bindings", "amqp", "exchange", "durable")),
            Arguments.of("AsyncAPI 2.6 schema map", ComponentsV26::class.java,
                """{"schemas":{"valid":{"type":"string"},"broken":{"type":"object","nullable":true}}}""",
                listOf("schemas", "broken", "nullable")),
            Arguments.of("AsyncAPI 2.0 schema map", ComponentsV20::class.java,
                """{"schemas":{"valid":{"type":"string"},"broken":{"type":"object","nullable":true}}}""",
                listOf("schemas", "broken", "nullable")),
            Arguments.of("AsyncAPI 3.0 schema map", ComponentsV3::class.java,
                """{"schemas":{"valid":{"type":"string"},"broken":{"type":"object","nullable":true}}}""",
                listOf("schemas", "broken", "nullable"))
        )
    }
}
