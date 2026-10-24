# Binding-map references

This fix covers AsyncAPI 3.0 server, channel, operation and message binding maps.
The [Components Object](https://www.asyncapi.com/docs/reference/specification/v3.0.0#componentsObject)
contains named reusable maps, with protocol names inside each concrete map:

```json
{
  "channelBindings": {
    "Shared": {"amqp": {"is": "routingKey", "exchange": {"name": "events", "type": "topic"}}},
    "Alias": {"$ref": "#/components/channelBindings/Shared"}
  }
}
```

`Components.getChannelBindings().get("Shared")` returns a map whose `amqp` entry is a
typed `AMQPChannelBinding`. The `Alias` entry returns a model `Reference`. The same
representation applies to the other three component binding collections. This corrects
the previous flat interpretation of component names as protocol names.

An owner's `getBindings()` retains its `Map<String, Object>` signature. Inline maps
contain typed protocol entries. If the whole `bindings` value is a Reference Object,
the map contains the single `$ref` key and its original URI string. The model neither
follows aliases nor fetches referenced documents. Callers such as Asyncwright use a
separate resolver to obtain the concrete map, then select the protocol entry.

Invalid reference values and protocol mapping errors propagate as `JsonMappingException`
with the complete component/entry/protocol/property path. No partial maps are returned.
Existing unknown-protocol behavior is unchanged; Asyncwright's preservation guard rejects
data the typed model cannot retain. Traits use the shared mapper but remain outside
Asyncwright's supported validation scope; no trait merging is introduced.

`BindingMapReferencesTest` covers all six owner classes (including the two trait classes),
four named map types, alias round trips, invalid `$ref` values and nested mapping failures.
The v3 component fixture builders and six JSON fixtures now contain the missing protocol
map layer. The existing v2 fixtures are unchanged.

Additional regressions distinguish empty maps from nullable properties, reject scalar/array
component collections and scalar/array/null named entries, and verify complete error paths
for invalid alias values. Encoded or external URI strings are retained verbatim by the model;
malformed protocol siblings of a whole-map reference are ignored. A host must validate the
document and apply its reference policy separately. These model-only tests do not enable
external references or traits in Asyncwright.

The source fix is on `fix/binding-map-references`. Local distribution testing uses
`build/binding-map-references-distribution` and `.4-SNAPSHOT`, without replacing public `.3`.
No new release is published by this change.

## IDE analysis of serialization fixtures

The JSON files below exercise model serialization/deserialization, not validation of a
complete AsyncAPI application or reference resolution:

- `asyncapi-core/src/test/resources/json/v3/3.0.0/model/asyncapi.json`
- `asyncapi-core/src/test/resources/json/v3/3.0.0/model/asyncapi - extended.json`
- `asyncapi-core/src/test/resources/json/v3/3.0.0/model/asyncapi - wrongly extended.json`
- the corresponding `components/components*.json` files in the same resource directory.

Some references are intentionally left without targets in these mapping fixtures. The
`wrongly extended` inputs must retain invalid extension names because the tests require
mapping to fail. Positive/extended fixtures compare typed values and, in two tests, exact
JSON text; they do not assert full document conformance. Repairing references or changing
data to satisfy an IDE's AsyncAPI schema would change the condition under test.

For IntelliJ's editor, select **Highlight → Syntax** in the inspection widget for these
files, retaining JSON syntax checks. When running project-wide **Inspect Code**, use a
local analysis scope that excludes these specific data files, or a local inspection profile
that disables only their schema/reference inspections. Keep analysis enabled for Java and
Kotlin tests and for actual AsyncAPI contracts. These IDE preferences are local; they do
not require committing `.idea` files or adding suppressions inside JSON fixtures.

See [IntelliJ file highlighting and inspection settings](https://www.jetbrains.com/help/idea/disabling-and-enabling-inspections.html#change-the-highlighting-level-for-a-file).
