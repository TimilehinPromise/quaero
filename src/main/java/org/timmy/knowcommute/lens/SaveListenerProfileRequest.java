package org.timmy.knowcommute.lens;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveListenerProfileRequest(@NotBlank @Size(min = 50, max = 1_500) String profile) {
}
