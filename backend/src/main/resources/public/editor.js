const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8080/gs-guide-websocket'
});

stompClient.onConnect = (frame) => {
    setConnected(true);
    console.log('Connected: ' + frame);
    stompClient.subscribe('/topic/greetings', (greeting) => {
        showGreeting(JSON.parse(greeting.body).content);
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

function createSession() {
    $.ajax({
        url: "/liveSession",
        type: "POST",
        data: JSON.stringify({
            connectionId: $("#connections").val(),
            title: $("#requestTitle").val(),
            type: "TemporaryAccess",
            connectionType: "DATASOURCE",
            description: "4-eyes session default"
        }),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function (data) {
            $("#currentSessionId").val(data.executionRequestId);
            $("#sqlExecutionBox").css("display", "block");

        }
    })
}

function populateConnections() {

    $.get("/connections/", {}, function (connections) {
        $("#connections").html(
            $(connections).map(function () {
                return "<option>" + this.id + "</option>";
            }).get().join());
    })
}

function sendSqlUpdate() {
    $.ajax({
            url: "/liveSession/" + $("#currentSessionId").val(),
            type: "PUT",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({
                executionRequestId: $("#currentSessionId").val(),
                sql: $("#sql").val()
            }),
            success: function (data) {
                $("#greetings").append("<tr><td>Session created for " + data.id + "</td></tr>");
            }
        }
    );
}


function showGreeting(message) {
    $("#greetings").append("<tr><td>" + message + "</td></tr>");
}

$(function () {
    $("form").on('submit', (e) => e.preventDefault());
    $("#startSession").click(() => createSession());
    $("#sql").keyup(() => sendSqlUpdate())

    populateConnections();
});
