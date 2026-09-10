package com.travelmate.map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;
import com.travelmate.common.ApiException;
class LiveLocationTest {
 @Test void researchAllowsFinalAnswerInsteadOfForcingEndlessSearch(){
  var b=RestClient.builder();var mock=MockRestServiceServer.bindTo(b).build();var s=new LiveLocationService("","test-key",b.build());
  mock.expect(requestTo("https://overpass-api.de/api/interpreter")).andRespond(withSuccess("{\"elements\":[{\"type\":\"node\",\"id\":10,\"lat\":39.9,\"lon\":116.4,\"tags\":{\"name\":\"测试地点\"}}]}",MediaType.APPLICATION_JSON));
  mock.expect(requestTo("https://api.deepseek.com/responses")).andExpect(jsonPath("$.tool_choice").value("auto"))
   .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"type\":\"web_search_call\",\"status\":\"completed\"},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"来源摘要\",\"annotations\":[{\"type\":\"url_citation\",\"url\":\"https://example.org/source\"}]}]}]}",MediaType.APPLICATION_JSON));
  var c=s.nearby(1,new LiveLocationService.Position(116.4,39.9,10,System.currentTimeMillis()));
  s.select(c.contextToken(),1,"node/10");assertFalse(s.research(c.contextToken(),1).knowledge().isBlank());mock.verify();
 }
 @Test void rejectsStaleInaccurateAndOutsideLocations(){
  long now=System.currentTimeMillis();
  for(var p:new LiveLocationService.Position[]{new LiveLocationService.Position(116,39,500,now),new LiveLocationService.Position(116,39,5,now-60000),new LiveLocationService.Position(181,-37,5,now),new LiveLocationService.Position(0,89,5,now),new LiveLocationService.Position(Double.NaN,39,5,now)})assertThrows(ApiException.class,()->LiveLocationService.validate(p));
 }
 @Test void acceptsOverseasCoordinates(){
  for(var p:new LiveLocationService.Position[]{new LiveLocationService.Position(145,-37,5,System.currentTimeMillis()),new LiveLocationService.Position(-74,40.7,5,System.currentTimeMillis())})assertDoesNotThrow(()->LiveLocationService.validate(p));
 }
 @Test void realProviderContractContextIsolationAndSelection(){
  var b=RestClient.builder();var mock=MockRestServiceServer.bindTo(b).build();var s=new LiveLocationService("","",b.build());
  mock.expect(requestTo("https://overpass-api.de/api/interpreter")).andExpect(header("User-Agent","TravelMate-Local-Prototype/1.0")).andRespond(withSuccess("{\"elements\":[{\"type\":\"node\",\"id\":10,\"lat\":39.9,\"lon\":116.4,\"tags\":{\"name:zh\":\"测试来源地点\",\"tourism\":\"museum\"}}]}",MediaType.APPLICATION_JSON));
  var c=s.nearby(1,new LiveLocationService.Position(116.4,39.9,10,System.currentTimeMillis()));
  assertEquals("node/10",c.places().get(0).id());assertThrows(ApiException.class,()->s.require(c.contextToken(),2));
  assertThrows(ApiException.class,()->s.select(c.contextToken(),1,"invented"));
  assertEquals("node/10",s.select(c.contextToken(),1,"node/10").selectedId());
  assertThrows(ApiException.class,()->s.research(c.contextToken(),1));
  s.clear(1);assertThrows(ApiException.class,()->s.require(c.contextToken(),1));mock.verify();
 }
 @Test void emptyResultsStayEmpty(){
  var b=RestClient.builder();var mock=MockRestServiceServer.bindTo(b).build();var s=new LiveLocationService("","",b.build());
  mock.expect(anything()).andRespond(withSuccess("{\"elements\":[]}",MediaType.APPLICATION_JSON));
  assertTrue(s.nearby(1,new LiveLocationService.Position(116,39,10,System.currentTimeMillis())).places().isEmpty());mock.verify();
 }
 @Test void missingAndUncitedResearchFailsClosed()throws Exception{
  var m=new ObjectMapper();for(String json:new String[]{"{}","{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"无来源历史\"}]}]}"})assertThrows(ApiException.class,()->LiveLocationService.parseResearch(m.readTree(json)));
 }
 @Test void researchRequiresSearchAndCitation()throws Exception{
  var result=LiveLocationService.parseResearch(new ObjectMapper().readTree("{\"status\":\"completed\",\"output\":[{\"type\":\"web_search_call\",\"status\":\"completed\"},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"有来源摘要\",\"annotations\":[{\"type\":\"url_citation\",\"url\":\"https://example.org/source\",\"title\":\"来源\"}]}]}]}"));assertEquals(1,result.sources().size());
 }
}
