package com.RevHire.service.impl;

import com.RevHire.dto.NotificationDTO;
import com.RevHire.entity.Notification;
import com.RevHire.entity.User;
import com.RevHire.repository.NotificationRepository;
import com.RevHire.repository.UserRepository;
import com.RevHire.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LogManager.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    public void sendNotification(final Long userId, final String message) {
        logger.info("Sending notification to userId: {}", userId);

        final User user = userRepository.findById(userId).orElseThrow(() -> {
                    logger.error("User not found while sending notification. userId: {}", userId);
                    return new RuntimeException("User not found with ID: " + userId);
                });

        final Notification notification = new Notification();
        notification.setUser(user);
        notification.setMessage(message);
        notification.setIsRead(false);

        notificationRepository.save(notification);
        logger.info("Notification sent successfully to userId: {}", userId);
    }

    @Override
    public List<NotificationDTO> getUserNotifications(final Long userId) {
        logger.info("Fetching notifications for userId: {}", userId);
        final List<Notification> notifications = notificationRepository.findByUserUserId(userId);
        logger.debug("Total notifications found for userId {} : {}", userId, notifications.size());

        return notifications.stream()
                .map(n -> new NotificationDTO(
                        n.getNotificationId(),
                        n.getUser().getUserId(),
                        n.getMessage(),
                        n.getIsRead()
                ))
                .toList();
    }

    @Override
    public void markAsRead(final Long notificationId) {
        logger.info("Marking notification as read. notificationId: {}", notificationId);

        final Notification n = notificationRepository.findById(notificationId).orElseThrow(() -> {
                    logger.error("Notification not found with ID: {}", notificationId);
                    return new RuntimeException("Notif not found");
                });

        n.setIsRead(true);
        notificationRepository.save(n);
        logger.info("Notification marked as read successfully. notificationId: {}", notificationId);
    }
}