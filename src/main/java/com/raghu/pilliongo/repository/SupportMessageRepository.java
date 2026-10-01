package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.SupportMessage;
import com.raghu.pilliongo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findAllByOrderByCreatedAtDesc();
    List<SupportMessage> findAllByUserOrderByCreatedAtDesc(User user);
}
