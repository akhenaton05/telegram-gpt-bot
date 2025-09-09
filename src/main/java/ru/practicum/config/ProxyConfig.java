package ru.practicum.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "proxy")
public class ProxyConfig {
    private boolean enabled;
    private String host;
    private int port;
    private String username;
    private String password;
}
