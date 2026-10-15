package com.dailyrupi.prreview.support;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** JSON settings for reports, state and audit lines: ISO-8601 dates throughout. */
public final class Json {

    /** Indented with map keys sorted, so the same analysis always produces the same file. */
    private static final ObjectMapper PRETTY = base()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    /** One line per value, keys in the order written ("at" and "event" first in the audit log). */
    private static final ObjectMapper COMPACT = base().build();

    private Json() {
    }

    public static ObjectMapper pretty() {
        return PRETTY;
    }

    public static ObjectMapper compact() {
        return COMPACT;
    }

    private static JsonMapper.Builder base() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST);
    }
}
