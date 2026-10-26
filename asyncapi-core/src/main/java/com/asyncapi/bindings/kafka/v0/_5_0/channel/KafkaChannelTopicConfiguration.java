package com.asyncapi.bindings.kafka.v0._5_0.channel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * API-relevant Kafka topic configuration, including optional properties not enumerated by the binding.
 * Additional values remain JSON data; reference-like objects are never resolved by this model.
 * <p>
 * This is a data model, not a schema validator or Kafka client. Bean Validation annotations
 * describe minima; callers must run validation separately. In particular, retaining an extra
 * value does not make it valid under a particular binding version's document schema.
 * <p>
 * Only explicitly annotated wire properties participate in Jackson mapping. Java accessor
 * names such as {@code retentionMs} remain ordinary extra keys rather than aliases of
 * {@code retention.ms}. This also prevents JSON from replacing the backing extras map.
 * Retention accessors and builders use {@link Long}; consumers of the former Integer API
 * must recompile.
 *
 * @see <a href="https://github.com/asyncapi/bindings/blob/master/kafka/README.md#topicConfiguration">Kafka channel binding</a>
 * @see <a href="https://kafka.apache.org">Kafka</a>
 * @author Pavel Bodiachevskii
 * @version 0.5.0
 * @since 1.0.0-RC2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class KafkaChannelTopicConfiguration {

    /**
     * The <a href="https://kafka.apache.org/documentation/#topicconfigs_cleanup.policy">cleanup.policy</a> configuration option.
     * <p>
     * array may only contain delete and/or compact
     */
    @Nullable
    @JsonProperty("cleanup.policy")
    private List<KafkaChannelTopicCleanupPolicy> cleanupPolicy;

    /**
     * The <a href="https://kafka.apache.org/documentation/#topicconfigs_retention.ms">retention.ms</a> configuration option.
     * Uses the signed 64-bit Kafka range; {@code -1} denotes unlimited retention.
     */
    @Nullable
    @jakarta.validation.constraints.Min(
            value = -1,
            message = "retention.ms must be greater or equals to -1"
    )
    @JsonProperty("retention.ms")
    @JsonPropertyDescription("The [`retention.ms`](https://kafka.apache.org/documentation/#topicconfigs_retention.ms) configuration option.")
    private Long retentionMs;

    /**
     * The <a href="https://kafka.apache.org/documentation/#topicconfigs_retention.bytes">retention.bytes</a> configuration option.
     * Uses the signed 64-bit Kafka range; {@code -1} denotes unlimited retention.
     */
    @Nullable
    @jakarta.validation.constraints.Min(
            value = -1,
            message = "retention.bytes must be greater or equals to -1"
    )
    @JsonProperty("retention.bytes")
    @JsonPropertyDescription("The [`retention.bytes`](https://kafka.apache.org/documentation/#topicconfigs_retention.bytes) configuration option.")
    private Long retentionBytes;

    /**
     * The <a href="https://kafka.apache.org/documentation/#topicconfigs_delete.retention.ms">delete.retention.ms</a> configuration option.
     * Uses the nonnegative signed 64-bit Kafka range.
     */
    @Nullable
    @jakarta.validation.constraints.Min(
            value = 0,
            message = "delete.retention.ms must be greater or equals to 0"
    )
    @JsonProperty("delete.retention.ms")
    @JsonPropertyDescription("The [`delete.retention.ms`](https://kafka.apache.org/documentation/#topicconfigs_delete.retention.ms) configuration option.")
    private Long deleteRetentionMs;

    /**
     * The <a href="https://kafka.apache.org/documentation/#topicconfigs_max.message.bytes">max.message.bytes</a> configuration option.
     * Deliberately remains an Integer: the retention API change does not widen message size.
     */
    @Nullable
    @jakarta.validation.constraints.Min(
            value = 0,
            message = "max.message.bytes must be greater or equals to 0"
    )
    @JsonProperty("max.message.bytes")
    @JsonPropertyDescription("The [`max.message.bytes`](https://kafka.apache.org/documentation/#topicconfigs_max.message.bytes) configuration option.")
    private Integer maxMessageBytes;

    /**
     * It shows whether the schema validation for the message key is enabled.
     * <p>
     * Vendor specific config.
     * For more details: <a href="https://docs.confluent.io/platform/current/installation/configuration/topic-configs.html#confluent-key-schema-validation">confluent.key.schema.validation</a>
     */
    @Nullable
    @JsonProperty("confluent.key.schema.validation")
    private Boolean confluentKeySchemaValidation;

    /**
     * The name of the schema lookup strategy for the message key.
     * <p>
     * Vendor specific config.
     * For more details: <a href="https://docs.confluent.io/platform/current/installation/configuration/topic-configs.html#confluent-key-subject-name-strategy">confluent.key.subject.name.strategy</a>
     */
    @Nullable
    @JsonProperty("confluent.key.subject.name.strategy")
    private String confluentKeySubjectNameStrategy;

    /**
     * It shows whether the schema validation for the message value is enabled.
     * <p>
     * Vendor specific config.
     * For more details: <a href="https://docs.confluent.io/platform/current/installation/configuration/topic-configs.html#confluent-value-schema-validation">confluent.value.schema.validation</a>
     */
    @Nullable
    @JsonProperty("confluent.value.schema.validation")
    private Boolean confluentValueSchemaValidation;

    /**
     * The name of the schema lookup strategy for the message value.
     * <p>
     * Vendor specific config.
     * For more details: <a href="https://docs.confluent.io/platform/current/installation/configuration/topic-configs.html#confluent-value-subject-name-strategy">confluent.value.subject.name.strategy</a>
     */
    @Nullable
    @JsonProperty("confluent.value.subject.name.strategy")
    private String confluentValueSubjectNameStrategy;

    /**
     * Optional topic configuration values, flattened into the same JSON object as typed fields.
     * No bean setter is generated: a wire property named additionalProperties is itself valid data,
     * rather than an instruction to replace this container. The builder still accepts the map.
     */
    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, JsonNode> additionalProperties = new LinkedHashMap<>();

    /**
     * Returns the live additional configuration with original property names.
     * The default map is mutable and insertion ordered; a caller-supplied map keeps its own behavior.
     * Empty configuration emits no extra wrapper property. Known fields keep their typed accessors.
     * Use those accessors for known wire names; this map is for the remaining configuration.
     * <p>
     * The returned map and its JSON nodes are live, not defensive copies. Removing an entry
     * removes that wire property; a {@link NullNode} represents a present JSON null instead.
     * Builders and all-arguments constructors accept a map directly; an explicitly null map
     * is initialized lazily, just like the default empty configuration.
     * @return additional JSON values, including explicit null nodes
     */
    public Map<String, JsonNode> getAdditionalProperties() {
        if (additionalProperties == null) additionalProperties = new LinkedHashMap<>();
        return additionalProperties;
    }

    /**
     * A non-bean accessor keeps the any-getter separate from setterless Map properties.
     * A same-named JSON value must remain data instead of merging into the backing map.
     * Only explicitly annotated wire fields and this any-getter participate in mapping.
     * @return flattened additional values, without a container-named wire property
     */
    @JsonAnyGetter
    private Map<String, JsonNode> additionalValuesForSerialization() {
        return getAdditionalProperties();
    }

    /**
     * Retains an unrecognized configuration value without interpreting schemas or reference objects.
     * This accepts ordinary Kafka option names; the specification-extension x- prefix is not required.
     * Replaces an existing extra value with the same name. Values are retained as supplied
     * without copying or resolving them. For explicit JSON null retention through a mapper
     * with NON_NULL content inclusion, use this method or insert a {@link NullNode} in the map.
     * @param name original JSON property name
     * @param value JSON data; a Java null is retained as an explicit JSON null
     * @throws NullPointerException if the property name is null
     */
    @JsonAnySetter
    public void putAdditionalProperty(String name, JsonNode value) {
        getAdditionalProperties().put(Objects.requireNonNull(name, "name"), value == null ? NullNode.getInstance() : value);
    }

}
