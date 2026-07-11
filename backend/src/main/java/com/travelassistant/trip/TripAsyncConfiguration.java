package com.travelassistant.trip;
import java.util.concurrent.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.*; import org.springframework.scheduling.annotation.*; import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration(proxyBeanMethods=false) @EnableScheduling
public class TripAsyncConfiguration {
 @Bean("tripPlanningExecutor") ThreadPoolTaskExecutor tripPlanningExecutor(@Value("${app.trip-planning.executor.core-size:2}")int core,@Value("${app.trip-planning.executor.max-size:4}")int max,@Value("${app.trip-planning.executor.queue-capacity:50}")int capacity){ThreadPoolTaskExecutor e=new ThreadPoolTaskExecutor();e.setCorePoolSize(core);e.setMaxPoolSize(max);e.setQueueCapacity(capacity);e.setThreadNamePrefix("trip-planner-");e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());e.initialize();return e;}
}
