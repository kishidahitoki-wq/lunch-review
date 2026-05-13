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
        // 80/20の垂直レイアウトを明示的に定義
        String layoutDescription = String.format(
            "The image has a strict vertical composition. The upper 80%% of the frame is a grid layout containing %d review panels. " +
            "The lower 20%% of the frame is a dedicated wide-shot horizontal banner showcasing the character in a unique thematic environment.", 
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
                aiGeneratedJsonParts = geminiClient.ask(modelName, aiInstruction).replaceAll("```[a-z]*\\n?|```\\n?", "");
                break; // 成功した場合はループを終了delNa
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
            # Canvas Layout:
            - Vertical Split: Divide the canvas vertically into two sections (80:20 ratio).
            - UPPER 80%%: A multi-panel montage of %d distinct food reviews, featuring the "Fixed Character: DO" in every panel.
            - LOWER 20%%: A special character showcase area (Signature Illustration) dedicated to the "Fixed Character: DO".

            # Overall Structure & Style
            %s.
            The overall image is a montage of %d separate panels.
            High resolution, 1024px or higher, ultra-detailed quality.
            Ensure generous white space (negative space) between elements to prevent clutter.

            # Fixed Character: DO
            %s

            # Upper Area: Grid Content (%d Panels)
            %s

            # Lower Area: Character Showcase (Signature Illustration)
            In this bottom 20%% horizontal section, depict the Fixed Character: DO in a single, cinematic wide-angle scene:
            This section should feel like a signature ending or a "special cut" of the character, distinct from the panels above but sharing the same art style.

            # Layout & Lighting
            %s.
            Each of the %d cells has a thick, dark wooden frame, making it look like a collection of valuable paintings.
            Cinematic lighting with deep shadows prevails, illuminating DO against a plain, misty gray background in each panel.
            The panels are arranged organically to fill the frame, creating a rich visual story of %d different food experiences.
            
            # Text & Icons Rules
            At the very top of the entire image, a grand title banner reads: "%s".
            - Font Family: Set the primary typeface to Noto Sans Japanese.
            - Font Size: Minimum 12pt for body text to ensure readability.
            - Spacing & Margins: Ensure generous white space (padding and margins) to avoid a cluttered look.
            - Orientation: Use horizontal text alignment.
            - Line Spacing: Set a wide line height to improve legibility and flow.
            - Within EACH PANEL (1-9), elements MUST be arranged vertically in the following order:
                - TOP: review text (e.g., dish name).
                - IMMEDIATELY BELOW review: rating (5 filled gold star icons).
                - MIDDLE: Character (Fixed Character: DO) and dish visuals.
                - BOTTOM: comment text in a traditional-looking text box.
            """, 
            totalItems,
            styleDescription, // スタイルを流し込む
            totalItems,
            request.characterSetting(),
            totalItems,
            aiGeneratedJsonParts.replaceAll("```[a-z]*\\n?|```\\n?", ""),
            layoutDescription,
            totalItems,
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
        sb.append("do_action_and_expressionとvisual_detailは簡潔にしてください。\n");
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
            sb.append("      \"do_action_and_expression\": \"[DOのアクションと表情の長すぎない具体的な描写 (英語)]\",\n");
            sb.append("      \"visual_detail\": \"[料理や小道具の長すぎない具体的なディテール描写 (英語)]\"\n");
            sb.append(i == totalGridCells ? "    }\n" : "    },\n");
        }
        
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    public String generateCharacterSetting(String summary) {
        String instruction = String.format("""
            以下のキャラを生成するための構造化された英語のjsonプロンプトを出力してください。
            jsonテンプレートに従って、キャラクターの内容を反映させてください。

            # キャラクター
            "%s"

            # jsonテンプレート
            {
            "subject": {
                "character": "[Character Name]",
                "series": "[Series/Origin]",
                "appearance": {
                "hair": "[Hair style and color]",
                "eyes": "[Eye color and unique details]",
                "facial_expression": "[Expression/Emotion]",
                "notable_features": "[Scars, accessories, clothing details]"
                }
            },
            "action_pose": {
                "pose": "[Action or standing position]",
                "special_abilities": "[Powers, weapons, or visual effects]",
                "hand_gesture": "[Specific hand/finger movements]"
            },
            "environment": {
                "location": "[Setting/Background]",
                "lighting": "[Lighting style, shadows, glow effects]",
                "atmosphere": "[Mood, weather, overall vibe]"
            },
            "technical_specifications": {
                "style": "[Art style, artist influence, or medium]",
                "composition": "[Camera angle, framing, focus]",
                "color_palette": ["[Primary color]", "[Secondary color]", "[Accent colors]"],
                "quality": "[Resolution, level of detail, textures]"
            },
            "text_overlay_hint": {
                "content": "[Text to be displayed]",
                "font_style": "[Typography style]",
                "placement": "[Positioning on the canvas]"
            }
            }
            """, summary);

        // リストを回して安全に生成する
        for (String modelName : fallbackModels) {
            try {
                return geminiClient.ask(modelName, instruction).replaceAll("```[a-z]*\\n?|```\\n?", "");
            } catch (Exception e) {
                System.err.println("Character Generation Error with " + modelName + ": " + e.getMessage());
            }
        }
        throw new RuntimeException("API Error : 全モデルの制限に達しました。しばらくしてから再試行してください。");
    } 
}