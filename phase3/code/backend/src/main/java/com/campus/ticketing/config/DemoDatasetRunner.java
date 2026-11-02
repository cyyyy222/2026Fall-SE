package com.campus.ticketing.config;

import com.campus.ticketing.service.core.DemoDatasetService;
import java.util.Map;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed.enabled", havingValue = "true")
public class DemoDatasetRunner implements ApplicationRunner {
  private final DemoDatasetService data;
  private final NamedParameterJdbcTemplate jdbc;
  private final Environment env;
  private final ConfigurableApplicationContext app;

  public DemoDatasetRunner(
      DemoDatasetService data,
      NamedParameterJdbcTemplate jdbc,
      Environment env,
      ConfigurableApplicationContext app) {
    this.data = data;
    this.jdbc = jdbc;
    this.env = env;
    this.app = app;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!env.getProperty("app.environment", "local").equals("local")
        || !"campus_ticketing_dev"
            .equals(jdbc.queryForObject("SELECT DATABASE()", Map.of(), String.class)))
      throw new IllegalStateException("Demo import is limited to local campus_ticketing_dev");
    var summary = data.seed();
    System.out.println(
        "DEMO_DATASET "
            + (summary.inserted() ? "IMPORTED" : "ALREADY_PRESENT")
            + " users="
            + summary.users()
            + " events="
            + summary.events()
            + " orders="
            + summary.orders()
            + " categories="
            + summary.categories());
    if (env.getProperty("app.demo.seed.exit-after", Boolean.class, false))
      System.exit(SpringApplication.exit(app, () -> 0));
  }
}
