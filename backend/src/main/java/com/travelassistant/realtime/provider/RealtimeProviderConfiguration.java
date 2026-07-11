package com.travelassistant.realtime.provider;

import com.travelassistant.realtime.RealtimeProperties;
import com.travelassistant.realtime.api.RealtimeDtos;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RealtimeProviderConfiguration {
 private static long lastNominatimCall;
 private static synchronized void throttleNominatim(){long wait=1000-(System.currentTimeMillis()-lastNominatimCall);if(wait>0)try{Thread.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ProviderException("LOCATION_DATA_UNAVAILABLE");}lastNominatimCall=System.currentTimeMillis();}
 @Bean LocationDataProvider locationDataProvider(RealtimeProperties p,HttpClient http,ObjectMapper json){
  if("stub".equalsIgnoreCase(p.mode())) return (q,l,n)->List.of(new LocationDataProvider.Candidate("OPENSTREETMAP_NOMINATIM","stub-"+q.toLowerCase(Locale.ROOT),q.trim(),q.trim()+", 示例地区","CN",new BigDecimal("31.2304000"),new BigDecimal("121.4737000"),"Asia/Shanghai","city"));
  if("disabled".equalsIgnoreCase(p.mode())) return (q,l,n)->{throw new ProviderException("LOCATION_DATA_UNAVAILABLE");};
  return (q,l,n)->{try{throttleNominatim();String url=p.nominatimBaseUrl()+"/search?format=jsonv2&addressdetails=1&q="+enc(q)+"&accept-language="+enc(l)+"&limit="+n;
    JsonNode root=get(http,json,p,URI.create(url));List<LocationDataProvider.Candidate> out=new ArrayList<>();for(JsonNode x:root){JsonNode a=x.path("address");String name=text(x,"name",text(x,"display_name",q));BigDecimal lon=decimal(x,"lon");out.add(new LocationDataProvider.Candidate("OPENSTREETMAP_NOMINATIM",text(x,"osm_type","")+text(x,"osm_id",UUID.randomUUID().toString()),name,text(x,"display_name",name),text(a,"country_code","").toUpperCase(Locale.ROOT),decimal(x,"lat"),lon,timezone(a,lon),text(x,"type","place")));}return out;
   }catch(Exception e){throw provider("LOCATION_DATA_UNAVAILABLE",e);}};
 }
 @Bean WeatherDataProvider weatherDataProvider(RealtimeProperties p,HttpClient http,ObjectMapper json){
  if("stub".equalsIgnoreCase(p.mode())) return (lat,lon,tz,start,end)->{List<RealtimeDtos.WeatherDay> days=new ArrayList<>();LocalDate last=LocalDate.now().plusDays(16);for(LocalDate d=start;!d.isAfter(end)&&!d.isAfter(last);d=d.plusDays(1))days.add(new RealtimeDtos.WeatherDay(d.toString(),1,"演示：晴间多云",new BigDecimal("25.0"),new BigDecimal("17.0"),20,new BigDecimal("0.0"),new BigDecimal("15.0"),d+"T06:00",d+"T18:00"));return new WeatherDataProvider.Result(days,null);};
  if("disabled".equalsIgnoreCase(p.mode())) return (a,b,c,d,e)->{throw new ProviderException("WEATHER_DATA_UNAVAILABLE");};
  return (lat,lon,tz,start,end)->{try{String url=p.weatherBaseUrl()+"/v1/forecast?latitude="+lat+"&longitude="+lon+"&timezone="+enc(tz)+"&start_date="+start+"&end_date="+end+"&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,sunrise,sunset";JsonNode r=get(http,json,p,URI.create(url)),d=r.path("daily");List<RealtimeDtos.WeatherDay> out=new ArrayList<>();for(int i=0;i<d.path("time").size();i++){int code=d.path("weather_code").get(i).asInt();out.add(new RealtimeDtos.WeatherDay(d.path("time").get(i).asText(),code,condition(code),bd(d,"temperature_2m_max",i),bd(d,"temperature_2m_min",i),integer(d,"precipitation_probability_max",i),bd(d,"precipitation_sum",i),bd(d,"wind_speed_10m_max",i),str(d,"sunrise",i),str(d,"sunset",i)));}return new WeatherDataProvider.Result(out,null);}catch(Exception e){throw provider("WEATHER_DATA_UNAVAILABLE",e);}};
 }
 @Bean PlaceDataProvider placeDataProvider(RealtimeProperties p,HttpClient http,ObjectMapper json){
  if("stub".equalsIgnoreCase(p.mode())) return (lat,lon,radius,limit)->new PlaceDataProvider.Result(List.of(new RealtimeDtos.Place("node/1","示例博物馆","ATTRACTION",lat,lon,"Tu-Su 09:00-17:00","https://example.com","https://www.openstreetmap.org/node/1",null)),null);
  if("disabled".equalsIgnoreCase(p.mode())) return (a,b,c,d)->{throw new ProviderException("PLACE_DATA_UNAVAILABLE");};
  return (lat,lon,radius,limit)->{try{String q="[out:json][timeout:10];nwr(around:"+radius+","+lat+","+lon+")[tourism~\"attraction|museum|gallery|zoo|theme_park\"];out center tags qt "+limit+";";URI uri=URI.create(p.overpassBaseUrl()+"/api/interpreter?data="+enc(q));JsonNode root=get(http,json,p,uri);List<RealtimeDtos.Place> out=new ArrayList<>();for(JsonNode x:root.path("elements")){JsonNode tags=x.path("tags");String site=safeUrl(text(tags,"website",null));BigDecimal la=x.has("lat")?x.path("lat").decimalValue():x.path("center").path("lat").decimalValue();BigDecimal lo=x.has("lon")?x.path("lon").decimalValue():x.path("center").path("lon").decimalValue();String type=text(x,"type","node"),id=x.path("id").asText();out.add(new RealtimeDtos.Place(type+"/"+id,text(tags,"name","未命名景点"),"ATTRACTION",la,lo,text(tags,"opening_hours",null),site,"https://www.openstreetmap.org/"+type+"/"+id,text(x,"timestamp",null)));}return new PlaceDataProvider.Result(out,null);}catch(Exception e){throw provider("PLACE_DATA_UNAVAILABLE",e);}};
 }
 private static JsonNode get(HttpClient h,ObjectMapper j,RealtimeProperties p,URI uri)throws Exception{if(!Set.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException("scheme");HttpRequest req=HttpRequest.newBuilder(uri).timeout(p.requestTimeout()).header("User-Agent",p.userAgent()).GET().build();HttpResponse<String> res=h.send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()!=200||res.body().length()>2_000_000)throw new IllegalStateException("provider status");return j.readTree(res.body());}
 private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);} private static String text(JsonNode n,String k,String d){JsonNode v=n.path(k);return v.isMissingNode()||v.isNull()?d:v.asText();} private static BigDecimal decimal(JsonNode n,String k){return new BigDecimal(n.path(k).asText());} private static BigDecimal bd(JsonNode n,String k,int i){JsonNode v=n.path(k).get(i);return v==null||v.isNull()?null:v.decimalValue();} private static Integer integer(JsonNode n,String k,int i){JsonNode v=n.path(k).get(i);return v==null||v.isNull()?null:v.asInt();} private static String str(JsonNode n,String k,int i){JsonNode v=n.path(k).get(i);return v==null||v.isNull()?null:v.asText();}
 private static String timezone(JsonNode a,BigDecimal longitude){String c=text(a,"country_code","");return switch(c){case"cn"->"Asia/Shanghai";case"jp"->"Asia/Tokyo";case"kr"->"Asia/Seoul";case"th"->"Asia/Bangkok";case"gb"->"Europe/London";case"fr"->"Europe/Paris";case"de"->"Europe/Berlin";case"au"->"Australia/Sydney";default->{int offset=Math.max(-12,Math.min(14,(int)Math.round(longitude.doubleValue()/15)));yield offset==0?"UTC":"Etc/GMT"+(offset>0?"-":"+")+Math.abs(offset);}};} private static String safeUrl(String s){try{if(s==null)return null;URI u=URI.create(s);return Set.of("http","https").contains(u.getScheme())?s:null;}catch(Exception e){return null;}} private static String condition(int c){return c==0?"晴":c<=3?"多云":c<=67?"降水":c<=77?"降雪":c>=95?"雷暴":"天气变化";} private static ProviderException provider(String code,Exception e){return e instanceof ProviderException p?p:new ProviderException(code);} public static class ProviderException extends RuntimeException{private final String code;public ProviderException(String c){super(c);code=c;}public String code(){return code;}}
}
