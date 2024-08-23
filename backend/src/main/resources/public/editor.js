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

    $.post("/liveSession", {}, function (data) {
        $("#currentSessionId").val(data.id);
    });
}


function sendSqlUpdate() {
    $.ajax({
            url: "/liveSession/" + $("#currentSessionId").val(),
            type: "PUT",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({
                id: $("#currentSessionId").val(),
                sql: $("#sql").val()
            }),
            success: function (data) {
                    $("#greetings").append("<tr><td>Session created for " + data.id + "</td></tr>");
                }
        }
    )
    ;


}

function showGreeting(message) {
    $("#greetings").append("<tr><td>" + message + "</td></tr>");
}

$(function () {
    $("form").on('submit', (e) => e.preventDefault());
    $("#startSession").click(() => createSession());
    $("#sql").keyup(() => sendSqlUpdate())
    // $( "#disconnect" ).click(() => disconnect());
    // $( "#send" ).click(() => sendName());
});
