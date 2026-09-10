package com.travelmate.ai;
import com.travelmate.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.*;

/** 阿里云NLS短句REST适配；不写入原始音频文件。 */
@RestController @RequestMapping("/api")
public class SpeechController {
 private final String key,token,base;
 private final RestClient client;
 @org.springframework.beans.factory.annotation.Autowired
 public SpeechController(@Value("${app.speech.app-key:}") String key,@Value("${app.speech.token:}") String token,
       @Value("${app.speech.base-url:https://nls-gateway-cn-shanghai.aliyuncs.com}") String base){
  this.key=key;this.token=token;this.base=base;
  var f=new org.springframework.http.client.SimpleClientHttpRequestFactory();f.setConnectTimeout(5000);f.setReadTimeout(30000);
  client=RestClient.builder().requestFactory(f).build();
 }
 public record TtsRequest(String text){}
 SpeechController(String key,String token,String base,RestClient client){this.key=key;this.token=token;this.base=base;this.client=client;}
 public record AsrRequest(String audioBase64,String format){}
 public record Audio(String audioBase64,String mimeType,int sampleRate){}
 private void configured(){if(key.isBlank()||token.isBlank())throw ApiException.serviceUnavailable("语音服务未配置");}
 @PostMapping("/tts")
 public Result<Audio> tts(@RequestBody TtsRequest request){
  if(request.text()==null||request.text().isBlank()||request.text().length()>300)throw ApiException.badRequest("文本长度须为1至300字");
  configured();
  try{
   var response=client.post().uri(base+"/stream/v1/tts").contentType(MediaType.APPLICATION_JSON)
      .body(Map.of("appkey",key,"token",token,"text",request.text(),"format","mp3","sample_rate",16000)).retrieve().toEntity(byte[].class);
   var type=response.getHeaders().getContentType();byte[] bytes=response.getBody();
   if(type==null||!type.getType().equals("audio")||bytes==null||bytes.length==0)throw ApiException.serviceUnavailable("语音合成返回无效音频");
   return Result.ok(new Audio(Base64.getEncoder().encodeToString(bytes),"audio/mpeg",16000));
  }catch(org.springframework.web.client.RestClientException e){throw ApiException.serviceUnavailable("语音合成暂时不可用");}
 }
 @PostMapping("/asr")
 public Result<?> asr(@RequestBody AsrRequest request){
  if(request.audioBase64()==null||request.audioBase64().length()>2_600_000||!Set.of("pcm","wav").contains(Objects.toString(request.format(),"")))
      throw ApiException.badRequest("请提交不超过60秒的16kHz单声道PCM/WAV录音");
  byte[] bytes;
  try{bytes=Base64.getDecoder().decode(request.audioBase64());}catch(IllegalArgumentException e){throw ApiException.badRequest("录音Base64格式错误");}
  if(bytes.length==0||bytes.length>1_920_100)throw ApiException.badRequest("录音大小超出限制");configured();
  try{
   var uri=java.net.URI.create(base+"/stream/v1/asr?appkey="+java.net.URLEncoder.encode(key,java.nio.charset.StandardCharsets.UTF_8)+"&format="+request.format()+"&sample_rate=16000");
   Map<?,?> data=client.post().uri(uri).header("X-NLS-Token",token).contentType(MediaType.APPLICATION_OCTET_STREAM).body(bytes).retrieve().body(Map.class);
   if(data==null||!"20000000".equals(String.valueOf(data.get("status"))))throw ApiException.serviceUnavailable("语音识别失败");
   return Result.ok(Map.of("text",Objects.toString(data.get("result"),"")));
  }catch(org.springframework.web.client.RestClientException e){throw ApiException.serviceUnavailable("语音识别暂时不可用");}
 }
}
