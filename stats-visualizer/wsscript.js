/**
 * Open a new WebSocket connection using the given parameters
 */
function openWSConnection(protocol, hostname, port, endpoint) {
    var webSocketURL = protocol + "://" + hostname + ":" + port + endpoint;
    console.log("openWSConnection::Connecting to: " + webSocketURL);
    try {
        webSocket = new WebSocket(webSocketURL);
        webSocket.onopen = function (openEvent) {
            console.log("WebSocket OPEN: " + JSON.stringify(openEvent, null, 4));
        };
        webSocket.onclose = function (closeEvent) {
            console.log("WebSocket CLOSE: " + JSON.stringify(closeEvent, null, 4));
        };
        webSocket.onerror = function (errorEvent) {
            console.log("WebSocket ERROR: " + JSON.stringify(errorEvent, null, 4));
        };
        webSocket.onmessage = function (messageEvent) {
            const wsMsg = JSON.parse(messageEvent.data);
            const values = [
                new Date(Number(wsMsg.time) * 1000),
                wsMsg.queueSize / 5,
                wsMsg.workersAmount,
                wsMsg.burndownRate,
                wsMsg.jobsArrivedInWindow
            ];
            stats.push(values);
            drawChart(stats);
            console.log("New graph values: " + values);
        };
    } catch (exception) {
        console.error(exception);
    }
}

