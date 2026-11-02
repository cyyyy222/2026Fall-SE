package com.campus.ticketing.config;

import com.campus.ticketing.interceptor.ApiContractInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Override
  public void addArgumentResolvers(
      java.util.List<org.springframework.web.method.support.HandlerMethodArgumentResolver>
          resolvers) {
    resolvers.add(new com.campus.ticketing.interceptor.RequestContextResolver());
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new ApiContractInterceptor()).addPathPatterns("/api/**");
  }

  @Override
  public void addFormatters(org.springframework.format.FormatterRegistry r) {
    r.addConverter(
        String.class,
        java.util.UUID.class,
        s -> {
          if (!s.matches(
              "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Invalid UUID");
          return java.util.UUID.fromString(s);
        });
    r.addConverter(
        String.class,
        Boolean.class,
        s -> {
          if ("true".equals(s)) return true;
          if ("false".equals(s)) return false;
          throw new IllegalArgumentException("Boolean query must be true/false");
        });
  }
}
