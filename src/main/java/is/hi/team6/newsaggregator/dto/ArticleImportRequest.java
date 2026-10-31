package is.hi.team6.newsaggregator.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ArticleImportRequest(

        @NotNull
        UUID sourceId,

        @NotEmpty
        @Size(max = 100)
        List<@Valid ArticleImportItemRequest> articles

) {}