package com.campus.ticketing;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.enums.Role;
import com.campus.ticketing.security.SessionPrincipal;
import com.campus.ticketing.validation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Contract/security tests only; no H2 or mock MySQL substituted for integration verification. */
@SpringBootTest
@AutoConfigureMockMvc
class FrameworkContractTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired Validator validator;
  @Autowired ApplicationContext context;
  @Autowired RequestMappingHandlerMapping mappings;

  private UsernamePasswordAuthenticationToken actor(Role role, boolean force) {
    return new UsernamePasswordAuthenticationToken(
        new SessionPrincipal(UUID.randomUUID(), role, force, Instant.now().plusSeconds(1000)),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf()
      throws Exception {
    var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    String token =
        json.readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("token")
            .asText();
    var cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return request -> {
      request.setCookies(cookie);
      request.addHeader("X-CSRF-TOKEN", token);
      return request;
    };
  }

  @Test
  void reviewStartupNeverCreatesDatabaseClients() {
    assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
    assertThat(
            context.getBeansOfType(
                org.springframework.data.redis.connection.RedisConnectionFactory.class))
        .isEmpty();
    assertThat(context.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
  }

  @Test
  void allCanonicalBusinessRoutesAreRegisteredExactlyOnce() throws Exception {
    var text = Files.readString(Path.of("../../docs/api.md"));
    var expected = new HashSet<String>();
    var matcher =
        Pattern.compile("(?m)^\\| ([A-Z]+-\\d+) \\| (GET|POST|PUT|PATCH|DELETE) `([^`]+)` \\|")
            .matcher(text);
    while (matcher.find())
      expected.add(matcher.group(1) + " " + matcher.group(2) + " /api" + matcher.group(3));
    var actual = new HashSet<String>();
    var ids = new HashSet<String>();
    mappings
        .getHandlerMethods()
        .forEach(
            (route, handler) -> {
              var contract = handler.getMethodAnnotation(ApiContract.class);
              if (contract == null || contract.id().equals("SYS-01")) return;
              assertThat(ids.add(contract.id())).as("duplicate interface id").isTrue();
              route
                  .getPatternValues()
                  .forEach(
                      path ->
                          route
                              .getMethodsCondition()
                              .getMethods()
                              .forEach(
                                  method -> actual.add(contract.id() + " " + method + " " + path)));
            });
    assertThat(expected).hasSize(99);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void csrfIsRealAndAnonymousMutationWithoutTokenIsRejected() throws Exception {
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
        .andExpect(jsonPath("$.data.token").isNotEmpty())
        .andExpect(cookie().httpOnly("XSRF-TOKEN", true));
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"Test1234\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }

  @Test
  void loginFailsClosedWithoutDatabaseAfterCsrfAndSyntaxValidation() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"Test1234\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
  }

  @Test
  void privateRoleAndForcedPasswordGuardsRunBeforeBusiness() throws Exception {
    mvc.perform(get("/api/me/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    mvc.perform(get("/api/me/orders").with(authentication(actor(Role.ORGANIZER, false))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/me/orders").with(authentication(actor(Role.PARTICIPANT, true))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    mvc.perform(get("/api/me/orders").with(authentication(actor(Role.PARTICIPANT, false))))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void queryTyposBooleanShortcutsAndRepeatedParamsAreRejected() throws Exception {
    mvc.perform(get("/api/public/sessions").param("pageSzie", "10"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get("/api/public/sessions").param("page", "0"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get("/api/public/sessions").param("page", "1", "2"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            get("/api/me/messages")
                .param("unreadOnly", "yes")
                .with(authentication(actor(Role.PARTICIPANT, false))))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void writeUnknownFieldsMissingKeyAndClientPriceCannotSlipThrough() throws Exception {
    mvc.perform(
            patch("/api/me/profile")
                .with(authentication(actor(Role.PARTICIPANT, false)))
                .with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"张三\"}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post("/api/me/registrations")
                .with(authentication(actor(Role.PARTICIPANT, false)))
                .with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"requestId\":\""
                        + UUID.randomUUID()
                        + "\",\"sessionId\":\""
                        + UUID.randomUUID()
                        + "\",\"ticketTypeId\":\""
                        + UUID.randomUUID()
                        + "\",\"attendeeName\":\"张三\",\"priceJiao\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void nullableMandatoryAndPatchThreeStatesDiffer() throws Exception {
    var absent = json.readValue("{\"expectedVersion\":0}", Requests.PublishedEdit.class);
    var clear =
        json.readValue(
            "{\"expectedVersion\":0,\"description\":null}", Requests.PublishedEdit.class);
    var value =
        json.readValue(
            "{\"expectedVersion\":0,\"description\":\"简介\"}", Requests.PublishedEdit.class);
    assertThat(absent.description().present()).isFalse();
    assertThat(clear.description().present()).isTrue();
    assertThat(clear.description().value()).isNull();
    assertThat(value.description().value()).isEqualTo("简介");
    var invalid =
        json.readValue("{\"expectedVersion\":0,\"sessions\":null}", Requests.PublishedEdit.class);
    assertThat(validator.validate(invalid)).isNotEmpty();
    assertThatThrownBy(
            () -> json.readValue("{\"expectedVersion\":0,\"sessions\":[]}", Requests.Draft.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    assertThat(json.readValue("{}", Requests.Export.class).sessionId()).isNull();
    assertThatThrownBy(() -> json.readValue("{\"sessionId\":null}", Requests.Export.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
  }

  @Test
  void unicodeLengthAndTimeGranularityMatchContract() throws Exception {
    assertThat(validator.validate(new Requests.Profile("😀".repeat(60)))).isEmpty();
    assertThat(validator.validate(new Requests.Profile("😀".repeat(61)))).isNotEmpty();
    var draft =
        new Requests.SessionDraft(
            UUID.randomUUID(),
            null,
            OffsetDateTime.parse("2026-11-05T18:01:00+08:00"),
            null,
            null,
            null,
            null,
            null,
            List.of());
    assertThat(validator.validate(draft)).isEmpty();
    var seconds =
        new Requests.SessionDraft(
            UUID.randomUUID(),
            null,
            OffsetDateTime.parse("2026-11-05T18:01:30+08:00"),
            null,
            null,
            null,
            null,
            null,
            List.of());
    assertThat(validator.validate(seconds)).isNotEmpty();
    var zone =
        new Requests.SessionDraft(
            UUID.randomUUID(),
            null,
            OffsetDateTime.parse("2026-11-05T18:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            List.of());
    assertThat(validator.validate(zone)).isEmpty();
    var valid =
        json.readValue(
            "{\"id\":\""
                + UUID.randomUUID()
                + "\",\"name\":null,\"startsAt\":\"2026-11-05T18:00:00+08:00\",\"endsAt\":null,\"location\":null,\"capacity\":null,\"salesOpensAt\":null,\"registrationClosesAt\":null,\"ticketTypes\":[]}",
            Requests.SessionDraft.class);
    assertThat(validator.validate(valid)).isEmpty();
    assertThat(valid.startsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
  }

  @Test
  void jsonDoesNotCoerceFloatsOrStringBooleans() throws Exception {
    assertThatThrownBy(() -> json.readValue("{\"expectedVersion\":1.2}", Requests.Version.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    assertThatThrownBy(
            () ->
                json.readValue(
                    "{\"expectedVersion\":1,\"enabled\":\"false\"}", Requests.UpdateAccount.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
  }

  @Test
  void registrationAcceptsMissingOrCommonSuffixEmailsAndExplainsInvalidFields() throws Exception {
    String base =
        "{\"username\":\"student\",\"password\":\"Password123\",\"phone\":\"13990000008\",\"role\":\"PARTICIPANT\",\"verificationId\":\""
            + UUID.randomUUID()
            + "\",\"verificationCode\":\"123456\"";
    for (String extra :
        List.of(
            "",
            ",\"email\":null",
            ",\"email\":\"\"",
            ",\"email\":\"123456@qq.com\"",
            ",\"email\":\"name@163.com\"",
            ",\"email\":\"name@nju.edu.cn\"")) {
      var request = json.readValue(base + extra + "}", Requests.Register.class);
      assertThat(validator.validate(request)).isEmpty();
    }
    var invalid = json.readValue(base + ",\"email\":\"not-an-email\"}", Requests.Register.class);
    assertThat(validator.validate(invalid))
        .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    mvc.perform(
            post("/api/auth/register")
                .with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(base.replace("Password123", "12345678") + "}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  void healthReportsDisconnectedDependenciesTruthfully() throws Exception {
    mvc.perform(get("/api/system/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.data.status").value("NOT_READY"))
        .andExpect(jsonPath("$.data.components.mysql").value("NOT_CONNECTED"))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void redisKeysKeepEnvironmentAndDoNotAcceptRawIdentifiers() {
    var keys = new com.campus.ticketing.dao.redis.RedisKeyFactory("test");
    assertThat(keys.authRate("login", "account", "a".repeat(64)))
        .isEqualTo("ticketing:test:rate:login:account:" + "a".repeat(64));
    assertThatThrownBy(() -> keys.authRate("login", "account", "13800000000"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoredDatabaseAuthenticationKeepsCsrfValidAcrossConsecutivePosts() throws Exception {
    var chain = context.getBean(org.springframework.security.web.SecurityFilterChain.class);
    assertThat(chain.getFilters())
        .noneMatch(
            f -> f instanceof org.springframework.security.web.session.SessionManagementFilter);
    var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    var cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
    String token =
        json.readTree(csrf.getResponse().getContentAsString()).path("data").path("token").asText();
    var principal = actor(Role.CHECKER, false);
    for (int i = 0; i < 2; i++) {
      var response =
          mvc.perform(
                  post("/api/checker/ticket-previews")
                      .with(authentication(principal))
                      .cookie(cookie)
                      .header("X-CSRF-TOKEN", token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"ticketId\":\"" + UUID.randomUUID() + "\"}"))
              .andExpect(status().isServiceUnavailable())
              .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
              .andReturn();
      var changed = response.getResponse().getCookie("XSRF-TOKEN");
      if (changed != null) assertThat(changed.getValue()).isEqualTo(token);
    }
  }
}
