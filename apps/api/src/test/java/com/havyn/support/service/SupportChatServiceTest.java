package com.havyn.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.support.domain.SupportChatMessage;
import com.havyn.support.domain.SupportChatRole;
import com.havyn.support.repo.SupportChatMessageRepository;
import com.havyn.support.repo.SupportTicketRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupportChatServiceTest {

    private final SupportChatMessageRepository messageRepository = mock(SupportChatMessageRepository.class);
    private final SupportTicketRepository ticketRepository = mock(SupportTicketRepository.class);
    private final OpenAiSupportClient openAiSupportClient = mock(OpenAiSupportClient.class);
    private final SupportChatService service = new SupportChatService(messageRepository, ticketRepository, openAiSupportClient);

    @Test
    void historyReturnsUserConversationInPersistedOrder() {
        UUID userId = UUID.randomUUID();
        SupportChatMessage saved = new SupportChatMessage(userId, SupportChatRole.ASSISTANT, SupportChatService.GREETING);
        when(messageRepository.findAllByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(saved));

        assertThat(service.history(userId)).containsExactly(saved);
    }

    @Test
    void sendPersistsUserMessageCallsOpenAiWithHistoryAndPersistsAnswer() {
        UUID userId = UUID.randomUUID();
        SupportChatMessage userMessage = new SupportChatMessage(userId, SupportChatRole.USER, "How do refunds work?");
        SupportChatMessage assistantMessage = new SupportChatMessage(userId, SupportChatRole.ASSISTANT, "Refunds depend on the cancellation policy.");
        when(messageRepository.save(any(SupportChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findAllByUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(userMessage))
                .thenReturn(List.of(userMessage, assistantMessage));
        when(openAiSupportClient.respond(List.of(userMessage))).thenReturn(assistantMessage.getBody());

        List<SupportChatMessage> result = service.send(userId, "  How do refunds work?  ");

        assertThat(result).containsExactly(userMessage, assistantMessage);
        verify(messageRepository, times(2)).save(any(SupportChatMessage.class));
        verify(openAiSupportClient).respond(List.of(userMessage));
    }
}
