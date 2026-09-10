package com.travelmate.realtime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RealtimePolicyTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void acceptsPcmThenJpeg()throws Exception{var p=new RealtimePolicy();p.validate(json.readTree("{\"type\":\"input_audio_buffer.append\",\"audio\":\"AAA=\"}"));p.validate(json.readTree("{\"type\":\"input_image_buffer.append\",\"image\":\"/9j/2Q==\"}"));}
 @Test void rejectsImageBeforeAudio()throws Exception{var e=json.readTree("{\"type\":\"input_image_buffer.append\",\"image\":\"/9j/2Q==\"}");assertThrows(IllegalArgumentException.class,()->new RealtimePolicy().validate(e));}
 @Test void rejectsSessionInjection()throws Exception{var e=json.readTree("{\"type\":\"session.update\",\"session\":{}}");assertThrows(IllegalArgumentException.class,()->new RealtimePolicy().validate(e));}
 @Test void rejectsInvalidPcm()throws Exception{for(String audio:new String[]{"", "AA==", "not-base64"}){var e=json.createObjectNode().put("type","input_audio_buffer.append").put("audio",audio);assertThrows(IllegalArgumentException.class,()->new RealtimePolicy().validate(e));}}
 @Test void rateLimitsImages()throws Exception{var p=new RealtimePolicy();p.validate(json.readTree("{\"type\":\"input_audio_buffer.append\",\"audio\":\"AAA=\"}"));var e=json.readTree("{\"type\":\"input_image_buffer.append\",\"image\":\"/9j/2Q==\"}");p.validate(e);assertThrows(IllegalArgumentException.class,()->p.validate(e));}
 @Test void limitsEvents()throws Exception{var p=new RealtimePolicy();var e=json.readTree("{\"type\":\"response.cancel\"}");for(int i=0;i<60;i++)p.validate(e);assertThrows(IllegalArgumentException.class,()->p.validate(e));}
}
