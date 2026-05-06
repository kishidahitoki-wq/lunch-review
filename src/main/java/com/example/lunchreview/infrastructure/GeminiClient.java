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
     * ユーザーのレビュー入力を元に、DOのアクションと料理のビジュアル詳細を生成する
     */
    public String ask(String instruction) {
        try {
            var response = client.models.generateContent("gemini-2.5-flash", instruction, null);
            return response.text();
        } catch (Exception e) {
            throw new RuntimeException("Gemini API通信エラー: " + e.getMessage());
        }
    }
}