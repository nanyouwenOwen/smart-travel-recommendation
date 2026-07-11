package com.travelassistant.trip.ai;
import com.travelassistant.trip.BudgetCategory; import java.math.BigDecimal; import java.time.LocalTime; import java.util.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="app.trip-planning.provider",havingValue="stub",matchIfMissing=true)
public class StubTripPlanningProvider implements TripPlanningProvider {
 public TripPlan generate(TripPlanningRequest r){
  if("__timeout__".equals(r.destination())) throw new TripPlanningException("AI_TIMEOUT","模拟超时",true);
  List<PlannedDay> days=new ArrayList<>();
  for(int i=1;i<=r.days();i++){
   BigDecimal cost=r.budget().divide(BigDecimal.valueOf(Math.max(r.days(),1)),2,java.math.RoundingMode.DOWN).min(new BigDecimal("100.00"));
   PlannedActivity activity=new PlannedActivity(1,LocalTime.of(9,0),LocalTime.of(11,0),r.destination()+"城市漫步",r.destination(),"AI 生成的建议行程",cost,BudgetCategory.ATTRACTION,"建议使用公共交通并核对实时班次");
   days.add(new PlannedDay(i,r.startDate().plusDays(i-1),"第 "+i+" 天",List.of(activity)));
  }
  return new TripPlan(List.copyOf(days),List.of("AI_GENERATED_ADVICE"));
 }
 public String providerName(){return "stub";} public String modelName(){return "stub-v1";}
}
