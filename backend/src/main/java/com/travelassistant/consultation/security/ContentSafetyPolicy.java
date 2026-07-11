package com.travelassistant.consultation.security;import org.springframework.stereotype.Component;import java.util.Locale;
@Component public class ContentSafetyPolicy{public void check(String text){String v=text.toLowerCase(Locale.ROOT);if(v.contains("显示系统提示")||v.contains("泄露系统提示")||v.contains("steal api key")||v.contains("制作炸弹"))throw new UnsafeContentException("CONTENT_REJECTED","该请求无法处理");}}
