package com.havyn.support.service;

import com.havyn.support.domain.SupportChatMessage;
import com.havyn.support.domain.SupportChatRole;
import com.havyn.support.repo.SupportChatMessageRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportChatService {

    public static final String GREETING = "Hi, I'm Havyn Villa's AI Assistant. How can I help you today?";

    private final SupportChatMessageRepository messageRepository;
    private final OpenAiSupportClient openAiSupportClient;

    public SupportChatService(SupportChatMessageRepository messageRepository, OpenAiSupportClient openAiSupportClient) {
        this.messageRepository = messageRepository;
        this.openAiSupportClient = openAiSupportClient;
    }

    @Transactional(readOnly = true)
    public List<SupportChatMessage> history(UUID userId) {
        return messageRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional
    public List<SupportChatMessage> send(UUID userId, String body) {
        SupportChatMessage userMessage = messageRepository.save(new SupportChatMessage(userId, SupportChatRole.USER, body.trim()));
        List<SupportChatMessage> history = messageRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        String answer = openAiSupportClient.respond(history);
        messageRepository.save(new SupportChatMessage(userId, SupportChatRole.ASSISTANT, answer));
        return messageRepository.findAllByUserIdOrderByCreatedAtAsc(userMessage.getUserId());
    }
}
