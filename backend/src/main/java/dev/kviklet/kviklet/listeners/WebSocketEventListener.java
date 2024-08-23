package dev.kviklet.kviklet.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
public class WebSocketEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    @SendToUser
    public void handleSubscribeEvent(SessionSubscribeEvent sessionSubscribeEvent) {

        String subscribedChannel =
                (String) sessionSubscribeEvent.getMessage().getHeaders().get("simpDestination");
        String simpSessionId =
                (String) sessionSubscribeEvent.getMessage().getHeaders().get("simpSessionId");
        if (subscribedChannel == null) {
            LOGGER.error("SUBSCRIBED TO NULL?? WAT?!");
            return;
        }

        //FIXME - add authentication and validation of subscriptions here.
        LOGGER.info("Got session [{}] and destination [{}]", simpSessionId, subscribedChannel);

//        simpSessionIdToSubscriptionId.put(simpSessionId, subscribedChannel);
//        onlineOfflineService.addUserSubscribed(sessionSubscribeEvent.getUser(), subscribedChannel);
    }
}
