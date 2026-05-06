package com.example.lunchreview.service;

import com.example.lunchreview.infrastructure.GeminiClient;
import com.example.lunchreview.model.dto.ReviewRequest;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PromptService {

    private final GeminiClient geminiClient;

    // 優先して使いたいモデルのリスト
    private final List<String> fallbackModels = List.of(
        "gemini-2.5-pro",        // 1. 最強。一番賢いDO
        "gemini-2.5-flash",      // 2. 高速・高性能。バランス型だDO
        "gemini-2.0-flash",      // 3. 安定の旧世代高速モデルだDO
        "gemini-2.5-flash-lite", // 4. 最終防衛線。一番軽いDO！
        "gemini-2.0-flash-lite"  // 5. 最後の最後、粘りのLiteだDO
    );

    public PromptService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    public String generate(ReviewRequest request) {
        // 1. レビュー数に基づいてグリッドサイズを計算
        int reviewCount = Math.min(request.reviews().size(), 9); 
        int side = (int) Math.ceil(Math.sqrt(reviewCount));
        if (side < 2) side = 2; // 最低2x2
        
        String gridLayout = side + "x" + side;
        int totalGridCells = side * side;

        // --- 追加：スタイルに応じた描写の定義 ---
        String styleDescription = switch (request.style()) {
            case "anime" -> "Modern high-quality anime style, vibrant colors, dynamic line work, like a professional studio animation.";
            case "ukiyo-e" -> "Traditional Japanese woodblock print (Ukiyo-e) style, bold outlines, washi paper texture, flat colors, classic Edo-period aesthetic.";
            case "pixel" -> "Detailed 16-bit pixel art style, reminiscent of retro video game cutscenes, vibrant palette, clean dot patterns.";
            case "oil-painting" -> "Classical oil painting on canvas, thick brushstrokes, rich textures, dramatic chiaroscuro lighting.";
            default -> "A photorealistic, highly detailed 3D render, dark and cinematic, like a sequence of high-end film stills."; // 以前のデフォルト
        };

        // 2. AIへの指示出しと回答取得
        String aiInstruction = buildAiInstruction(request, totalGridCells);
        String aiGeneratedJsonParts = null;
        for (String modelName : fallbackModels) {
            try {
                aiGeneratedJsonParts = geminiClient.ask(modelName, aiInstruction);
                break; // 成功した場合はループを終了
            } catch (RuntimeException e) {
                // 指定されたモデルでエラーが発生した場合、次のモデルに移行
                System.err.println("Error with model " + modelName + ": " + e.getMessage());
            }
        }

        if (aiGeneratedJsonParts == null) {
            throw new RuntimeException("API Error : 全モデルの制限に達しました。しばらくしてから再試行してください。");
        }

        // 3. 最終プロンプトテンプレート
        return String.format("""
            [Overall Structure & Style]
            %s Presented as a %s grid layout of %d distinct scenes.

            [Fixed Character: DO]
            %s

            [Grid Content]
            %s

            [Layout & Lighting]
            Each of the %d cells has a thick, dark wooden frame, making it look like a collection of valuable paintings. 
            Cinematic lighting with deep shadows prevails, illuminating DO against a plain, misty gray background in each panel.
            
            [Text & Icons Rules]
            At the very top of the entire image, a grand title banner reads: "%s".

            [STRICT TEXT RULE]
            For the TOP (Header) and BOTTOM (Footer), use the EXACT text provided in the Grid Content 'review' and 'comment' fields.
            If the JSON says "DO", write only "DO". No creative additions allowed.
            
            Inside EACH grid cell, text and icons must be arranged strictly as follows:
            1. TOP (Header): For LUNCH REVIEW cells, write the text from JSON 'review' field in large, bold letters. For MUSCLE TRAINING cells, DO NOT write any text.
            2. IMMEDIATELY BELOW THE TOP TEXT: For LUNCH REVIEW cells, write "Score: X/5". For MUSCLE TRAINING cells, DO NOT write any text.
            3. BOTTOM (Footer): For LUNCH REVIEW cells, write the exact comment inside a traditional scroll. For MUSCLE TRAINING cells, DO NOT write any text.
            
            * All text must be legible and avoid overlapping with DO's face.
            * Do not include any English action descriptions in the final image.
            """, 
            styleDescription, // スタイルを流し込む
            gridLayout,
            totalGridCells,
            request.characterSetting(),
            aiGeneratedJsonParts,
            totalGridCells,
            request.title()
        );
    }

    private String buildAiInstruction(ReviewRequest request, int totalGridCells) {
        int reviewCount = request.reviews().size();
        int emptyCells = totalGridCells - reviewCount;

        StringBuilder sb = new StringBuilder();
        sb.append("Convert these lunch reviews into a JSON array for an image prompt.\n");
        sb.append("The character 'DO' has this setting: ").append(request.characterSetting()).append("\n"); 
        sb.append("Each object MUST have: 'cell_number', 'review', 'rating', 'comment', 'do_action_and_expression', 'visual_detail'.\n\n");
        sb.append("### CRITICAL DATA INTEGRITY RULES ###\n");
        sb.append("1. The 'review' field is a generic LABEL. It may contain nonsense, names, or non-food items.\n");
        sb.append("2. You MUST copy the 'review' and 'comment' values EXACTLY as provided by the user.\n");
        sb.append("3. DO NOT use the visual context (like food in the scene) to rename the 'review' field.\n");
        sb.append("4. If the user input is 'DODO', the output JSON 'review' MUST be 'DODO'. Never 'Hamburger' or anything else.\n\n");

        // 視覚描写についても、特定の料理名を固定しないように指示
        sb.append("When describing 'visual_detail', if the 'review' content is ambiguous (like 'DODO'), ");
        sb.append("describe a high-protein, hearty meal in general terms (e.g., 'a grand feast', 'a large platter') ");
        sb.append("rather than naming a specific dish that might contradict the label.\n\n");

        // ランチレビューの枠
        for (int i = 0; i < reviewCount; i++) {
            var item = request.reviews().get(i);
            sb.append(String.format("- Cell: %d, review: %s, rating: %d, comment: %s\n", 
                i + 1, item.dishName(), item.stars(), item.comment()));
        }

        // 筋トレの枠（余り分）
        if (emptyCells > 0) {
            sb.append("\n- Remaining Cells (");
            for (int i = reviewCount + 1; i <= totalGridCells; i++) {
                sb.append(i).append(i == totalGridCells ? "" : ", ");
            }
            sb.append("): Describe DO performing intense muscle hypertrophy training (e.g., bench press, squats, leg press). ");
            sb.append("Make these cells pure training scenes WITHOUT food. Set 'review', 'rating', and 'comment' to 'NONE'.\n");
        }

        sb.append("\nReturn ONLY the raw JSON array. No conversational text or markdown blocks.");
        System.out.println("AI Instruction:\n" + sb.toString()); // デバッグ用
        return sb.toString();
    }

    public String generateCharacterSetting(String summary) {
        String instruction = String.format("""
            You are an expert prompt engineer for AI image generation.
            The user wants a character based on this summary: "%s"
            
            Please create a detailed character definition for an image prompt.
            Include physical appearance, personality, and signature items.
            
            Output ONLY the final descriptive paragraph in English. 
            No conversational text.
            """, summary);

        // リストを回して安全に生成する
        for (String modelName : fallbackModels) {
            try {
                return geminiClient.ask(modelName, instruction);
            } catch (Exception e) {
                System.err.println("Character Generation Error with " + modelName + ": " + e.getMessage());
            }
        }
        throw new RuntimeException("API Error : 全モデルの制限に達しました。しばらくしてから再試行してください。");
    } 
}