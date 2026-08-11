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
    private static final List<String> DETAIL_TERMS = List.of(
            "booking", "reference", "transaction", "payment", "paid", "receipt", "property", "shortlet", "apartment",
            "host", "lekki", "ikeja", "uyo", "tomorrow", "today", "yesterday", "check-in", "checkout", "august",
            "january", "february", "march", "april", "may", "june", "july", "september", "october", "november", "december");

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
        List<SupportChatMessage> history = messageRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        boolean complaint = isComplaint(trimmed);
        boolean supportFollowUp = !complaint && isSupportFollowUp(trimmed, history);
        if (complaint || supportFollowUp) {
            ticketRepository.save(new SupportTicket(userId, bookingReference(trimmed), summarize(trimmed), trimmed));
        }
        String answer;
        try {
            answer = openAiSupportClient.respond(history);
        } catch (ServiceUnavailableException e) {
            log.warn("Support chat OpenAI response unavailable userId={} code={}", userId, e.getCode());
            answer = fallbackAnswer(trimmed, complaint, supportFollowUp);
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

    private boolean isSupportFollowUp(String body, List<SupportChatMessage> history) {
        String normalized = body.toLowerCase(Locale.ROOT);
        if (looksLikeNavigationQuestion(normalized)) {
            return false;
        }
        boolean hasRecentTicketPrompt = history.stream()
                .skip(Math.max(0, history.size() - 8))
                .filter(message -> message.getRole() == SupportChatRole.ASSISTANT)
                .map(SupportChatMessage::getBody)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("flagged") || value.contains("support ticket") || value.contains("admin"));
        return hasRecentTicketPrompt && DETAIL_TERMS.stream().anyMatch(normalized::contains);
    }

    private boolean looksLikeNavigationQuestion(String normalized) {
        return normalized.matches(".*\\b(where|how|show me|open|go to|find)\\b.*")
                && normalized.matches(".*\\b(trips|wishlist|favorites|host dashboard|listing|search|filters|cancel)\\b.*");
    }

    private String fallbackAnswer(String body, boolean complaint, boolean supportFollowUp) {
        String normalized = body.toLowerCase(Locale.ROOT);
        if (complaint || supportFollowUp) {
            String reference = bookingReference(body);
            String referenceLine = reference == null ? "" : " with booking reference " + reference;
            if (supportFollowUp) {
                return "Got it. I've added those details to the Admin support queue" + referenceLine
                        + ". If you have a payment transaction ID, property name, host name, or screenshot details, send them too so the team can review faster.";
            }
            return "Thanks for explaining. I've flagged this for Admin" + referenceLine
                    + ". Please share the booking reference if you have it, plus the property name, dates, payment reference, or host details so the team can review it faster.";
        }
        if (normalized.matches(".*\\b(hello|hi|hey|good morning|good afternoon|good evening)\\b.*")) {
            return "Hello. I can help with bookings, trips, hosting, cancellations, refunds, and using Havyn Villa. What would you like to do?";
        }
        if (normalized.contains("payment gateway") || normalized.contains("paystack") || normalized.contains("flutterwave")
                || normalized.contains("payment method") || normalized.contains("card") || normalized.contains("bank transfer")) {
            return "Havyn Villa uses secure hosted checkout. The backend is configured for Paystack by default, with Flutterwave also available. Guests can pay with supported cards and bank transfer options when the checkout provider offers them.";
        }
        if (normalized.contains("become a host") || normalized.contains("hosting") || normalized.contains("host")) {
            return "To become a host, use Become a host from the header, complete the host verification steps, then create and manage listings from Host dashboard > Listings.";
        }
        if (normalized.contains("wishlist") || normalized.contains("favorite")) {
            return "To save a place, open a property and use the heart button. Your saved homes appear on the Wishlists page when you're signed in.";
        }
        if (normalized.contains("trip") || normalized.contains("booking")) {
            return "You can review your bookings from Trips. Open a trip to see the property, dates, guests, payment status, and cancellation options.";
        }
        return """
                I can help with search, filters, bookings, wishlists, trips, becoming a host, listing management, cancellations, refunds, and support.
                Please mention the page or task you want help with, and I'll guide you step by step.
                """.trim();
    }
}
