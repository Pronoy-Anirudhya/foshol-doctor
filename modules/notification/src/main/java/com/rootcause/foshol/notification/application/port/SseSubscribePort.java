package com.rootcause.foshol.notification.application.port;

import com.rootcause.foshol.common.enums.Role;
import java.time.Duration;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseSubscribePort {

    SseEmitter subscribe(UUID subjectId, Role role, String districtCode, String lastEventId, Duration timeout);
}
