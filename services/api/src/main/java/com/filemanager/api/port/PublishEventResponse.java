package com.filemanager.api.port;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublishEventResponse {
    String messageId;
    String topic;
    int partition;
    long offset;
}
