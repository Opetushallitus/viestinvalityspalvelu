package fi.vm.sade.viestinvalitys.config;

import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import fi.vm.sade.viestinvalitys.RequestIdFilter;
import fi.vm.sade.viestinvalitys.lahetys.service.LahetysSendService;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DbSchedulerConfiguration {

  @Bean
  @ConditionalOnProperty(name = "viestinvalitys.lahetys.enabled", havingValue = "true")
  public Task<Void> lahetysTask(LahetysSendService lahetysSendService) {
    return recurring(
            "laheta-task", Schedules.fixedDelay(Duration.ofSeconds(2)), lahetysSendService::laheta);
  }

  private Task<Void> recurring(String name, Schedule schedule, Runnable action) {
    return Tasks.recurring(name, schedule)
            .execute(
                    (inst, ctx) -> {
                      try {
                        MDC.put(RequestIdFilter.REQUEST_ID_ATTRIBUTE, UUID.randomUUID().toString());
                        action.run();
                      } finally {
                        MDC.remove(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
                      }
                    });
  }
}
