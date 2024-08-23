package dev.kviklet.kviklet.controller;

import dev.kviklet.kviklet.security.CurrentUser;
import dev.kviklet.kviklet.security.UserDetailsWithId;
import dev.kviklet.kviklet.service.ExecutionRequestService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


/**
 * Flow is:
 * 1. Editor - creates session (post /). This effectively persists a temporary live session.
 * 2. Approver - gets lists of all session (get /)
 * 3. Editor - updates SQL box - onkey event sends SQL update - this in turn passes to subscribers of event
 * 4.
 */
@RequestMapping("/liveSession")
@RestController
public class SharedSessionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedSessionController.class);

    private ExecutionRequestService executionRequestService;

    private SimpMessagingTemplate template;

    public SharedSessionController(final SimpMessagingTemplate template) {
        this.template = template;
    }

    @GetMapping
    public List<LiveSQLSession> getLiveSessions() {
        //get all sessions
        //FIXME - may want to authenticate based on groups/etc.

        //todo - store session somewhere for future retrievals... might want to audit it...
        //hook into the existing session side of things already in existence?

        // establish web socket
        //persist session to db or redis: UUID.randomUUID().toString()
        return List.of(new LiveSQLSession("x-123" , ""),
                new LiveSQLSession("x-456", "")
                );
    }


    @PostMapping("")
    public LiveSQLSession createSession(
            @Valid @RequestBody
            CreateExecutionRequestRequest executionRequest,
            @CurrentUser UserDetailsWithId currentUser) {
        executionRequestService.create(executionRequest.getConnectionId(), executionRequest, currentUser.getId());


//        LOGGER.info("Joining session with id {}", id);
//FIXME - does approver need to know session? yes if there are multiple editors

        //todo - store session somewhere for future retrievals... might want to audit it...
        //hook into the existing session side of things already in existence?

        // establish web socket
        //persist session to db or redis: UUID.randomUUID().toString()
        return new LiveSQLSession("x-123" , "");
    }

    //requested on key stroke
    @PutMapping("/{id}")
    public void updateCurrentSQL(@RequestBody LiveSQLSession liveSQLSession) {
        //FIXME: think about validating 'id' and authenticating it...
//FIXME- do some basic SQL validation for security reasons
        //FIXME - the subscriptiono should be authenticated - e.g. prevent listening to '#'


        //FIXME: add security and go over this: https://github.com/SatvikNema/satchat/blob/main/src/main/java/com/satvik/satchat/service/ChatService.java
        LOGGER.info("Updating SQL for live session: {}", liveSQLSession.sql());

        //then update all listeners to this session

        // send data to other sessions
        this.template.convertAndSend("/topic/liveSession/" + liveSQLSession.id(), liveSQLSession);
    }


//    @GetMapping("/{id}")
//    public LiveSQLSession joinSession(@PathVariable("id") String id) {
//        LOGGER.info("Joining session with id {}", id);
//
//         establish web socket
//        return new LiveSQLSession(UUID.randomUUID().toString(), "");
//    }

//    @PostMapping
//    public void executeSql() {
//        check not owner of session
//
//        hook into 'execution' of SQL code
//
//    }
}
