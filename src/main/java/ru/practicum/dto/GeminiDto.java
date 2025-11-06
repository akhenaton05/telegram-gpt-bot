package ru.practicum.dto;

import lombok.Data;

@Data
public class GeminiDto {
    private String baseUrl;
    private String apiKey;
    private String model;
}
