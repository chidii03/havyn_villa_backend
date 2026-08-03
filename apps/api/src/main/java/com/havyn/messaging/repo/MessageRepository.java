package com.havyn.messaging.repo;

import com.havyn.messaging.domain.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    List<Message> findAllByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID senderId);
}
