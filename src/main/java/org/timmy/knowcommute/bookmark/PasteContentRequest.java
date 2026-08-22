package org.timmy.knowcommute.bookmark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasteContentRequest(
        @Size(max = 512) String title,
        @NotBlank @Size(max = 500_000) String text) {
}
