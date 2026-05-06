package com.example.lunchreview.controller;

import com.example.lunchreview.model.dto.ReviewRequest;
import com.example.lunchreview.service.PromptService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final PromptService promptService;

    public ReviewController(PromptService promptService) {
        this.promptService = promptService;
    }

    @PostMapping("/generate")
    public Map<String, String> getPrompt(@RequestBody ReviewRequest request) {
        String finalPrompt = promptService.generate(request);
        return Map.of(
            "prompt", finalPrompt,
            "url", "https://gemini.google.com/app"
        );
    }

    // 既存のインポートに追加
    @PostMapping("/generate-character")
    public Map<String, String> generateCharacter(@RequestBody Map<String, String> request) {
        String summary = request.get("summary");
        String detailedSetting = promptService.generateCharacterSetting(summary);
        return Map.of("characterSetting", detailedSetting);
    }
}