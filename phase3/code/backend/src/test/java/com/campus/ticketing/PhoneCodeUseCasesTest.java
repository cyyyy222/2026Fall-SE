package com.campus.ticketing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dao.redis.RedisTransientDao;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.enums.RegisterRole;
import com.campus.ticketing.service.core.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

class PhoneCodeUseCasesTest {
  BusinessSupport b;
  RedisTransientDao dao;
  RateLimitService rate;
  ObjectProvider<RedisTransientDao> provider;
  MockEnvironment env;
  PhoneCodeUseCases codes;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    b = mock(BusinessSupport.class);
    dao = mock(RedisTransientDao.class);
    rate = mock(RateLimitService.class);
    provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(dao);
    env =
        new MockEnvironment()
            .withProperty("app.environment", "local")
            .withProperty("app.rate.hmac-secret", "local-test-only-secret-at-least-32-characters");
    codes = new PhoneCodeUseCases(b, provider, rate, env);
    when(dao.issuePhoneCode(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
  }

  @Test
  void issuanceUsesOpaqueKeysAndHashesAndNeverReturnsTheCode() {
    var result =
        codes.issue(
            new RequestContext(null, null, "test"),
            new Requests.PhoneCode(
                "13911112222", RegisterRole.PARTICIPANT, Requests.PhoneCodePurpose.REGISTER));
    assertThat(result.data().verificationId()).isNotNull();
    assertThat(result.data().resendAfterSeconds()).isEqualTo(60);
    assertThat(result.data().delivery()).isEqualTo("LOCAL_CONSOLE");
    var key = ArgumentCaptor.forClass(String.class);
    var hash = ArgumentCaptor.forClass(String.class);
    verify(dao).issuePhoneCode(key.capture(), anyString(), anyString(), hash.capture());
    assertThat(key.getValue())
        .startsWith("ticketing:local:otp:register:")
        .doesNotContain("13911112222");
    assertThat(hash.getValue()).matches("[0-9a-f]{64}");
  }

  @Test
  void cooldownRejectsRatherThanReplacingTheActiveCode() {
    when(dao.issuePhoneCode(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
    assertThatThrownBy(
            () ->
                codes.issue(
                    new RequestContext(null, null, "test"),
                    new Requests.PhoneCode(
                        "13911112222", RegisterRole.PARTICIPANT, Requests.PhoneCodePurpose.LOGIN)))
        .isInstanceOf(RateLimitService.Limited.class);
  }

  @Test
  void consoleDeliveryIsDisabledOutsideLocalAndRedisFailureDoesNotBypassVerification() {
    env.withProperty("app.environment", "prod");
    var production = new PhoneCodeUseCases(b, provider, rate, env);
    assertThatThrownBy(
            () ->
                production.issue(
                    new RequestContext(null, null, "test"),
                    new Requests.PhoneCode(
                        "13911112222", RegisterRole.PARTICIPANT, Requests.PhoneCodePurpose.LOGIN)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(503));
    when(dao.verifyPhoneCode(anyString(), anyString(), anyString(), anyBoolean()))
        .thenThrow(new IllegalStateException());
    assertThatThrownBy(
            () ->
                codes.verify(
                    "13911112222",
                    RegisterRole.PARTICIPANT,
                    Requests.PhoneCodePurpose.LOGIN,
                    UUID.randomUUID(),
                    "123456",
                    true))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("PHONE_CODE_UNAVAILABLE"));
  }

  @Test
  void validationDistinguishesConsumedAndWrongCodesAndUsesRolePurposeBoundKey() {
    UUID challenge = UUID.randomUUID();
    when(dao.verifyPhoneCode(anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(0L, -1L, 1L);
    assertThatThrownBy(
            () ->
                codes.verify(
                    "13911112222",
                    RegisterRole.PARTICIPANT,
                    Requests.PhoneCodePurpose.LOGIN,
                    challenge,
                    "123456",
                    true))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("PHONE_CODE_EXPIRED"));
    assertThatThrownBy(
            () ->
                codes.verify(
                    "13911112222",
                    RegisterRole.PARTICIPANT,
                    Requests.PhoneCodePurpose.LOGIN,
                    challenge,
                    "000000",
                    true))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo("PHONE_CODE_INVALID"));
    codes.verify(
        "13911112222",
        RegisterRole.ORGANIZER,
        Requests.PhoneCodePurpose.REGISTER,
        challenge,
        "123456",
        false);
    var keys = ArgumentCaptor.forClass(String.class);
    verify(dao, times(3)).verifyPhoneCode(keys.capture(), anyString(), anyString(), anyBoolean());
    assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(2));
    assertThat(keys.getAllValues().get(2)).contains(":register:");
  }
}
