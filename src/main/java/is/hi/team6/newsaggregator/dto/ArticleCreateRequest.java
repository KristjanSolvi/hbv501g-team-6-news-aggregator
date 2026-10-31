
package is.hi.team6.newsaggregator.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ArticleCreateRequest(

        @NotBlank
        @Size(max = 255)
        String title,

        String content,

        @Size(max = 2048)
        String url,

        @NotBlank
        String category,

        @NotNull
        Instant publishedAt,

        UUID sourceId

) {}