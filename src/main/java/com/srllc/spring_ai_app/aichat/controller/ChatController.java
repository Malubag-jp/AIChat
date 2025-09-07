package com.srllc.spring_ai_app.aichat.controller;

import com.srllc.spring_ai_app.aichat.dto.ChatResponse;
import com.srllc.spring_ai_app.aichat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping("/")
    public String chatForm(Model model) {
        model.addAttribute("message", "");
        model.addAttribute("response", "");
        return "chat";
    }

    @PostMapping("/chat")
    public String chat(@RequestParam("message") String message, Model model) {
        // Always use the full AI model
        String response = chatService.getChatResponse(message);

        model.addAttribute("message", message);
        model.addAttribute("response", response);
        return "chat";
    }

    @PostMapping(value = "/chat/ajax", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ChatResponse chatAjax(@RequestParam("message") String message) {
        // Always use the full AI model
        String response = chatService.getChatResponse(message);
        return new ChatResponse(response);
    }

    @GetMapping("/chat/stream")
    public SseEmitter streamChat(@RequestParam("message") String message) {
        SseEmitter emitter = new SseEmitter(6000_000L);
        StringBuilder completeResponse = new StringBuilder();

        executor.execute(() -> {
            try {
                chatService.getChatResponseStream(message)
                        .subscribe(
                                chunk -> {
                                    try {
                                        completeResponse.append(chunk);
                                        // Send formatted chunks
                                        emitter.send(SseEmitter.event()
                                                .data(new ChatResponse(chunk))
                                                .name("message"));
                                    } catch (IOException e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    try {
                                        // Send final formatted response on error
                                        String finalResponse = completeResponse.toString();
                                        emitter.send(SseEmitter.event()
                                                .data(new ChatResponse(finalResponse))
                                                .name("error"));
                                        emitter.complete();
                                    } catch (IOException e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                () -> {
                                    try {
                                        // Send final complete formatted response
                                        String finalResponse = completeResponse.toString();
                                        emitter.send(SseEmitter.event()
                                                .data(new ChatResponse(finalResponse))
                                                .name("complete"));
                                        emitter.complete();
                                    } catch (IOException e) {
                                        emitter.completeWithError(e);
                                    }
                                }
                        );
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .data(new ChatResponse("Error: " + e.getMessage()))
                            .name("error"));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        emitter.onTimeout(() -> {
            try {
                String finalResponse = completeResponse.toString();
                emitter.send(SseEmitter.event()
                        .data(new ChatResponse(finalResponse + "\n\n(Response may be incomplete due to timeout)"))
                        .name("timeout"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}