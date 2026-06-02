from app.processors.audio_mime_types import is_processable_audio_mime_type, parse_processable_audio_mime_types


def test_parse_processable_audio_mime_types_normalizes_entries():
    processable = parse_processable_audio_mime_types(" audio/mpeg , AUDIO/X-M4A; charset=binary ,, audio/flac ")

    assert processable == frozenset({"audio/mpeg", "audio/x-m4a", "audio/flac"})


def test_is_processable_audio_mime_type_handles_parameters_and_unknowns():
    processable = parse_processable_audio_mime_types("audio/mpeg,audio/wav")

    assert is_processable_audio_mime_type("audio/mpeg; charset=binary", processable) is True
    assert is_processable_audio_mime_type("AUDIO/WAV", processable) is True
    assert is_processable_audio_mime_type("video/mp4", processable) is False
    assert is_processable_audio_mime_type(None, processable) is False
