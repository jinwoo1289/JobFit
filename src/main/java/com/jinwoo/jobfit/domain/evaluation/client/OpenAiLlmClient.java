package com.jinwoo.jobfit.domain.evaluation.client;

import com.jinwoo.jobfit.domain.evaluation.exception.LlmResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiLlmClient implements LlmClient {

    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final int MAX_TOKENS = 2000;

    private final RestClient restClient;

    public OpenAiLlmClient(@Value("${OPENAI_API_KEY}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(CHAT_COMPLETIONS_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "max_tokens", MAX_TOKENS,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        ChatCompletionResponse response = restClient.post()
                .body(requestBody)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmResponseException("OpenAI 응답에 choices가 없습니다.");
        }

        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            throw new LlmResponseException("OpenAI 응답 content가 비어 있습니다.");
        }

        return content;
    }

    private record ChatCompletionResponse(List<Choice> choices) {
        private record Choice(Message message) {
            private record Message(String content) {
            }
        }
    }
}
