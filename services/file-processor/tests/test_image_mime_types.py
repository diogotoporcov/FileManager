from app.processors.image_mime_types import is_processable_image_mime_type, parse_processable_image_mime_types


def test_parse_processable_image_mime_types_normalizes_entries():
    processable = parse_processable_image_mime_types(" image/jpeg , IMAGE/X-ICON; charset=binary ,, image/png ")

    assert processable == frozenset({"image/jpeg", "image/x-icon", "image/png"})


def test_is_processable_image_mime_type_normalizes_input():
    processable = parse_processable_image_mime_types("image/jpeg,image/x-icon")

    assert is_processable_image_mime_type("IMAGE/JPEG; charset=binary", processable) is True
    assert is_processable_image_mime_type(" image/x-icon ", processable) is True
    assert is_processable_image_mime_type("image/svg+xml", processable) is False
    assert is_processable_image_mime_type("", processable) is False
    assert is_processable_image_mime_type(None, processable) is False
