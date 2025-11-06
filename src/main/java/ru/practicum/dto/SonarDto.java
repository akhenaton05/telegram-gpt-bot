package ru.practicum.dto;

import lombok.Data;

@Data
public class SonarDto {
    private String baseUrl;
    private String apiKey;
    private String model;
}
