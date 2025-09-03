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

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatModel chatModel;
    private StringBuilder buffer = new StringBuilder();

    @Override
    public String getChatResponse(String message) {
        return chatModel.call(message);
    }

    @Override
    public Flux<String> getChatResponseStream(String message) {
        buffer.setLength(0); // Clear buffer for new request
        Prompt prompt = new Prompt(new UserMessage(message));

        return chatModel.stream(prompt)
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
                .concatMap(chunk -> {
                    buffer.append(chunk);
                    return processBuffer();
                })
                .concatWith(Flux.defer(() -> {
                    // Emit any remaining content in buffer at the end
                    if (buffer.length() > 0) {
                        String remaining = buffer.toString();
                        buffer.setLength(0);
                        return Flux.just(remaining);
                    }
                    return Flux.empty();
                }));
    }

    // New method specifically for API streaming that returns a single response
    public Flux<String> getApiChatResponseStream(String message) {
        return Flux.just(chatModel.call(message));
    }

    private Flux<String> processBuffer() {
        Flux<String> words = Flux.empty();
        String content = buffer.toString();

        // Look for the best place to split that preserves punctuation
        int splitIndex = findOptimalSplitPoint(content);

        if (splitIndex > 0) {
            String completePart = content.substring(0, splitIndex);
            String remaining = content.substring(splitIndex);

            // Split into meaningful chunks (words with their punctuation)
            words = splitIntoMeaningfulChunks(completePart);

            // Reset buffer with remaining content
            buffer.setLength(0);
            buffer.append(remaining);
        }

        return words;
    }

    private int findOptimalSplitPoint(String text) {
        // Prefer to split after punctuation followed by space
        for (int i = text.length() - 1; i > 0; i--) {
            char currentChar = text.charAt(i);

            // Split after punctuation that's likely to end a sentence
            if (i > 0 && isSentenceEndingPunctuation(text.charAt(i - 1)) &&
                    Character.isWhitespace(currentChar)) {
                return i + 1; // Split after the space
            }

            // Split after punctuation (without following space)
            if (isSentenceEndingPunctuation(currentChar) &&
                    (i == text.length() - 1 || Character.isWhitespace(text.charAt(i + 1)))) {
                return i + 1;
            }

            // Split after words (space followed by non-space)
            if (Character.isWhitespace(currentChar) &&
                    i + 1 < text.length() &&
                    !Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }

        return 0; // No good split point found
    }

    private boolean isSentenceEndingPunctuation(char c) {
        return c == '.' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    private Flux<String> splitIntoMeaningfulChunks(String text) {
        // Split by spaces but keep punctuation with words
        String[] parts = text.split("(?<=\\s)|(?=\\s)");
        return Flux.fromArray(parts)
                .filter(part -> !part.trim().isEmpty())
                .map(part -> {
                    // Ensure punctuation has proper spacing
                    if (part.matches("^[.,!?;:]$")) {
                        return part;
                    } else if (part.matches("^\\s+$")) {
                        return " ";
                    } else {
                        return part.trim() + " ";
                    }
                });
    }
}