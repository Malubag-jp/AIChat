package com.srllc.spring_ai_app.aichat.service;

import reactor.core.publisher.Flux;

public interface ChatService {
    String getChatResponse(String message);
    Flux<String> getChatResponseStream(String message);
}