package com.travelassistant.consultation;

import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.consultation.ai.*;
import com.travelassistant.consultation.security.OutputSafetyPolicy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConversationStreamService {
    private final ConversationService conversations; private final ConversationRepository conversationRepo;
    private final MessageRepository messages; private final ConversationStreamRepository streams;
    private final ConversationStreamEventRepository events; private final ConsultationGateway gateway;
    private final OutputSafetyPolicy outputSafety; private final ObjectMapper mapper; private final TransactionTemplate tx;
    private final Duration retention, disconnectGrace; private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler=Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String,RuntimeStream> active=new ConcurrentHashMap<>();

    public ConversationStreamService(ConversationService c,ConversationRepository cr,MessageRepository m,
      ConversationStreamRepository s,ConversationStreamEventRepository e,ConsultationGateway g,OutputSafetyPolicy output,
      ObjectMapper map,PlatformTransactionManager manager,@Value("${app.consultation.event-retention:PT10M}")Duration retention,
      @Value("${app.consultation.disconnect-grace:PT30S}")Duration grace){conversations=c;conversationRepo=cr;messages=m;streams=s;events=e;gateway=g;outputSafety=output;mapper=map;tx=new TransactionTemplate(manager);this.retention=retention;disconnectGrace=grace;}

    public SseEmitter start(String user,String conversation,String key,String content){
        ConversationService.PreparedStream preparedStream=conversations.prepareStream(user,conversation,key,content);
        if(preparedStream.replayed())return replay(user,conversation,preparedStream.streamId(),0);
        ConversationService.Prepared prepared=preparedStream.prepared();
        RuntimeStream runtime=new RuntimeStream(user,preparedStream.streamId(),prepared,new CancellationToken());active.put(preparedStream.streamId(),runtime);
        SseEmitter emitter=attach(runtime);runtime.heartbeat=scheduler.scheduleAtFixedRate(()->heartbeat(runtime),15,15,TimeUnit.SECONDS);
        executor.submit(()->produce(runtime));return emitter;
    }

    public SseEmitter replay(String user,String conversation,String streamId,int after){
        ConversationStream stream=owned(user,conversation,streamId);if(Instant.now().isAfter(stream.getExpiresAt()))throw new BusinessException("STREAM_REPLAY_EXPIRED","流事件已过期",HttpStatus.CONFLICT);if(after<0||after>stream.getLastSequence())throw new BusinessException("VALIDATION_ERROR","Last-Event-ID 无效",HttpStatus.BAD_REQUEST);
        RuntimeStream runtime=active.get(streamId);SseEmitter emitter=new SseEmitter(runtime==null?30_000L:120_000L);
        executor.submit(()->{try{if(runtime==null){for(ConversationStreamEvent event:events.findByStreamIdAndSequenceGreaterThanOrderBySequence(streamId,after))send(emitter,event);emitter.complete();}else{synchronized(runtime){for(ConversationStreamEvent event:events.findByStreamIdAndSequenceGreaterThanOrderBySequence(streamId,after))send(emitter,event);attach(runtime,emitter);}}}catch(Exception error){detach(runtime,emitter);}});return emitter;
    }

    public void cancel(String user,String conversation,String streamId){owned(user,conversation,streamId);RuntimeStream runtime=active.get(streamId);if(runtime!=null)runtime.token.cancel("CLIENT_CANCELLED");finalizeFailure(streamId,"CLIENT_CANCELLED",StreamStatus.CANCELLED);}

    private void produce(RuntimeStream runtime){StringBuilder answer=new StringBuilder(),safeBuffer=new StringBuilder();try{
        publish(runtime,StreamEventType.ACK,Map.of("streamId",runtime.id,"userMessageId",runtime.prepared.userMessageId(),"assistantMessageId",runtime.prepared.assistantMessageId(),"eventId",1));
        ConsultationResult result=gateway.stream(runtime.user,runtime.prepared.prompt(),chunk->{answer.append(chunk);safeBuffer.append(chunk);outputSafety.check(safeBuffer.toString());outputSafety.check(answer.toString());if(safeBuffer.length()>128){String safe=safeBuffer.substring(0,safeBuffer.length()-64);safeBuffer.delete(0,safeBuffer.length()-64);emitDelta(runtime,safe);}},runtime.token);
        if(runtime.token.isCancelled())throw new CancellationException(runtime.token.reason());outputSafety.check(answer.toString());if(!safeBuffer.isEmpty())emitDelta(runtime,safeBuffer.toString());conversations.completeStream(runtime.prepared.assistantMessageId(),new ConsultationResult(answer.toString(),result.model(),result.inputTokens(),result.outputTokens()));tx.executeWithoutResult(s->streams.lockById(runtime.id).ifPresent(ConversationStream::complete));publish(runtime,StreamEventType.DONE,Map.of("streamId",runtime.id,"messageId",runtime.prepared.assistantMessageId(),"status","COMPLETED","usage",Map.of("inputTokens",Objects.requireNonNullElse(result.inputTokens(),0),"outputTokens",Objects.requireNonNullElse(result.outputTokens(),0)),"replayed",false));
    }catch(Exception failure){String code=runtime.token.isCancelled()?runtime.token.reason():failure instanceof BusinessException b?b.getCode():failure instanceof com.travelassistant.consultation.security.UnsafeContentException?"CONTENT_REJECTED":failure instanceof ConsultationException c?c.getCode():"AI_UNAVAILABLE";boolean retryable=failure instanceof ConsultationException c&&c.isRetryable();StreamStatus status="CLIENT_CANCELLED".equals(code)?StreamStatus.CANCELLED:StreamStatus.FAILED;finalizeFailure(runtime.id,code,status);if(!"CLIENT_CANCELLED".equals(code))publishIfNonTerminalMissing(runtime,code,retryable);}finally{finishRuntime(runtime);}}

    private SseEmitter attach(RuntimeStream runtime){SseEmitter emitter=new SseEmitter(120_000L);attach(runtime,emitter);return emitter;}private void attach(RuntimeStream runtime,SseEmitter emitter){runtime.emitters.add(emitter);if(runtime.disconnectTask!=null)runtime.disconnectTask.cancel(false);tx.executeWithoutResult(s->streams.lockById(runtime.id).ifPresent(ConversationStream::connected));emitter.onCompletion(()->detach(runtime,emitter));emitter.onError(e->detach(runtime,emitter));emitter.onTimeout(()->detach(runtime,emitter));}
    private void detach(RuntimeStream runtime,SseEmitter emitter){if(runtime==null)return;runtime.emitters.remove(emitter);if(runtime.emitters.isEmpty()&&!runtime.token.isCancelled()){tx.executeWithoutResult(s->streams.lockById(runtime.id).ifPresent(ConversationStream::disconnected));runtime.disconnectTask=scheduler.schedule(()->{if(runtime.emitters.isEmpty())runtime.token.cancel("CLIENT_DISCONNECTED");},disconnectGrace.toMillis(),TimeUnit.MILLISECONDS);}}
    private void publish(RuntimeStream runtime,StreamEventType type,Object payload){synchronized(runtime){ConversationStreamEvent persisted=event(runtime.id,type,payload);for(SseEmitter emitter:runtime.emitters){try{send(emitter,persisted);}catch(Exception e){detach(runtime,emitter);}}}}
    private void emitDelta(RuntimeStream runtime,String content){publish(runtime,StreamEventType.DELTA,Map.of("streamId",runtime.id,"messageId",runtime.prepared.assistantMessageId(),"sequence",nextSequence(runtime.id),"content",content));}
    private void publishIfNonTerminalMissing(RuntimeStream runtime,String code,boolean retryable){try{publish(runtime,StreamEventType.ERROR,Map.of("streamId",runtime.id,"code",code,"message","流式咨询已终止","retryable",retryable,"final",true));}catch(Exception ignored){}}
    private void finalizeFailure(String streamId,String code,StreamStatus status){tx.executeWithoutResult(s->{ConversationStream stream=streams.lockById(streamId).orElse(null);if(stream==null||stream.terminal())return;stream.fail(status);messages.findById(stream.getAssistantMessage().getId()).ifPresent(m->m.fail(code));});}
    private void finishRuntime(RuntimeStream runtime){active.remove(runtime.id);if(runtime.heartbeat!=null)runtime.heartbeat.cancel(false);if(runtime.disconnectTask!=null)runtime.disconnectTask.cancel(false);runtime.emitters.forEach(SseEmitter::complete);}
    private void heartbeat(RuntimeStream runtime){runtime.emitters.forEach(e->{try{e.send(SseEmitter.event().comment("ping"));}catch(Exception x){detach(runtime,e);}});}
    private int nextSequence(String id){return tx.execute(s->streams.lockById(id).map(ConversationStream::getLastSequence).orElse(0)+1);}
    private ConversationStreamEvent event(String id,StreamEventType type,Object payload){return tx.execute(s->{ConversationStream stream=streams.lockById(id).orElseThrow();try{return events.save(new ConversationStreamEvent(stream,stream.nextSequence(),type,mapper.writeValueAsString(payload),stream.getExpiresAt()));}catch(Exception e){throw new IllegalStateException(e);}});}
    private void send(SseEmitter emitter,ConversationStreamEvent event)throws Exception{String payload=event.getPayload();var node=mapper.readTree(payload);if(node.isString())payload=node.asText();emitter.send(SseEmitter.event().id(Integer.toString(event.getSequence())).name(event.getType().name().toLowerCase()).data(payload));}
    private ConversationStream owned(String user,String conversation,String id){conversationRepo.findByIdAndUserId(conversation,user).orElseThrow(this::notFound);return streams.findByIdAndConversationId(id,conversation).orElseThrow(this::notFound);}private BusinessException notFound(){return new BusinessException("CONVERSATION_NOT_FOUND","会话或流不存在",HttpStatus.NOT_FOUND);}
    private static final class RuntimeStream{final String user,id;final ConversationService.Prepared prepared;final CancellationToken token;final CopyOnWriteArrayList<SseEmitter>emitters=new CopyOnWriteArrayList<>();volatile ScheduledFuture<?>disconnectTask,heartbeat;RuntimeStream(String u,String i,ConversationService.Prepared p,CancellationToken t){user=u;id=i;prepared=p;token=t;}}
}
