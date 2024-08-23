const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8080/gs-guide-websocket'
});

stompClient.onConnect = (frame) => {
    setConnected(true);
    console.log('Connected: ' + frame);
    stompClient.subscribe('/topic/liveSession/' +
        $("#currentSession").val(), (liveSession) => {
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
    $("#previewSql").val(message.sql);
}

function listSessions() {
    $.get("/liveSession", {}, function (sessions) {
        $("#greetings").html(
            $(sessions).map(function () {
                console.log("hi");
                return "<tr><td><button class='joinSession' type=\"submit\">" + this.id + "</button></td></tr>";
            }).get().join());

        //after addimg buttons, let's add a click handler
        $(".joinSession").click(function () {
            joinSession(this);
        })
    })

}

$(function () {
    $("form").on('submit', (e) => e.preventDefault());
    $("#listSessions").click(() => listSessions());
    $("#connect").click(() => connect());
    $("#disconnect").click(() => disconnect());
    $("#send").click(() => sendName());
});
