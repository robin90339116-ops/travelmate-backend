package com.travelmate;
import com.fasterxml.jackson.databind.*;
import com.travelmate.config.RedisCacheConfig;
import com.travelmate.catalog.CatalogDtos.CityView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest(properties={"app.ai.api-key=", "app.map.amap-web-key=", "spring.datasource.url=jdbc:h2:mem:integration;MODE=MySQL"}) @AutoConfigureMockMvc
class BackendIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper mapper;
 JsonNode login() throws Exception {
  String body=mapper.writeValueAsString(java.util.Map.of("phone","13"+String.format("%09d",java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000)),"code","246810"));
  return mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
 }
 String bearer(JsonNode login){return "Bearer "+login.get("accessToken").asText();}
 String refreshBody(JsonNode login){return "{\"refreshToken\":\""+login.get("refreshToken").asText()+"\"}";}
 @Test void refreshReplayAndLogout() throws Exception {
  var user=login();
  mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(user))).andExpect(status().isOk());
  mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(user))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/logout-all").header("Authorization",bearer(user))).andExpect(status().isOk());
  mvc.perform(get("/api/favorites").header("Authorization",bearer(user))).andExpect(status().isUnauthorized());
 }
 @Test void concurrentRefreshOnlyOneSucceeds() throws Exception {
  var user=login();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  Callable<Integer> request=()->{start.await();return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(user))).andReturn().getResponse().getStatus();};
  try{var a=pool.submit(request);var b=pool.submit(request);start.countDown();
   var results=java.util.List.of(a.get(),b.get());assertTrue(results.contains(200));assertTrue(results.contains(401));
  }finally{pool.shutdownNow();}
 }
 @Test void revokeSingleDeviceAndLogoutWithoutBody() throws Exception {
  var a=login();
  mvc.perform(delete("/api/auth/sessions/"+a.get("sessionId").asText()).header("Authorization",bearer(a))).andExpect(status().isOk());
  mvc.perform(get("/api/favorites").header("Authorization",bearer(a))).andExpect(status().isUnauthorized());
  var b=login();mvc.perform(post("/api/auth/logout").header("Authorization",bearer(b))).andExpect(status().isOk());
  mvc.perform(get("/api/favorites").header("Authorization",bearer(b))).andExpect(status().isUnauthorized());
 }
 @Test void favoriteValidationAndIsolation() throws Exception {
  var a=login();var b=login();
  mvc.perform(post("/api/favorites").header("Authorization",bearer(a)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"targetType\":\"\"}")).andExpect(status().isBadRequest());
  var result=mvc.perform(post("/api/favorites").header("Authorization",bearer(a)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"test\",\"targetType\":\"point\"}")).andExpect(status().isOk()).andReturn();
  long id=mapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
  mvc.perform(delete("/api/favorites/"+id).header("Authorization",bearer(b))).andExpect(status().isNotFound());
 }
 @Test void favoriteTargetIsIdempotentAndValidated()throws Exception{
  var user=login();String body="{\"title\":\"景点\",\"targetType\":\"spot\",\"targetId\":\"1\"}";
  var a=mvc.perform(post("/api/favorites").header("Authorization",bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
  var b=mvc.perform(post("/api/favorites").header("Authorization",bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
  assertEquals(mapper.readTree(a.getResponse().getContentAsString()).at("/data/id"),mapper.readTree(b.getResponse().getContentAsString()).at("/data/id"));
  mvc.perform(post("/api/favorites").header("Authorization",bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"1\"","\"999999\""))).andExpect(status().isNotFound());
 }
 @Test void privateRoomAndOwnerExit() throws Exception {
  var a=login();var b=login();
  var result=mvc.perform(post("/api/teams").header("Authorization",bearer(a)).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
  var team=mapper.readTree(result.getResponse().getContentAsString()).get("data");String id=team.get("teamId").asText();
  mvc.perform(get("/api/teams/"+id).header("Authorization",bearer(b))).andExpect(status().isForbidden());
  mvc.perform(post("/api/teams/join").header("Authorization",bearer(b)).contentType(MediaType.APPLICATION_JSON).content("{\"teamCode\":\""+team.get("teamCode").asText()+"\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/teams/"+id+"/leave").header("Authorization",bearer(a))).andExpect(status().isOk());
  mvc.perform(get("/api/teams/"+id).header("Authorization",bearer(b))).andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerId").value(b.at("/user/id").asLong()));
 }
 @Test void failedJobIsPersistentAndPrivate() throws Exception {
  var a=login();var b=login();
  var result=mvc.perform(post("/api/ai/explanations/async").header("Authorization",bearer(a)).contentType(MediaType.APPLICATION_JSON).content("{\"spotId\":\"1\"}")).andExpect(status().isOk()).andReturn();
  String id=mapper.readTree(result.getResponse().getContentAsString()).at("/data/jobId").asText();
  mvc.perform(get("/api/ai/jobs/"+id).header("Authorization",bearer(b))).andExpect(status().isNotFound());
  String state="";
  for(int i=0;i<50;i++){
   var response=mvc.perform(get("/api/ai/jobs/"+id).header("Authorization",bearer(a))).andReturn();
   state=mapper.readTree(response.getResponse().getContentAsString()).at("/data/status").asText();
   if("failed".equals(state))break;Thread.sleep(20);
  }assertEquals("failed",state);
 }
 @Test void redisDtoRoundTrip() {
  var factory=org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
  var manager=new RedisCacheConfig().cacheManager(factory,mapper);
  manager.afterPropertiesSet();
  var config=manager.getCache("route");
  var pair=((org.springframework.data.redis.cache.RedisCache)config).getCacheConfiguration().getValueSerializationPair();
  Object value=pair.read(pair.write(new CityView("test","test","test")));
  assertInstanceOf(CityView.class,value);
 }

 @Test void passwordAccountAndDataDeletion() throws Exception {
  String phone="14"+String.format("%09d",java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000));
  String body=mapper.writeValueAsString(java.util.Map.of("phone",phone,"password","audit-long-password"));
  var response=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
  var user=mapper.readTree(response.getResponse().getContentAsString()).get("data");
  mvc.perform(post("/api/auth/password/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  mvc.perform(get("/api/data/export").header("Authorization",bearer(user))).andExpect(status().isOk()).andExpect(jsonPath("$.data.user.phone").value(phone));
  mvc.perform(delete("/api/data/account").header("Authorization",bearer(user))).andExpect(status().isOk());
  mvc.perform(get("/api/favorites").header("Authorization",bearer(user))).andExpect(status().isUnauthorized());
 }
 @Test void rejectsBadCoordinatesAndMissingJob() throws Exception {
  mvc.perform(post("/api/map/route-validate").contentType(MediaType.APPLICATION_JSON).content("{\"origin\":\"NaN,0\",\"destination\":\"1,2\"}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/ai/jobs/missing").header("Authorization",bearer(login()))).andExpect(status().isNotFound());
 }
 @Test void teamQuestionsRequireMembership() throws Exception {
  var a=login();var outsider=login();
  var created=mvc.perform(post("/api/teams").header("Authorization",bearer(a)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andReturn();
  String id=mapper.readTree(created.getResponse().getContentAsString()).at("/data/teamId").asText();
  String path="/api/teams/"+id+"/questions";
  mvc.perform(post(path).header("Authorization",bearer(a)).contentType(MediaType.APPLICATION_JSON).content("{\"spotId\":\"1\",\"question\":\"这里值得观察什么？\"}")).andExpect(status().isOk());
  mvc.perform(get(path).header("Authorization",bearer(a))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].question").value("这里值得观察什么？"));
  mvc.perform(get(path).header("Authorization",bearer(outsider))).andExpect(status().isForbidden());
 }
}
