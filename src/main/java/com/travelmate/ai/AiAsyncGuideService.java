package com.travelmate.ai;
import com.travelmate.ai.AiDtos.*;
import com.travelmate.common.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;

@Service
public class AiAsyncGuideService {
 private final AiService ai;
 private final Executor executor;
 private final ObjectProvider<AiJobProducer> producer;
 private final String mode;
 private final GuideJobRepository jobs;
 private final ScheduledExecutorService poller=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"job-events");t.setDaemon(true);return t;});
 private record Subscription(String jobId,Long userId,String token,SseEmitter emitter){}
 private final Map<String,Subscription> subscriptions=new ConcurrentHashMap<>();
 private final com.travelmate.auth.SessionAuthenticator authenticator;

 public AiAsyncGuideService(AiService ai,@Qualifier("guideExecutor") Executor executor,ObjectProvider<AiJobProducer> producer,
        @Value("${app.async.mode:local}") String mode,GuideJobRepository jobs,com.travelmate.auth.SessionAuthenticator authenticator){
  this.ai=ai;this.executor=executor;this.producer=producer;this.mode=mode;this.jobs=jobs;this.authenticator=authenticator;
  poller.scheduleWithFixedDelay(this::tick,1,1,TimeUnit.SECONDS);
 }
 @jakarta.annotation.PreDestroy public void close(){subscriptions.values().forEach(s->s.emitter().complete());poller.shutdownNow();}
 public GuideJobResponse submit(ExplanationRequest r){
  return submit(r,null,null);
 }
 public GuideJobResponse submitQuestion(ExplanationRequest r,Long teamId,String question){
  return submit(r,teamId,question);
 }
 private GuideJobResponse submit(ExplanationRequest r,Long teamId,String question){
  ai.requireSpot(r.spotId());
  GuideJob job=new GuideJob();job.setId(UUID.randomUUID().toString());job.setUserId(CurrentUser.id());
  job.setTeamId(teamId);job.setQuestion(question);
  job.setSpotId(r.spotId());job.setStyle(r.style());job.setRouteContext(r.routeContext());jobs.save(job);
  GuideJobMessage msg=new GuideJobMessage(job.getId(),r.spotId(),r.style(),r.routeContext());
  try{
   if("rabbit".equals(mode)) {
    var p=producer.getIfAvailable();if(p==null)throw new IllegalStateException("MQ profile is required");p.send(msg);
   } else executor.execute(()->runJob(msg));
  }catch(Exception e){job.setStatus("failed");job.setError("任务队列不可用，请稍后重试");jobs.save(job);throw ApiException.serviceUnavailable("任务队列不可用");}
  return new GuideJobResponse(job.getId(),"queued","/api/ai/jobs/"+job.getId()+"/stream");
 }
 public record JobView(String jobId,String status,String content,String error,Instant updatedAt){}
 public JobView status(String id){return view(owned(id,CurrentUser.id()));}
 public void cancel(String id){owned(id,CurrentUser.id());jobs.cancel(id,CurrentUser.id(),Instant.now());}
 private GuideJob owned(String id,Long uid){
  var j=jobs.findById(id).orElseThrow(()->ApiException.notFound("任务不存在"));
  if(!j.getUserId().equals(uid))throw ApiException.notFound("任务不存在");return j;
 }
 private JobView view(GuideJob j){return new JobView(j.getId(),j.getStatus(),j.getContent(),j.getError(),j.getUpdatedAt());}
 public SseEmitter subscribe(String id,String bearer){
  owned(id,CurrentUser.id());
  if(subscriptions.size()>=500)throw ApiException.serviceUnavailable("订阅数量已达上限");
  SseEmitter emitter=new SseEmitter(120000L);String key=UUID.randomUUID().toString();
  subscriptions.put(key,new Subscription(id,CurrentUser.id(),bearer.substring(7),emitter));
  emitter.onCompletion(()->subscriptions.remove(key));emitter.onTimeout(()->subscriptions.remove(key));emitter.onError(e->subscriptions.remove(key));
  return emitter;
 }
 private void tick(){
  try{jobs.expire(Instant.now().minusSeconds(300),Instant.now());}catch(Exception ignored){}
  subscriptions.forEach((key,s)->{
   try{
    authenticator.authenticate(s.token());
    var j=owned(s.jobId(),s.userId());
    boolean done=Set.of("completed","failed","cancelled").contains(j.getStatus());
    s.emitter().send(SseEmitter.event().name(done?"result":"progress").data(view(j)));
    if(done){subscriptions.remove(key);s.emitter().complete();}
   }catch(Exception e){subscriptions.remove(key);s.emitter().completeWithError(e);}
  });
 }
 public void runJob(GuideJobMessage message){
  if(jobs.claim(message.jobId(),Instant.now())==0)return;
  try{
   var j=jobs.findById(message.jobId()).orElseThrow();
   String text=j.getQuestion()==null ? ai.explanation(new ExplanationRequest(j.getSpotId(),j.getStyle(),j.getRouteContext())).content()
           : ai.chat(new ChatRequest(j.getSpotId(),j.getQuestion(),null)).answer();
   jobs.finish(j.getId(),"completed",text,null,Instant.now());
  }catch(Exception e){
   jobs.finish(message.jobId(),"failed",null,"讲解生成失败，请检查服务配置或稍后重试",Instant.now());
   if("rabbit".equals(mode))throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("AI generation failed",e);
  }
 }
}
