package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Notification;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.repository.NotificationRepository;
import com.kabus.tracking.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backend notification abstraction.
 *
 * <p>Current channel: in-app notifications stored in MySQL and delivered via
 * the REST API (and, for connected clients, pushed over the WebSocket).
 * Future push (e.g. an optional push gateway) plugs in behind this interface
 * without making any proprietary service mandatory - see README.</p>
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification notifyUser(User user, String type, String title, String body, String dataJson) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setDataJson(dataJson);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> forUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ApiException.notFound("Notification not found."));
        if (!notification.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Notification does not belong to this user.");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public long markAllRead(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                .stream()
                .peek(n -> n.setRead(true))
                .count();
    }
}