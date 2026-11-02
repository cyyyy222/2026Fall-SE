package com.campus.ticketing.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.io.IOException;
import java.time.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class CommonConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictJson() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper
                  .coercionConfigFor(LogicalType.Integer)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
              mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
              mapper
                  .coercionConfigFor(LogicalType.Boolean)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
              SimpleModule module = new SimpleModule();
              module.addDeserializer(
                  java.util.UUID.class,
                  new JsonDeserializer<java.util.UUID>() {
                    @Override
                    public java.util.UUID deserialize(
                        com.fasterxml.jackson.core.JsonParser p, DeserializationContext c)
                        throws IOException {
                      String value = p.getValueAsString();
                      if (value == null
                          || !value.matches(
                              "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                        throw com.fasterxml.jackson.databind.exc.InvalidFormatException.from(
                            p, "UUID format invalid", value, java.util.UUID.class);
                      return java.util.UUID.fromString(value);
                    }
                  });
              module.addSerializer(
                  OffsetDateTime.class,
                  new JsonSerializer<OffsetDateTime>() {
                    @Override
                    public void serialize(OffsetDateTime t, JsonGenerator g, SerializerProvider p)
                        throws IOException {
                      g.writeString(t.withOffsetSameInstant(ZoneOffset.ofHours(8)).toString());
                    }
                  });
              mapper.registerModule(module);
            });
  }
}
