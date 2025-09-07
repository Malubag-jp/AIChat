package com.srllc.spring_ai_app.aichat.service.Impl;

import com.srllc.spring_ai_app.aichat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatModel chatModel;

    // Patterns for formatting
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("(\\d+)(°C|°F|°)");
    private static final Pattern NUMBER_WITH_LETTERS = Pattern.compile("(\\d+)([A-Za-z°])");
    private static final Pattern PUNCTUATION_WITHOUT_SPACE = Pattern.compile("([.,!?;:])(?=\\S)");
    private static final Pattern SENTENCE_END = Pattern.compile("([.!?])\\s+");

    @Override
    public String getChatResponse(String message) {
        String response = chatModel.call(message);
        return formatResponse(response);
    }

    @Override
    public Flux<String> getChatResponseStream(String message) {
        // For web UI - return character-by-character streaming
        Prompt prompt = new Prompt(new UserMessage(message));

        return chatModel.stream(prompt)
                .publishOn(Schedulers.boundedElastic())
                .map(chatResponse -> {
                    try {
                        AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
                        return assistantMessage.getText();
                    } catch (Exception e) {
                        log.error("Error extracting text content", e);
                        return "Error: " + e.getMessage();
                    }
                })
                .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    log.warn("Stream timeout or error", e);
                    return Flux.just("Error: Response timed out");
                });
    }

    // For API streaming - return complete formatted responses
    public Flux<String> getApiChatResponseStream(String message) {
        AtomicReference<StringBuilder> completeResponse = new AtomicReference<>(new StringBuilder());
        Prompt prompt = new Prompt(new UserMessage(message));

        return chatModel.stream(prompt)
                .publishOn(Schedulers.boundedElastic())
                .map(chatResponse -> {
                    try {
                        AssistantMessage assistantMessage = (AssistantMessage) chatResponse.getResult().getOutput();
                        String chunk = assistantMessage.getText();
                        completeResponse.get().append(chunk);

                        // Return the chunk for immediate processing (but we'll filter this out)
                        return chunk;
                    } catch (Exception e) {
                        log.error("Error extracting text content", e);
                        return "Error: " + e.getMessage();
                    }
                })
                .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
                .thenMany(Flux.defer(() -> {
                    // After stream completes, return the complete formatted response
                    String formattedResponse = formatResponse(completeResponse.get().toString());
                    return Flux.just(formattedResponse);
                }))
                .timeout(Duration.ofSeconds(120)) // Longer timeout for complete responses
                .onErrorResume(e -> {
                    log.warn("API Stream timeout or error: {}", e.getMessage());
                    String currentResponse = completeResponse.get().toString();
                    if (!currentResponse.isEmpty()) {
                        String formattedResponse = formatResponse(currentResponse);
                        return Flux.just(formattedResponse + "\n\n(Note: Response may be incomplete due to processing limits)");
                    } else {
                        return Flux.just("I apologize, but I'm taking longer than expected to generate a response. Please try again with a more specific question or try the non-streaming endpoint at /api/chat");
                    }
                });
    }

    // Alternative: Simple non-streaming approach for API
    public Flux<String> getApiChatResponseStreamSimple(String message) {
        return Flux.defer(() -> {
                    try {
                        String response = getChatResponse(message);
                        return Flux.just(response);
                    } catch (Exception e) {
                        log.error("Error getting API response", e);
                        return Flux.just("Error: " + e.getMessage());
                    }
                })
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> Flux.just("Sorry, the response is taking too long. Please try the non-streaming endpoint at /api/chat"));
    }

    private String formatResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "I'm here to help! What would you like to know?";
        }

        // Apply all formatting rules
        response = formatText(response);

        return response.trim();
    }

    private String formatText(String text) {
        // 1. Fix temperature and number formatting
        text = NUMBER_WITH_LETTERS.matcher(text).replaceAll(match -> {
            String number = match.group(1);
            String letter = match.group(2);

            if (isSpecialCase(number, letter)) {
                return number + letter;
            }
            return number + " " + letter;
        });

        // 2. Fix punctuation spacing
        text = PUNCTUATION_WITHOUT_SPACE.matcher(text).replaceAll("$1 ");

        // 3. Ensure space after commas
        text = text.replaceAll(",([^ ])", ", $1");

        // 4. Add paragraph breaks after sentences
        text = SENTENCE_END.matcher(text).replaceAll("$1\n\n");

        // 5. Clean up multiple spaces
        text = text.replaceAll(" +", " ");

        // 6. Clean up multiple newlines
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text;
    }

    private boolean isSpecialCase(String number, String letter) {
        if (number.length() == 4 && number.matches("\\d{4}")) {
            return true;
        }
        if (letter.equals("%") || letter.equals("°")) {
            return true;
        }
        return false;
    }
}