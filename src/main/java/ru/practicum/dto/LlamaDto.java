package ru.practicum.dto;

import lombok.Data;

@Data
public class LlamaDto {
    private String baseUrl;
    private String apiKey;
    private String model;
}
