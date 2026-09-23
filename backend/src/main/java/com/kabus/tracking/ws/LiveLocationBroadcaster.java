package com.kabus.tracking.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts live bus updates to all subscribers of /topic/live.
 *
 * <p>For horizontal scaling, move this behind a shared broker (Redis/STOMP
 * relay) - see docs/SCALABILITY.md. Single-instance development and small
 * deployments use the in-memory simple broker.</p>
 */
@Component
public class LiveLocationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveLocationBroadcaster.class);
    public static final String DESTINATION = "/topic/live";

    private final SimpMessagingTemplate messagingTemplate;

    public LiveLocationBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(LiveLocationMessage message) {
        messagingTemplate.convertAndSend(DESTINATION, message);
        log.debug("Broadcast {}", message);
    }
}