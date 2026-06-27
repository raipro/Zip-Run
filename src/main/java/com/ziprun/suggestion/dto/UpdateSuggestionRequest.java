package com.ziprun.suggestion.dto;

import com.ziprun.suggestion.SuggestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Accept or reject a suggestion. Only {@code ACCEPTED} / {@code REJECTED} are valid
 * targets — the service rejects anything else.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateSuggestionRequest {

    @NotNull
    private SuggestionStatus status;
}
