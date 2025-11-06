package ru.practicum.dto;

import lombok.Data;

@Data
public class DeepSeekDto {
    private String baseUrl;
    private String apiKey;
    private String model;
}
