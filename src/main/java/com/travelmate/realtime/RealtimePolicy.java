package com.travelmate.realtime;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;

public final class RealtimePolicy {
 private long window=System.nanoTime(),lastImage;
 private int audioBytes,events;
 private boolean audioStarted;
 public synchronized void validate(JsonNode event){
  long now=System.nanoTime();if(now-window>=1_000_000_000L){window=now;audioBytes=0;events=0;}
  if(++events>60)throw new IllegalArgumentException("事件发送过快");
  String type=event.path("type").asText();
  switch(type){
   case "input_audio_buffer.append" -> {
    String s=event.path("audio").asText();if(s.length()>44000)throw new IllegalArgumentException("音频块过大");
    byte[] bytes=Base64.getDecoder().decode(s);
    if(bytes.length==0||bytes.length%2!=0||(audioBytes+=bytes.length)>96000)throw new IllegalArgumentException("音频格式或速率错误");
    audioStarted=true;
   }
   case "input_image_buffer.append" -> {
    String s=event.path("image").asText();if(!audioStarted||s.length()>256000||now-lastImage<900_000_000L)throw new IllegalArgumentException("请先发送音频，视频帧最多每秒一张");
    byte[] bytes=Base64.getDecoder().decode(s);if(bytes.length<4||(bytes[0]&255)!=255||(bytes[1]&255)!=216)throw new IllegalArgumentException("仅支持JPEG视频帧");
    lastImage=now;
   }
   case "response.cancel","input_audio_buffer.clear" -> {}
   default -> throw new IllegalArgumentException("不支持的实时事件");
  }
 }
}
