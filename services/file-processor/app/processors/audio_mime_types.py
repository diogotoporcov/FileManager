def parse_processable_audio_mime_types(value: str) -> frozenset[str]:
    return frozenset(
        mime_type.split(";", 1)[0].strip().lower()
        for mime_type in value.split(",")
        if mime_type.strip()
    )


def is_processable_audio_mime_type(mime_type: str | None, processable_mime_types: frozenset[str]) -> bool:
    if not mime_type:
        return False

    normalized = mime_type.split(";", 1)[0].strip().lower()
    return normalized in processable_mime_types
