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
        int totalItems = request.reviews().size();

        // 特定のNxNグリッドではなく、「複数のパネルの集合体」として定義
        // 画像生成AIには "A collection of X panels" と伝えるのがスムーズです
        String layoutDescription = String.format(
            "A multi-panel composition consisting of exactly %d distinct scenes in a dynamic layout.", 
            totalItems
        );

        // --- 追加：スタイルに応じた描写の定義 ---
        String styleDescription = switch (request.style()) {
            case "anime" -> "Modern high-quality anime style, vibrant colors, dynamic line work, like a professional studio animation.";
            case "ukiyo-e" -> "Traditional Japanese woodblock print (Ukiyo-e) style, bold outlines, washi paper texture, flat colors, classic Edo-period aesthetic.";
            case "pixel" -> "Detailed 16-bit pixel art style, reminiscent of retro video game cutscenes, vibrant palette, clean dot patterns.";
            case "oil-painting" -> "Classical oil painting on canvas, thick brushstrokes, rich textures, dramatic chiaroscuro lighting.";
            default -> "A photorealistic, highly detailed 3D render, dark and cinematic, like a sequence of high-end film stills."; // 以前のデフォルト
        };

        // 2. AIへの指示出しと回答取得
        String aiInstruction = buildAiInstruction(request, totalItems);
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
            %s.
            The overall image is a montage of %d separate panels.
            High resolution, 1024px or higher, ultra-detailed quality.
            Ensure generous white space (negative space) between elements to prevent clutter.

            [Fixed Character: DO]
            %s

            [Grid Content]
            %s

            [Layout & Lighting]
            %s.
            Each panel is separated by a dark, thin border.
            The panels are arranged organically to fill the frame, creating a rich visual story of %d different food experiences.
            
            [Text & Icons Rules]
            At the very top of the entire image, a grand title banner reads: "%s".
            - Use Bold Gothic Font (Sans-serif) for all text to ensure maximum readability.
            - Text size must be large and prominent within each panel.
            - The review is text at the top of the cell.
            - The rating is shown by 5 star icons (filled gold stars).
            - The comment text is in a traditional-looking text box at the bottom.
            - Maintain wide margins and padding around all text and icons for a clean look.
            """, 
            styleDescription, // スタイルを流し込む
            totalItems,
            request.characterSetting(),
            aiGeneratedJsonParts,
            layoutDescription,
            totalItems,
            request.title()
        );
    }

    private String buildAiInstruction(ReviewRequest request, int totalGridCells) {
        int reviewCount = request.reviews().size();
        
        StringBuilder sb = new StringBuilder();
        
        // 1. 指示
        sb.append("# 指示\n");
        sb.append("与えられた入力に適したdo_action_and_expressionとvisual_detailを考え出力してください。\n");
        sb.append("なお、与えられた入力の文言（review, rating, comment）は絶対に変えないでください。\n\n");

        // 2. 入力
        sb.append("# 入力\n");
        // 実データの入力（セル1〜N）
        for (int i = 0; i < reviewCount; i++) {
            var item = request.reviews().get(i);
            sb.append(String.format("- panel_number%d\n", i + 1));
            sb.append(String.format("    - review: %s\n", item.dishName()));
            sb.append(String.format("    - rating: %d / 5 Stars\n", item.stars()));
            sb.append(String.format("    - comment: %s\n", item.comment()));
        }

        sb.append("\n");

        // 3. 出力フォーマット
        sb.append("# 出力フォーマット\n");
        sb.append("以下のJSON形式で出力してください。全セルのデータを省略せずに出力してください。\n\n");
        sb.append("{\n");
        sb.append("  \"panels\": [\n");
        
        // セル1からセル9までの雛形（AIに全件出力を促すための例示）
        for (int i = 1; i <= totalGridCells; i++) {
            sb.append("    {\n");
            sb.append(String.format("      \"panel_number\": %d,\n", i));
            sb.append(String.format("      \"review\": \"[入力セルのreview %d をそのままコピー]\",\n", i));
            sb.append(String.format("      \"rating\": \"[入力セルのrating %d をそのままコピー]\",\n", i));
            sb.append(String.format("      \"comment\": \"[入力セルのcomment %d をそのままコピー]\",\n", i));
            sb.append("      \"do_action_and_expression\": \"[DOのアクションと表情の具体的な描写 (英語)]\",\n");
            sb.append("      \"visual_detail\": \"[料理や小道具の具体的なディテール描写 (英語)]\"\n");
            sb.append(i == totalGridCells ? "    }\n" : "    },\n");
        }
        
        sb.append("  ]\n");
        sb.append("}\n");

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