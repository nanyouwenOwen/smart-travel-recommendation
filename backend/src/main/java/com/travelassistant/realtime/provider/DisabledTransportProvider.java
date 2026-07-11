package com.travelassistant.realtime.provider;

import org.springframework.stereotype.Component;

@Component
public class DisabledTransportProvider implements TransportDataProvider {
  public boolean enabled() {
    return false;
  }
}
