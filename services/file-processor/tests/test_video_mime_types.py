from app.processors.video_mime_types import is_processable_video_mime_type, parse_processable_video_mime_types


def test_parse_processable_video_mime_types_normalizes_entries():
    processable = parse_processable_video_mime_types(
        " video/mp4 , VIDEO/X-MATROSKA; charset=binary ,, video/MP2T "
    )

    assert processable == frozenset({"video/mp4", "video/x-matroska", "video/mp2t"})


def test_is_processable_video_mime_type_handles_parameters_case_and_unknowns():
    processable = parse_processable_video_mime_types("video/x-matroska,video/mp2t")

    assert is_processable_video_mime_type("VIDEO/X-MATROSKA", processable) is True
    assert is_processable_video_mime_type("video/x-matroska; charset=binary", processable) is True
    assert is_processable_video_mime_type(" video/MP2T ", processable) is True
    assert is_processable_video_mime_type("video/x-flv", processable) is False
    assert is_processable_video_mime_type(None, processable) is False
