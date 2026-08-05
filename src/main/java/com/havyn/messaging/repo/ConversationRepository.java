package com.havyn.messaging.repo;

import com.havyn.messaging.domain.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByPropertyIdAndGuestId(UUID propertyId, UUID guestId);

    Page<Conversation> findAllByHostIdOrGuestIdOrderByLastMessageAtDescCreatedAtDesc(UUID hostId, UUID guestId, Pageable pageable);
}
