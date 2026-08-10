package com.havyn.support.service;

import com.havyn.common.error.ServiceUnavailableException;
import com.havyn.support.domain.SupportChatMessage;
import com.havyn.support.domain.SupportChatRole;
import com.havyn.support.domain.SupportTicket;
import com.havyn.support.repo.SupportChatMessageRepository;
import com.havyn.support.repo.SupportTicketRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportChatService {

    private static final Logger log = LoggerFactory.getLogger(SupportChatService.class);
    public static final String GREETING = "Hi, I'm Havyn Villa's AI Assistant. How can I help you today?";
    private static final Pattern BOOKING_REFERENCE = Pattern.compile("\\bHV-\\d{4}-\\d{6}\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> COMPLAINT_TERMS = List.of(
            "complaint", "problem", "issue", "refund", "charged", "payment failed", "cancel", "dirty", "unsafe", "host", "scam",
            "not working", "wrong", "dispute", "support ticket", "bad experience");

    private final SupportChatMessageRepository messageRepository;
    private final SupportTicketRepository ticketRepository;
    private final OpenAiSupportClient openAiSupportClient;

    public SupportChatService(
            SupportChatMessageRepository messageRepository,
            SupportTicketRepository ticketRepository,
            OpenAiSupportClient openAiSupportClient) {
        this.messageRepository = messageRepository;
        this.ticketRepository = ticketRepository;
        this.openAiSupportClient = openAiSupportClient;
    }

    @Transactional(readOnly = true)
    public List<SupportChatMessage> history(UUID userId) {
        return messageRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional
    public List<SupportChatMessage> send(UUID userId, String body) {
        String trimmed = body.trim();
        SupportChatMessage userMessage = messageRepository.save(new SupportChatMessage(userId, SupportChatRole.USER, trimmed));
        boolean complaint = isComplaint(trimmed);
        if (complaint) {
            ticketRepository.save(new SupportTicket(userId, bookingReference(trimmed), summarize(trimmed), trimmed));
        }
        List<SupportChatMessage> history = messageRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        String answer;
        try {
            answer = openAiSupportClient.respond(history);
        } catch (ServiceUnavailableException e) {
            log.warn("Support chat OpenAI response unavailable userId={} code={}", userId, e.getCode());
            answer = fallbackAnswer(trimmed, complaint);
        }
        messageRepository.save(new SupportChatMessage(userId, SupportChatRole.ASSISTANT, answer));
        return messageRepository.findAllByUserIdOrderByCreatedAtAsc(userMessage.getUserId());
    }

    private boolean isComplaint(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        return COMPLAINT_TERMS.stream().anyMatch(normalized::contains);
    }

    private String bookingReference(String body) {
        Matcher matcher = BOOKING_REFERENCE.matcher(body);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private String summarize(String body) {
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 237) + "...";
    }

    private String fallbackAnswer(String body, boolean complaint) {
        if (complaint) {
            String reference = bookingReference(body);
            String referenceLine = reference == null ? "" : " with booking reference " + reference;
            return "Thanks for explaining. I've flagged this for Admin" + referenceLine
                    + ". Please add any dates, property name, payment reference, or host details that could help us review it faster.";
        }
        return """
                I can help with search, filters, bookings, wishlists, trips, becoming a host, listing management, cancellations, refunds, and support.
                Tell me what you want to do, and I'll guide you step by step.
                """.trim();
    }
}
