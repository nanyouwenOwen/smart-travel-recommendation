package com.travelassistant.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(XiaomiMimoProperties.class)
public class XiaomiMimoConfiguration {}
