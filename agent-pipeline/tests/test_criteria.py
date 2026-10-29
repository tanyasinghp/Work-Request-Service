"""The acceptance-criteria schema: strict locally, reduced only for the model call."""

import json

from criteria import UNSUPPORTED_BY_STRUCTURED_OUTPUT, load_schema, parse_criteria, validate_criteria, wire_schema

VALID = {
    "title": "Flag stale requests",
    "criteria": [{"given": "a request 31 days old", "when": "the batch is processed", "then": "STALE_REQUEST"}],
    "affectedClasses": ["ProblemCode", "WorkRequestValidator"],
    "outOfScope": ["Configurable threshold"],
}


def with_change(**changes):
    return {**VALID, **changes}


def test_accepts_valid_criteria():
    assert validate_criteria(VALID) == []


def test_requires_every_top_level_field():
    for field in ("title", "criteria", "affectedClasses", "outOfScope"):
        data = {key: value for key, value in VALID.items() if key != field}
        assert any(f"'{field}' is a required property" in error for error in validate_criteria(data))


def test_requires_given_when_then_in_each_criterion():
    errors = validate_criteria(with_change(criteria=[{"given": "a request", "when": "processed"}]))
    assert any("criteria/0" in error and "'then'" in error for error in errors)


def test_rejects_empty_criteria_and_classes():
    assert validate_criteria(with_change(criteria=[]))
    assert validate_criteria(with_change(affectedClasses=[]))


def test_rejects_class_names_that_are_not_simple_java_names():
    for bad in ("workRequestValidator", "com.tanya.Problem", "Problem.java", "My Class"):
        assert validate_criteria(with_change(affectedClasses=[bad])), bad


def test_rejects_unknown_fields():
    assert any("Additional properties" in error for error in validate_criteria(with_change(notes="x")))


def test_parse_reports_invalid_json_with_position():
    criteria, errors = parse_criteria('{"title": "x",')
    assert criteria is None
    assert errors[0].startswith("not valid JSON")


def test_parse_returns_criteria_only_when_valid():
    assert parse_criteria(json.dumps(VALID)) == (VALID, [])
    criteria, errors = parse_criteria(json.dumps(with_change(title="x")))
    assert criteria is None and errors


def test_wire_schema_drops_keywords_structured_output_rejects_at_every_depth():
    wire = wire_schema(load_schema())

    def keywords(node):
        if isinstance(node, dict):
            yield from node.keys()
            for value in node.values():
                yield from keywords(value)
        elif isinstance(node, list):
            for item in node:
                yield from keywords(item)

    # Named literally: checking against the module's own constant would pass even if it lost an entry.
    used_by_our_schema = {"$schema", "$id", "minItems", "maxItems", "uniqueItems", "minLength", "pattern"}
    assert used_by_our_schema <= set(keywords(load_schema()))
    assert not set(keywords(wire)) & used_by_our_schema
    assert not set(keywords(wire)) & UNSUPPORTED_BY_STRUCTURED_OUTPUT
    assert wire["required"] == load_schema()["required"]
    assert wire["properties"]["criteria"]["items"]["additionalProperties"] is False


def test_wire_schema_does_not_loosen_local_validation():
    # The model sees the reduced schema; parse_criteria still enforces the full one.
    _, errors = parse_criteria(json.dumps(with_change(affectedClasses=["lowercase"])))
    assert errors
