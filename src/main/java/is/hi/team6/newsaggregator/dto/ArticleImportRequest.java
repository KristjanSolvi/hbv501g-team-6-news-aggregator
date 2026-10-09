package is.hi.team6.newsaggregator.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ArticleImportRequest(

        @NotNull
        Long sourceId,

        @NotEmpty
        @Size(max = 100)
        List<@Valid ArticleImportItemRequest> articles

) {}