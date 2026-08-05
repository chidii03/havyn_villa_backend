package com.havyn.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Standard pagination envelope: {@code { data, page, size, total, nextCursor } }.
 * See project-docs/architecture/03-api-design.md#conventions.
 */
public record PageResponse<T>(List<T> data, int page, int size, long total, String nextCursor) {

    public static <T> PageResponse<T> of(Page<T> page) {
        boolean hasNext = page.hasNext();
        String nextCursor = hasNext ? String.valueOf(page.getNumber() + 1) : null;
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), nextCursor);
    }
}
