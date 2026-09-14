package com.example.ragkb.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.chat-model")
public class ChatModelProperties {

    private String provider = "mock";

    private String model = "mock-chat-model";

    private double temperature = 0.2;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("chat model temperature must be between 0 and 2");
        }
        this.temperature = temperature;
    }
}
