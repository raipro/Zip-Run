package com.ziprun.suggestion;

import com.ziprun.suggestion.dto.SuggestionResponse;
import com.ziprun.suggestion.dto.UpdateSuggestionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reassignment suggestions (T-1). The list feeds the ops UI; the PATCH is the human
 * checkpoint where ops accepts or rejects a proposed reassignment.
 */
@RestController
@RequestMapping("/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    /** GET /suggestions?status= — list suggestions, optionally filtered (e.g. PENDING). */
    @GetMapping
    public List<SuggestionResponse> list(@RequestParam(required = false) SuggestionStatus status) {
        return suggestionService.list(status);
    }

    /** PATCH /suggestions/{id} — accept or reject; ACCEPTED applies the reassignment. */
    @PatchMapping("/{id}")
    public SuggestionResponse updateStatus(@PathVariable Long id,
                                           @Valid @RequestBody UpdateSuggestionRequest request) {
        return suggestionService.updateStatus(id, request.getStatus());
    }
}
