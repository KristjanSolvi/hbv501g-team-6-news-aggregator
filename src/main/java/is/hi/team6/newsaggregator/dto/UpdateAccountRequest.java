package is.hi.team6.newsaggregator.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @Size(min = 8, max = 128) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String password) {}
