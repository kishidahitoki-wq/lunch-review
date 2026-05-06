package com.example.lunchreview.model.dto;
import java.util.List;

public record ReviewRequest(
    String title,
    String characterSetting,
    String style,            // 追加：選択された画風
    List<LunchItem> reviews 
) {
    public record LunchItem(String dishName, int stars, String comment) {}
}