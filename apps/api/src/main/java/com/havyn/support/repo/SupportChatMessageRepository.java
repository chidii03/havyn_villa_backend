package com.havyn.support.repo;

import com.havyn.support.domain.SupportChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportChatMessageRepository extends JpaRepository<SupportChatMessage, UUID> {

    List<SupportChatMessage> findAllByUserIdOrderByCreatedAtAsc(UUID userId);
}
