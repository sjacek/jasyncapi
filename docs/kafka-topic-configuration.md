# Kafka topic configuration

The published model **1.0.0-RC4-asyncwright.5** covers Kafka channel bindings
**0.4.0 and 0.5.0** for Asyncwright 0.2.0. Both are available from public repositories;
the model resolves from Maven Central without a local repository.
The published model .4 retains its original API and behavior.

Model preservation and document validity are separate concerns. Asyncwright's pinned
AsyncAPI schema permits extra topic-configuration properties for binding 0.5.0, while
the 0.4.0 schema rejects them. The model retains such data in either class; this does
not relax the host's 0.4.0 validation. Use binding 0.5.0 for additional configuration
in an Asyncwright-validated contract.

| Property | Java type after the fix | Existing validation metadata |
| --- | --- | --- |
| `retention.ms` | `Long` | Minimum -1; -1 denotes unlimited retention |
| `retention.bytes` | `Long` | Minimum -1; -1 denotes unlimited retention |
| `delete.retention.ms` | `Long` | Minimum 0 |
| `max.message.bytes` | `Integer` | Minimum 0 |

Retention values above 2³¹−1, up to `Long.MAX_VALUE`, retain their full value. Values
outside the signed 64-bit range produce a Jackson mapping error at the typed property.
The model retains Bean Validation annotations; deserialization does not itself execute
Bean Validation or connect to a broker. Asyncwright supplies document validation separately.

## Additional properties

Topic configuration allows optional properties beyond the named binding fields.
Both classes expose `getAdditionalProperties()` and
`putAdditionalProperty(String, JsonNode)`. Values are stored as JSON nodes and serialized
at the original object level through Jackson's any-getter/any-setter support.
There is no additional wrapper in the wire format and no requirement for an `x-` prefix.
The getter exposes the `additionalProperties` map, and the builder accepts that map directly.
The default map is mutable and insertion ordered. A supplied map and its JSON nodes
are retained directly, so their mutability and later changes remain visible; callers
needing independent values should supply their own copies. An explicitly null map
from a builder or all-arguments constructor is initialized lazily. The helper replaces
an existing value, rejects a null property name and converts a null value to `NullNode`.
Removing a map entry removes the wire property; inserting `NullNode` retains it as JSON null.
Use the helper or `NullNode` when a mapper excludes Java null map values.

For example, this configuration retains the typed retention fields, `compression.type`,
and every value of `custom.option`:

```json
{
  "retention.ms": 3000000000,
  "retention.bytes": 5000000000,
  "compression.type": "zstd",
  "custom.option": [null, {"$ref": "literal-data.json"}]
}
```

Objects, arrays, booleans, numbers and explicit JSON nulls remain data. Neither a property
called `$ref` nor a nested reference-like object triggers resolution. Even a wire property
literally named `additionalProperties` is retained as an ordinary extra value.
Known properties continue to use their typed fields; invalid values cannot fall back
into the extra-value map. Callers constructing a model should use typed accessors for
those known properties and reserve the extra map for other names.

Configure Jackson with `USE_BIG_INTEGER_FOR_INTS` and `USE_BIG_DECIMAL_FOR_FLOATS` when
exact arbitrary-precision JSON numbers are required. Asyncwright already uses that mapping
policy. A default mapper also accepts the added properties, subject to its own numeric policy.

## Java API

```java
import com.asyncapi.bindings.kafka.v0._5_0.channel.KafkaChannelTopicConfiguration;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

final class KafkaTopicExample {
    static KafkaChannelTopicConfiguration configuration() {
        KafkaChannelTopicConfiguration configuration = KafkaChannelTopicConfiguration.builder()
                .retentionMs(3_000_000_000L)
                .retentionBytes(5_000_000_000L)
                .maxMessageBytes(1_048_588)
                .build();
        configuration.putAdditionalProperty("compression.type", JsonNodeFactory.instance.textNode("zstd"));
        return configuration;
    }
}
```

The equivalent 0.4.0 class has the same additional-value API.

## Compatibility and verification

This is an intentional binary and source API change from model .4: getters, setters and
builder methods now use `Long`, and Java integer literals passed to those methods need
an `L` suffix. The all-arguments constructors also include the new map. Equality and
hash codes account for additional values. Recompile consumers and extensions that use
these APIs; do not treat .5 as a drop-in binary replacement for such consumers.
The production model still targets Java 8; model tests use JDK 21.

`KafkaTopicConfigurationTest` covers both versions: long boundaries and overflow,
nullable fields, builder/setter signatures, JSON round trips, arbitrary values and numeric
precision, the container-named wire property, independent builder defaults, equality/hashCode,
typed named binding maps, retained aliases and nested failure paths. Extra cases cover
Java-like and escaped wire names, null containers and names, live-map mutations and
whole-document mapping with inline bindings and aliases. The existing Kafka
tests and Adeo example use long literals without changing their JSON fixtures.

This is model-data support only. It does not by itself establish acceptance of the whole
Kafka profile, Avro, Schema Registry dependencies or broker/runtime integration.
