Simple WebSocket Service with Akka Streams / HTTP 
-----

1. a scheduled Actor checks an external webservice every 5 seconds for a conversion rate
2. stores the rate in a database only if it has changed since the last time
3. the WebSocket streams all stored values. Once all available stored data is transmitted it continues streaming real-time updates.


Run "sbt run" from the project directory. To connect to the WS endpoint you can use the Chrome extension simple-websocket-client.
The endpoint is ws://127.0.0.1:8080/ws-rates


Uses the following technologies:

* Akka Http: Websockets and HttpClient
* Akka Streams: concatenate a persistent DB content stream and a stream of real time events
* Slick 3: simple DAO based on Slick 3
* Spray Json: parse and query Json 


