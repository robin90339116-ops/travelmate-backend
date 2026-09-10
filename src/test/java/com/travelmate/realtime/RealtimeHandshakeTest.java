package com.travelmate.realtime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.realtime.enabled=false","spring.datasource.url=jdbc:h2:mem:realtime-handshake;DB_CLOSE_DELAY=-1"})
class RealtimeHandshakeTest {
 @LocalServerPort int port;
 @Test void realtimePathIsNotInterceptedBySockJs()throws Exception{
  var message=new CompletableFuture<String>();
  var session=new StandardWebSocketClient().execute(new TextWebSocketHandler(){@Override protected void handleTextMessage(WebSocketSession session,TextMessage text){message.complete(text.getPayload());}},"ws://127.0.0.1:"+port+"/ws/realtime").get(5,TimeUnit.SECONDS);
  try{session.sendMessage(new TextMessage("{\"type\":\"start\"}"));assertTrue(message.get(5,TimeUnit.SECONDS).contains("实时模型未启用"));}finally{session.close();}
 }
}
