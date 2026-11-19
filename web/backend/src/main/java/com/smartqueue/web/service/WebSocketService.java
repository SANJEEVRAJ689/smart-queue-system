package com.smartqueue.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyQueueUpdated(int serviceId) {
        try {
            messagingTemplate.convertAndSend("/topic/queue/" + serviceId, Map.of("serviceId", serviceId, "event", "QUEUE_UPDATED"));
            messagingTemplate.convertAndSend("/topic/display", Map.of("event", "DISPLAY_REFRESH"));
        } catch (Exception e) {
            logger.warn("Failed to send WebSocket queue notification", e);
        }
    }

    public void notifyTokenUpdated(String trackingCode, Object payload) {
        if (trackingCode == null || trackingCode.isBlank()) return;
        try {
            messagingTemplate.convertAndSend("/topic/token/" + trackingCode, payload);
        } catch (Exception e) {
            logger.warn("Failed to send WebSocket token notification for tracking code {}", trackingCode, e);
        }
    }

    public void notifyDisplayBoard(Object displayPayload) {
        try {
            messagingTemplate.convertAndSend("/topic/display", displayPayload);
        } catch (Exception e) {
            logger.warn("Failed to send WebSocket display notification", e);
        }
    }
}
