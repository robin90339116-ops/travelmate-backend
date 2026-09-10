package com.travelmate.config;
import com.travelmate.common.CurrentUser;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 单实例限流，部署多个实例时应在入口网关增加共享配额。 */
@Configuration
public class RequestLimits implements WebMvcConfigurer {
 private record Window(long minute,AtomicInteger count){}
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 @Override public void addInterceptors(InterceptorRegistry registry){
  registry.addInterceptor(new HandlerInterceptor(){
   @Override public boolean preHandle(HttpServletRequest r,HttpServletResponse s,Object handler)throws Exception{
    String path=r.getRequestURI();boolean auth=path.startsWith("/api/auth/");
    boolean ai=(path.startsWith("/api/ai/")||path.equals("/api/routes/generate")||path.equals("/api/tts")||path.equals("/api/asr")||path.matches("/api/teams/[0-9]+/questions")) && r.getMethod().equals("POST");
    if(!auth&&!ai)return true;
    long minute=System.currentTimeMillis()/60000;
    if(windows.size()>10000)windows.entrySet().removeIf(e->e.getValue().minute()<minute);
    String key=(auth?"auth:"+r.getRemoteAddr():"ai:"+CurrentUser.id());
    if(windows.size()>20000&&!windows.containsKey(key)){s.setStatus(503);return false;}
    var window=windows.compute(key,(k,v)->v==null||v.minute()!=minute?new Window(minute,new AtomicInteger()):v);
    if(window.count().incrementAndGet()>(auth?120:30)){
      s.setStatus(429);s.setHeader("Retry-After","60");s.setContentType("application/json;charset=UTF-8");
      s.getWriter().write("{\"code\":42900,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null}");return false;
    }return true;
   }
  });
 }
}
