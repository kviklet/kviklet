package dev.kviklet.kviklet.controller;

import dev.kviklet.kviklet.security.CurrentUser;
import dev.kviklet.kviklet.security.UserDetailsWithId;
import dev.kviklet.kviklet.service.ExecutionRequestService;
import dev.kviklet.kviklet.service.dto.*;
import jakarta.validation.Valid;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;


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


    public SharedSessionController(final SimpMessagingTemplate template, ExecutionRequestService executionRequestService) {
        this.template = template;
        this.executionRequestService = executionRequestService;
    }

    @GetMapping
    public List<LiveSQLSession> getLiveSessions() {
        //get all sessions
        //TODO: ideally filter down in the DB
        return this.executionRequestService.list().stream()
                .filter(r -> ((DatasourceExecutionRequest)r.getRequest()).getConnection().getReviewConfig().getFourEyesRequired())
                .map(r -> {
                    final DatasourceExecutionRequest request = (DatasourceExecutionRequest) r.getRequest();
                    final ExecutionRequestId id = request.getId();
                    return new LiveSQLSession(id.toString(), "", null);
                }).toList();

        //FIXME - may want to authenticate based on groups/etc.

        //todo - store session somewhere for future retrievals... might want to audit it...
        //hook into the existing session side of things already in existence?

        // establish web socket
        //persist session to db or redis: UUID.randomUUID().toString()
    }


    @PostMapping("")
    public LiveSQLSession createSession(
            @Valid @RequestBody
            CreateExecutionRequestRequest executionRequest,
            @CurrentUser UserDetailsWithId currentUser) {
        final String authorSecret = UUID.randomUUID().toString();

        final ExecutionRequestDetails executionRequestDetails = executionRequestService.create(
                executionRequest.getConnectionId(), executionRequest,
                currentUser.getId(),
                //TODO- change to secure random?
                authorSecret);

        //Question - does approver need to know session? yes if there are multiple editors
        //TODO - do some basic SQL validation for security reasons (SQL injection, etc.)
        //TODO - want to time-out websocket sessions to avoid future re-use

        // establish web socket
        //persist session to db or redis: UUID.randomUUID().toString()
        return new LiveSQLSession(executionRequestDetails.getId(), "", null);
    }

    //requested on key stroke
    @PutMapping("/{id}")
    public void updateCurrentSQL(@RequestBody LiveSQLSession liveSQLSession) {
        //TODO: think about validating 'id' and authenticating it... - otherwise can post to any channel
        //TODO - do some basic SQL validation for security reasons (SQL injection, etc.)
        //FIXME: add security and go over this: https://github.com/SatvikNema/satchat/blob/main/src/main/java/com/satvik/satchat/service/ChatService.java

        LOGGER.info("Updating SQL for live session: {}", liveSQLSession.sql());

        String authorSecret =
                ((DatasourceExecutionRequest) this.executionRequestService.get(
                 new ExecutionRequestId(liveSQLSession.executionRequestId())).getRequest()).getFourEyesAuthorSecret();

        //add the hmac to the live sessions
        LiveSQLSession tamperProofSession = new LiveSQLSession(liveSQLSession.executionRequestId(), liveSQLSession.sql(),
                getHmacForPayload(liveSQLSession, authorSecret));

        //then update all listeners to this session
        // send data to other sessions
        //TODO - the subscription should be authenticated and validated - e.g. prevent listening to '#'
        this.template.convertAndSend("/topic/liveSession/" + tamperProofSession.executionRequestId(), tamperProofSession);
    }

    @PutMapping("/{id}/execute")
    public void executeQuery(
            @PathVariable("id") String executionRequestId,
            @RequestBody LiveSQLSession liveSQLSession,
            @CurrentUser UserDetailsWithId currentUser) {
        //TODO - probably check that ID is the same as the liveSQL session
        final ExecutionRequestDetails authorsRequest = executionRequestService.get(new ExecutionRequestId(liveSQLSession.executionRequestId()));

        //FIXME - this probably goes down into the auth/policy engine
        verifySignature(liveSQLSession, Objects.requireNonNull(((DatasourceExecutionRequest) authorsRequest.getRequest()).getFourEyesAuthorSecret()));

        //set the request as executable - as we are the 2nd set of four eyes
        this.executionRequestService.createReview(new ExecutionRequestId(authorsRequest.getId()),
                new CreateReviewRequest("Auto-approved through four-eyes checks", ReviewAction.APPROVE),
                currentUser.getId());

        final ExecutionResult execute = this.executionRequestService.execute(new ExecutionRequestId(liveSQLSession.executionRequestId()), liveSQLSession.sql(), currentUser.getId());

        LOGGER.info("{}", ((DBExecutionResult)  execute).getResults());
    }

    private void verifySignature(final LiveSQLSession liveSQLSession,
                                 String authorSecret) {
        //initialise the hmac crypto
        String hmac = getHmacForPayload(liveSQLSession, authorSecret);

        if (!liveSQLSession.tamperProofSignature().equals(hmac)) {
            //TODO - convert to proper error handling
            throw new RuntimeException("Throw a 401 here");
        }

        //TODO - run query and display results..
//        return ExecutionResponse.fromDto(this.executionRequestService.execute(new ExecutionRequestId(liveSQLSession.executionRequestId()),
//                liveSQLSession.sql(), currentUser.getId()));
    }

    private String getHmacForPayload(final LiveSQLSession liveSQLSession, final String authorSecret) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(authorSecret.getBytes(), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            String hmacablePayload = getQueryPayload(liveSQLSession);
            final byte[] computedHmac = mac.doFinal(hmacablePayload.getBytes());
            return Hex.toHexString(computedHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private String getQueryPayload(final LiveSQLSession liveSQLSession) {
        //get secret from backend somewhere
        return liveSQLSession.executionRequestId() + liveSQLSession.sql();
    }
}
