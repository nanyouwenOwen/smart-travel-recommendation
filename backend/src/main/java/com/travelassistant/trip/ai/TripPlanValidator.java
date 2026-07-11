package com.travelassistant.trip.ai;
import java.math.BigDecimal; import java.util.*; import org.springframework.stereotype.Component;
@Component
public class TripPlanValidator {
 public void validate(TripPlanningRequest request,TripPlan plan){
  if(plan==null||plan.days()==null||plan.days().size()!=request.days()) invalid("天数不匹配");
  for(int index=0;index<plan.days().size();index++){
   PlannedDay day=plan.days().get(index); int expected=index+1;
   if(day.dayNumber()!=expected||!request.startDate().plusDays(index).equals(day.date())) invalid("日期或编号不连续");
   if(day.activities()==null) invalid("活动列表缺失");
   if(day.summary()!=null&&day.summary().length()>500)invalid("每日摘要过长"); java.time.LocalTime previousEnd=null;
   for(int activityIndex=0;activityIndex<day.activities().size();activityIndex++){PlannedActivity activity=day.activities().get(activityIndex);
    if(activity.sequenceNumber()!=activityIndex+1) invalid("活动顺序必须从1连续递增");
    if(activity.startTime()==null||activity.endTime()==null||!activity.endTime().isAfter(activity.startTime())) invalid("活动时间无效");
    if(previousEnd!=null&&activity.startTime().isBefore(previousEnd)) invalid("活动时间重叠");
    if(activity.estimatedCost()==null||activity.estimatedCost().compareTo(BigDecimal.ZERO)<0||activity.estimatedCost().scale()>2) invalid("活动费用无效");
    if(activity.category()==null||blank(activity.title())||blank(activity.location())||blank(activity.description())||blank(activity.transportAdvice())) invalid("活动必填字段缺失");
    if(activity.title().length()>200||activity.location().length()>300||activity.description().length()>4000||activity.transportAdvice().length()>2000)invalid("活动文本过长"); previousEnd=activity.endTime();
   }
  }
  if(plan.warnings()==null||plan.warnings().size()>20||plan.warnings().stream().anyMatch(w->blank(w)||w.length()>500))invalid("警告字段无效");
 }
 private boolean blank(String v){return v==null||v.isBlank();} private void invalid(String message){throw new TripPlanningException("AI_OUTPUT_INVALID",message,false);}
}
