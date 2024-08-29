const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8080/gs-guide-websocket'
});

stompClient.onConnect = (frame) => {
    setConnected(true);
    console.log('Connected: ' + frame);

    var currentSession = $("#currentSession").val();
    var topic = "/topic/liveSession/" + currentSession;
    console.log("Connecting to " + topic);
    stompClient.subscribe(topic, (liveSession) => {
        updateSqlText(JSON.parse(liveSession.body));
    });
};

stompClient.onWebSocketError = (error) => {
    console.error('Error with websocket', error);
};

stompClient.onStompError = (frame) => {
    console.error('Broker reported error: ' + frame.headers['message']);
    console.error('Additional details: ' + frame.body);
};

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);
    if (connected) {
        $("#conversation").show();
    } else {
        $("#conversation").hide();
    }
    $("#greetings").html("");
}

function joinSession(clickedButton) {
    console.log($(clickedButton).text());
    $("#currentSession").val($(clickedButton).text());
    connect();
}

function connect() {
    stompClient.activate();
}

function disconnect() {
    stompClient.deactivate();
    setConnected(false);
    console.log("Disconnected");
}


function updateSqlText(message) {
    console.log(message);
    $("#previewSql").val(message.sql);
    $("#currentTamperProofSignature").val(message.tamperProofSignature);
}

function runQuery() {
    var currentSessionId = $("#currentSession").val();
    $.ajax({
            url: "/liveSession/" + currentSessionId + "/execute",
            type: "PUT",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({
                executionRequestId: currentSessionId,
                sql: $("#previewSql").val(),
                tamperProofSignature: $("#currentTamperProofSignature").val()
            }),
            success: function (data) {
                $("#greetings").append("<tr><td>Session created for " + data.id + "</td></tr>");
            }
        }
    );
}

function listSessions() {
    $.get("/liveSession", {}, function (sessions) {
        $("#sessionList").html(
            $(sessions).map(function () {
                return "<tr><td><button class='joinSession' type=\"submit\">" + this.executionRequestId + "</button></td></tr>";
            }).get().join());

        //after addimg buttons, let's add a click handler
        $(".joinSession").click(function () {
            joinSession(this);
        })
    })

}

$(function () {
    $("form").on('submit', (e) => e.preventDefault());
    listSessions();
    $("#listSessions").click(() => listSessions());
    $("#connect").click(() => connect());
    $("#disconnect").click(() => disconnect());
    $("#send").click(() => sendName());
    $("#runQuery").click(() => runQuery());
});
