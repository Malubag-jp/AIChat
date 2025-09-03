package com.srllc.spring_ai_app.aichat.controller;

import com.srllc.spring_ai_app.aichat.dto.ChatRequest;
import com.srllc.spring_ai_app.aichat.dto.ChatResponse;
import com.srllc.spring_ai_app.aichat.service.ChatService;
import com.srllc.spring_ai_app.aichat.service.Impl.ChatServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat API", description = "API for interacting with Llama 3 8B via Ollama")
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;
    private final ChatServiceImpl chatServiceImpl; // Inject the implementation directly

    @PostMapping
    @Operation(
            summary = "Send a message to AI",
            description = "Send a message to the Llama 3 8B model and get a response"
    )
    @ApiResponse(responseCode = "200", description = "Successful response from AI")
    public ResponseEntity<ChatResponse> chat(
            @Parameter(description = "The message to send to AI", required = true)
            @RequestBody ChatRequest request) {

        String response = chatService.getChatResponse(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream chat response",
            description = "Get a streaming response from the AI model"
    )
    public Flux<ChatResponse> chatStream(
            @Parameter(description = "The message to send to AI", required = true)
            @RequestBody ChatRequest request) {

        return chatServiceImpl.getApiChatResponseStream(request.getMessage())
                .map(ChatResponse::new);
    }

    @GetMapping("/health")
    @Operation(summary = "Check API health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API is running with Llama 3 8B via Ollama");
    }
}