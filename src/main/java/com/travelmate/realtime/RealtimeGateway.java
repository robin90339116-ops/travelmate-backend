package com.travelmate.realtime;

import com.fasterxml.jackson.databind.*;
import com.travelmate.auth.SessionAuthenticator;
import com.travelmate.repository.SpotRepository;
import com.travelmate.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Component
public class RealtimeGateway extends TextWebSocketHandler {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(RealtimeGateway.class);
 private final ObjectMapper mapper;
 private final SessionAuthenticator auth;
 private final SpotRepository spots;
 private final RealtimeBudget budget;
 private final com.travelmate.map.LiveLocationService locations;
 private final String key,base;
 private final boolean enabled;
 private final String model="qwen3-omni-flash-realtime-2025-12-01";
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
 private final Map<String,Connection> connections=new ConcurrentHashMap<>();
 private final Set<Long> activeUsers=ConcurrentHashMap.newKeySet();
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"realtime-guard");t.setDaemon(true);return t;});
 public RealtimeGateway(ObjectMapper mapper,SessionAuthenticator auth,SpotRepository spots,RealtimeBudget budget,com.travelmate.map.LiveLocationService locations,
   @Value("${app.ai.api-key:}") String key,@Value("${app.ai.base-url:}") String base,@Value("${app.realtime.enabled:false}") boolean enabled){
  this.mapper=mapper;this.auth=auth;this.spots=spots;this.budget=budget;this.locations=locations;this.key=key;this.base=base;this.enabled=enabled;
  timer.scheduleWithFixedDelay(this::tick,1,1,TimeUnit.SECONDS);
 }
 public Map<String,Object> status(){return Map.of("configured",enabled&&!key.isBlank(),"model",model,"maxSeconds",60,"maxTurns",3,"remainingSessions",budget.remaining(),"transport","websocket-audio-video-frames");}
 class Connection {
  final WebSocketSession browser;
  final long started=System.currentTimeMillis();
  final RealtimePolicy policy=new RealtimePolicy();
  final AtomicBoolean closed=new AtomicBoolean();
  final AtomicInteger pending=new AtomicInteger();
  final AtomicInteger sentEvents=new AtomicInteger(),droppedVideo=new AtomicInteger();
  volatile String token;
  volatile String locationToken;
  volatile long lastLocationUpdate;
  volatile Long uid;
  volatile boolean ready,responding;
  volatile int turns;
  volatile long playbackUntil, finishAfter;
  volatile java.net.http.WebSocket upstream;
  CompletableFuture<Void> sending=CompletableFuture.completedFuture(null);
  Connection(WebSocketSession session){browser=new ConcurrentWebSocketSessionDecorator(session,5000,1_048_576);}
  void emit(Object event){if(closed.get())return;try{browser.sendMessage(new TextMessage(mapper.writeValueAsString(event)));}catch(Exception e){close("浏览器连接中断");}}
  synchronized void send(Object event){
   if(upstream==null||closed.get())return;
   boolean video=event instanceof Map<?,?> m&&"input_image_buffer.append".equals(m.get("type"));
   // Keep voice responsive: stale video must not fill the bounded audio queue.
   if(video&&pending.get()>1){droppedVideo.incrementAndGet();return;}
   if(pending.incrementAndGet()>30){pending.decrementAndGet();close("上行网络拥塞，请重试");return;}
   try{String json=mapper.writeValueAsString(event);sending=sending.thenCompose(v->{if(closed.get())return CompletableFuture.completedFuture(null);if(video&&pending.get()>2){droppedVideo.incrementAndGet();return CompletableFuture.completedFuture(null);}return upstream.sendText(json,true).thenApply(w->{sentEvents.incrementAndGet();return (Void)null;});});sending.whenComplete((v,e)->{pending.decrementAndGet();if(e!=null)close("模型连接中断");});}
   catch(Exception e){pending.decrementAndGet();close("事件编码失败");}
  }
  void close(String reason){
   if(!closed.compareAndSet(false,true))return;
   log.info("Realtime ended: {}, sentEvents={}, droppedVideo={}, pending={}, durationMs={}",reason,sentEvents.get(),droppedVideo.get(),pending.get(),System.currentTimeMillis()-started);
   connections.remove(browser.getId());if(uid!=null)activeUsers.remove(uid);
   if(upstream!=null)upstream.abort();
   try{if(browser.isOpen()){browser.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type","gateway.closed","message",reason))));browser.close(CloseStatus.NORMAL);}}catch(Exception ignored){}
  }
 }
 @Override public void afterConnectionEstablished(WebSocketSession session)throws Exception{
  session.setTextMessageSizeLimit(270000);
  if(connections.size()>=8){session.close(CloseStatus.POLICY_VIOLATION);return;}
  connections.put(session.getId(),new Connection(session));
 }
 @Override protected void handleTextMessage(WebSocketSession session,TextMessage message){
  var c=connections.get(session.getId());if(c==null)return;
  try{
   var event=mapper.readTree(message.getPayload());
   if(c.token==null){start(c,event);return;}
   auth.authenticate(c.token);
   String type=event.path("type").asText();
   if(type.equals("stop")){c.close("会话已结束");return;}
   if(c.finishAfter>0)return; // Drain the last answer without accepting another paid turn.
   if(!c.ready)throw new IllegalArgumentException("模型尚未就绪");
   if(type.equals("location.update")){
    if(System.currentTimeMillis()-c.lastLocationUpdate<5000)throw ApiException.badRequest("位置更新过于频繁");
    var context=locations.require(event.path("contextToken").asText(),c.uid);
    c.locationToken=context.token();c.lastLocationUpdate=System.currentTimeMillis();
    c.send(Map.of("type","session.update","session",Map.of("instructions",locations.instructions(context))));return;
   }
   c.policy.validate(event);
   // Construct fresh events. Never forward arbitrary session instructions or credentials.
   switch(type){
    case "input_audio_buffer.append" -> c.send(Map.of("type",type,"audio",event.path("audio").asText()));
    case "input_image_buffer.append" -> c.send(Map.of("type",type,"image",event.path("image").asText()));
    case "response.cancel" -> {if(c.responding)c.send(Map.of("type",type));}
    default -> c.send(Map.of("type",type));
   }
  }catch(Exception e){c.close(e instanceof ApiException?e.getMessage():"实时请求无效或超过安全限制");}
 }
 private void start(Connection c,JsonNode event){
  if(!event.path("type").asText().equals("start"))throw new IllegalArgumentException();
  if(!enabled||key.isBlank())throw ApiException.serviceUnavailable("实时模型未启用或未配置");
  String token=event.path("token").asText();var identity=auth.authenticate(token);
  Long uid=(Long)identity.getPrincipal();
  String instructions;
  if(event.hasNonNull("contextToken")){
   var context=locations.require(event.path("contextToken").asText(),uid);c.locationToken=context.token();instructions=locations.instructions(context);
  }else{
   var spot=spots.findById(event.path("spotId").asLong(-1)).orElseThrow(()->ApiException.notFound("请先获取真实位置"));
   instructions="你是中文现场导游。当前选择的是演示景点："+spot.getName()+"，不能据此确认镜头地点。仅描述可见形状颜色，不能编造历史、价格或身份，每次不超过80字。";
  }
  if(!activeUsers.add(uid))throw ApiException.serviceUnavailable("该账号已有实时会话，请先结束");
  c.uid=uid;c.token=token;
  budget.reserve();
  URI original=URI.create(base);
  if(!"https".equals(original.getScheme())||original.getHost()==null||!original.getHost().endsWith(".cn-beijing.maas.aliyuncs.com"))throw ApiException.badRequest("实时测试仅允许已确认的北京业务空间域名");
  URI target=URI.create("wss://"+original.getHost()+"/api-ws/v1/realtime?model="+model);
  http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+key).buildAsync(target,new java.net.http.WebSocket.Listener(){
   final StringBuilder parts=new StringBuilder();
   @Override public void onOpen(java.net.http.WebSocket ws){
    if(c.closed.get()){ws.abort();return;}c.upstream=ws;
    c.send(Map.of("type","session.update","session",Map.of("modalities",List.of("text","audio"),"voice","Cherry","input_audio_format","pcm","output_audio_format","pcm","instructions",instructions,"turn_detection",Map.of("type","server_vad","threshold",0.5,"silence_duration_ms",700),"max_tokens",512)));
    ws.request(1);
   }
   @Override public CompletionStage<?> onText(java.net.http.WebSocket ws,CharSequence text,boolean last){
    try{
     parts.append(text);if(parts.length()>1_048_576){c.close("模型事件过大");return null;}
     if(last){var e=mapper.readTree(parts.toString());parts.setLength(0);providerEvent(c,e);}
    }catch(Exception e){c.close("模型返回格式错误");}finally{ws.request(1);}return null;
   }
   @Override public CompletionStage<?> onClose(java.net.http.WebSocket ws,int code,String reason){if(c.finishAfter==0)c.close("模型会话已结束");return null;}
   @Override public void onError(java.net.http.WebSocket ws,Throwable error){if(c.finishAfter==0)c.close("实时模型连接失败，请检查模型权限和网络");}
  }).whenComplete((ws,error)->{if(error!=null){Throwable root=error;while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();log.warn("Realtime connection failure type: {}",root.getClass().getSimpleName());c.close("无法连接实时模型，请检查模型权限、配置与网络");}else if(c.closed.get())ws.abort();});
 }
 private void providerEvent(Connection c,JsonNode e){
  String type=e.path("type").asText();
  switch(type){
   case "session.updated" -> {c.ready=true;c.emit(Map.of("type","gateway.ready","model",model,"maxSeconds",60,"maxTurns",3));}
   case "error" -> c.close("实时模型拒绝请求，请核对模型权限或请求格式");
   case "response.created" -> {if(++c.turns>3){c.close("已达到本次三轮讲解上限");return;}c.responding=true;c.emit(Map.of("type",type));}
   case "response.done" -> {c.responding=false;c.emit(Map.of("type",type,"usage",e.path("response").path("usage")));if(c.turns>=3){c.finishAfter=Math.max(System.currentTimeMillis(),c.playbackUntil)+500;c.emit(Map.of("type","gateway.finishing"));if(c.upstream!=null)c.upstream.abort();}}
   case "input_audio_buffer.speech_started","input_audio_buffer.speech_stopped" -> c.emit(Map.of("type",type));
   case "response.audio.delta","response.audio_transcript.delta","response.text.delta" -> {if(type.equals("response.audio.delta"))c.playbackUntil=Math.max(System.currentTimeMillis(),c.playbackUntil)+Base64.getDecoder().decode(e.path("delta").asText()).length/48;c.emit(Map.of("type",type,"delta",e.path("delta").asText()));}
   case "response.audio_transcript.done","response.text.done" -> c.emit(Map.of("type",type,"text",e.path("transcript").asText(e.path("text").asText())));
   default -> {}
  }
 }
 private void tick(){connections.values().forEach(c->{try{
  long age=System.currentTimeMillis()-c.started;
  if(c.finishAfter>0&&System.currentTimeMillis()>=c.finishAfter)c.close("已完成本次三轮讲解，可重新开启会话");
  else if(age>60000)c.close("已达到60秒会话时限");
  else if(!c.ready&&age>12000)c.close("建立连接超时");
  else if(c.token==null&&age>5000)c.close("登录验证超时");
  else if(c.token!=null){auth.authenticate(c.token);if(c.locationToken!=null)locations.require(c.locationToken,c.uid);}
 }catch(Exception e){c.close("登录会话已失效");}});}
 @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){var c=connections.get(session.getId());if(c!=null)c.close("连接已关闭");}
 @Override public void handleTransportError(WebSocketSession session,Throwable error){var c=connections.get(session.getId());if(c!=null)c.close("网络传输失败");}
 @jakarta.annotation.PreDestroy public void destroy(){connections.values().forEach(c->c.close("服务正在关闭"));timer.shutdownNow();}
}
