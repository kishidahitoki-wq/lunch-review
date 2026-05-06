package com.example.lunchreview.infrastructure;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiClient {
    
    @Value("${gemini.api.key}")
    private String apiKey;

    private Client client;

    @PostConstruct
    public void init() {
        HttpOptions options = HttpOptions.builder()
                .apiVersion("v1")
                .build();

        this.client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(options)
                .build();
    }

    /**
     * 指定されたモデルを使用してコンテンツを生成する
     */
    public String ask(String modelName, String instruction) {
        try {
            // モデル名を引数で受け取ったものに差し替える
            var response = client.models.generateContent(modelName, instruction, null);
            return response.text();
        } catch (Exception e) {
            // ここで429エラー等をそのまま投げることで、Service側で検知可能にする
            throw new RuntimeException("API Error [" + modelName + "]: " + e.getMessage(), e);
        }
    }
}