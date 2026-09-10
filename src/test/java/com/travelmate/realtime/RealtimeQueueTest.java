package com.travelmate.realtime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RealtimeQueueTest {
 @Test void audioQueueRemainsBoundedAndStopsAfterClose(){
  var gateway=new RealtimeGateway(new ObjectMapper(),null,null,null,null,"","",false);
  try{
   var browser=mock(WebSocketSession.class);when(browser.getId()).thenReturn("bounded-test");
   var upstream=mock(java.net.http.WebSocket.class);var blocked=new CompletableFuture<java.net.http.WebSocket>();
   when(upstream.sendText(anyString(),eq(true))).thenReturn(blocked);
   var c=gateway.new Connection(browser);c.upstream=upstream;
   for(int i=0;i<31;i++)c.send(Map.of("type","input_audio_buffer.append","audio","AAA="));
   assertTrue(c.closed.get());assertEquals(30,c.pending.get());verify(upstream).abort();
   blocked.complete(upstream);assertEquals(0,c.pending.get());
   c.send(Map.of("type","input_audio_buffer.append","audio","AAA="));
   verify(upstream,times(1)).sendText(anyString(),eq(true));
  }finally{gateway.destroy();}
 }
 @Test void failedSendClosesConnectionAndDrainsPending(){
  var gateway=new RealtimeGateway(new ObjectMapper(),null,null,null,null,"","",false);
  try{
   var browser=mock(WebSocketSession.class);when(browser.getId()).thenReturn("failure-test");
   var upstream=mock(java.net.http.WebSocket.class);var blocked=new CompletableFuture<java.net.http.WebSocket>();
   when(upstream.sendText(anyString(),eq(true))).thenReturn(blocked);
   var c=gateway.new Connection(browser);c.upstream=upstream;
   c.send(Map.of("type","input_audio_buffer.append","audio","AAA="));c.send(Map.of("type","input_audio_buffer.append","audio","AAA="));
   blocked.completeExceptionally(new java.io.IOException("test disconnect"));
   assertTrue(c.closed.get());assertEquals(0,c.pending.get());verify(upstream).abort();
  }finally{gateway.destroy();}
 }
 @Test void encodingFailureDoesNotLeakPendingCount(){
  var gateway=new RealtimeGateway(new ObjectMapper(),null,null,null,null,"","",false);
  try{
   var browser=mock(WebSocketSession.class);when(browser.getId()).thenReturn("encoding-test");
   var upstream=mock(java.net.http.WebSocket.class);var c=gateway.new Connection(browser);c.upstream=upstream;
   c.send(new Object());assertTrue(c.closed.get());assertEquals(0,c.pending.get());verify(upstream,never()).sendText(anyString(),eq(true));
  }finally{gateway.destroy();}
 }
 @Test void dropsVideoUnderBackpressureButKeepsVoiceBounded(){
  var gateway=new RealtimeGateway(new ObjectMapper(),null,null,null,null,"","",false);
  try{
   var browser=mock(WebSocketSession.class);when(browser.getId()).thenReturn("queue-test");
   var upstream=mock(java.net.http.WebSocket.class);
   var blocked=new CompletableFuture<java.net.http.WebSocket>();
   when(upstream.sendText(anyString(),eq(true))).thenReturn(blocked);
   var connection=gateway.new Connection(browser);connection.upstream=upstream;
   var audio=Map.of("type","input_audio_buffer.append","audio","AAA=");
   connection.send(audio);connection.send(audio);
   for(int i=0;i<50;i++)connection.send(Map.of("type","input_image_buffer.append","image","frame"));
   assertEquals(2,connection.pending.get());assertFalse(connection.closed.get());
   blocked.complete(upstream);assertEquals(0,connection.pending.get());
   verify(upstream,times(2)).sendText(anyString(),eq(true));
  }finally{gateway.destroy();}
 }
}
