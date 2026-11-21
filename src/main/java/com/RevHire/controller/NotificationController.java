package com.RevHire.controller;

import com.RevHire.dto.NotificationDTO;
import com.RevHire.service.NotificationService;

import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Logger logger = LogManager.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    @GetMapping("/notification")
    public String showNotificationsPage(final HttpSession session) {
        logger.info("Opening notifications page");

        if (session.getAttribute("userId") == null) {
            logger.warn("Unauthorized attempt to access notifications page");
            return "redirect:/auth/login";
        }

        return "notifications";
    }

    @GetMapping("/api/{userId}")
    @ResponseBody
    public List<NotificationDTO> getNotifications(@PathVariable final Long userId) {
        logger.info("Fetching notifications for userId {}", userId);
        return notificationService.getUserNotifications(userId);
    }

    @PutMapping("/api/{id}/read")
    @ResponseBody
    public ResponseEntity<?> markRead(@PathVariable final Long id) {
        logger.info("Marking notification {} as read", id);
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}