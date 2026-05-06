package com.example.lunchreview.model.dto;

public record GridItem(
    int cellNumber,
    String review,
    String rating,
    String comment,
    String doActionAndExpression, // AIが生成
    String visualDetail           // AIが生成
) {}