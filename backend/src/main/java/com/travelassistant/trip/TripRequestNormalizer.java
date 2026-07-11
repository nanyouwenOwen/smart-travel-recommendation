package com.travelassistant.trip;
import com.travelassistant.common.exception.BusinessException; import com.travelassistant.trip.ai.TripPlanningRequest; import com.travelassistant.trip.api.CreateTripRequest; import java.math.*; import java.time.*; import java.util.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Component; import tools.jackson.databind.ObjectMapper;
@Component public class TripRequestNormalizer {
 private final Clock clock; private final ObjectMapper mapper; public TripRequestNormalizer(Clock clock,ObjectMapper mapper){this.clock=clock;this.mapper=mapper;}
 public Normalized normalize(CreateTripRequest r){
  String destination=r.destination().trim(); ZoneId zone; try{zone=ZoneId.of(r.timezone());}catch(Exception e){throw invalid("timezone","无效时区");}
  if(r.startDate().isBefore(LocalDate.now(clock.withZone(zone))))throw invalid("startDate","不能早于当地今天");
  BigDecimal amount;try{amount=new BigDecimal(r.budget().amount());}catch(Exception e){throw invalid("budget.amount","金额无效");}if(amount.compareTo(BigDecimal.ZERO)<=0||amount.scale()>2)throw invalid("budget.amount","必须大于0且最多两位小数");
  try{Currency.getInstance(r.budget().currency());}catch(Exception e){throw invalid("budget.currency","不是有效的 ISO 4217 币种");}
  LinkedHashMap<String,String> unique=new LinkedHashMap<>();for(String p:r.preferences()){String v=p.trim();if(v.isEmpty())throw invalid("preferences","偏好不能为空");unique.putIfAbsent(v.toLowerCase(Locale.ROOT),v);}
  String requirements=r.additionalRequirements()==null?null:r.additionalRequirements().trim();if(requirements!=null&&requirements.isEmpty())requirements=null;
  return new Normalized(destination,r.startDate(),r.days(),r.travelers(),amount.setScale(2),r.budget().currency(),zone.getId(),List.copyOf(unique.values()),requirements);
 }
 public String json(Normalized n){try{return mapper.writeValueAsString(n);}catch(Exception e){throw new IllegalStateException(e);}}
 public TripPlanningRequest planning(Normalized n,String instruction,com.travelassistant.trip.ai.TripPlan base){return new TripPlanningRequest(n.destination(),n.startDate(),n.days(),n.travelers(),n.budget(),n.currency(),n.timezone(),n.preferences(),n.additionalRequirements(),instruction,base);}
 private BusinessException invalid(String field,String message){return new BusinessException("VALIDATION_ERROR",field+": "+message,HttpStatus.BAD_REQUEST);}
 public record Normalized(String destination,LocalDate startDate,int days,int travelers,BigDecimal budget,String currency,String timezone,List<String> preferences,String additionalRequirements){}
}
