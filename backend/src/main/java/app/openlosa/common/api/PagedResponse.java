package app.openlosa.common.api;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Envelope for server-side paginated list endpoints. {@code content} holds the
 * mapped rows for the current page; the remaining fields describe the slice so
 * clients can render pagination controls without a second round trip.
 */
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
