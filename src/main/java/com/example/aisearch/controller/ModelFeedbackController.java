package com.example.aisearch.controller;

import com.example.aisearch.controller.dto.ModelFeedbackRequestDto;
import com.example.aisearch.controller.dto.ModelFeedbackOverallResponseDto;
import com.example.aisearch.controller.dto.ModelFeedbackResponseDto;
import com.example.aisearch.service.feedback.ModelFeedbackOverallSummary;
import com.example.aisearch.service.feedback.ModelFeedbackService;
import com.example.aisearch.service.feedback.ModelFeedbackSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@CrossOrigin(origins = "*")
public class ModelFeedbackController {

    private final ModelFeedbackService modelFeedbackService;

    public ModelFeedbackController(ModelFeedbackService modelFeedbackService) {
        this.modelFeedbackService = modelFeedbackService;
    }

    @PostMapping("/api/model-feedback")
    public ModelFeedbackResponseDto saveFeedback(@Valid @RequestBody ModelFeedbackRequestDto request) {
        ModelFeedbackSummary summary = modelFeedbackService.save(request.query(), request.score());
        return ModelFeedbackResponseDto.from(summary, request.score());
    }

    @GetMapping("/api/model-feedback")
    public ModelFeedbackResponseDto getFeedbackSummary(@RequestParam("q") @NotBlank String query) {
        ModelFeedbackSummary summary = modelFeedbackService.getSummary(query);
        return ModelFeedbackResponseDto.from(summary, 0);
    }

    @GetMapping("/api/model-feedback/summary")
    public ModelFeedbackOverallResponseDto getOverallFeedbackSummary() {
        ModelFeedbackOverallSummary summary = modelFeedbackService.getOverallSummary();
        return ModelFeedbackOverallResponseDto.from(summary);
    }
}
