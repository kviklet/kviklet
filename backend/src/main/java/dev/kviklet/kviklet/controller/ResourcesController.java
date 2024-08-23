package dev.kviklet.kviklet.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ResourcesController {

    @GetMapping
    public String home() {
        return "/editor.html";
    }


    @GetMapping("/approver")
    public String approver() {
        return "/approver.html";
    }

//    @MessageMapping("/chat/sendMessage/{convId}")
//    public void sendMessageToConvId(
//            @Payload LiveSQLSession chatMessage,
//            SimpMessageHeaderAccessor headerAccessor,
//            @DestinationVariable("convId") String conversationId) {
//        sharedSessionService.sendMessageToConvId(chatMessage, conversationId, headerAccessor);
//        return chatMessage;
//    }

//    @MessageMapping("/hello")
//    @SendTo("/topic/greetings")
//    public LiveSQLSession greeting(HelloMessage message) throws Exception {
//        Thread.sleep(1000); // simulated delay
//        return new LiveSQLSession("s", "Hello, " + HtmlUtils.htmlEscape(message.getName()) + "!");
//    }

}