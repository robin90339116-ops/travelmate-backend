package com.travelmate.ai;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;
import com.travelmate.common.ApiException;

class SpeechContractTest {
 @Test void ttsAudioAndAsrContract(){
  var builder=RestClient.builder();var mock=MockRestServiceServer.bindTo(builder).build();
  var service=new SpeechController("key","token","https://speech.invalid",builder.build());
  mock.expect(requestTo("https://speech.invalid/stream/v1/tts")).andRespond(withSuccess(new byte[]{1,2,3},MediaType.parseMediaType("audio/mpeg")));
  mock.expect(requestTo("https://speech.invalid/stream/v1/asr?appkey=key&format=pcm&sample_rate=16000"))
      .andExpect(header("X-NLS-Token","token")).andRespond(withSuccess("{\"status\":20000000,\"result\":\"你好\"}",MediaType.APPLICATION_JSON));
  assertNotNull(service.tts(new SpeechController.TtsRequest("你好")));
  assertNotNull(service.asr(new SpeechController.AsrRequest("AQID","pcm")));mock.verify();
 }
 @Test void invalidInputAndMissingCredentials(){
  var service=new SpeechController("","","https://unused");
  assertThrows(ApiException.class,()->service.tts(new SpeechController.TtsRequest("")));
  assertThrows(ApiException.class,()->service.tts(new SpeechController.TtsRequest("你好")));
  assertThrows(ApiException.class,()->service.asr(new SpeechController.AsrRequest("not base64","pcm")));
 }
}
