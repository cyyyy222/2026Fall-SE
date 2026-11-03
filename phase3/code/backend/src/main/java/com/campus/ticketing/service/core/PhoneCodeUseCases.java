package com.campus.ticketing.service.core;

import static com.campus.ticketing.service.core.BusinessSupport.*;

import com.campus.ticketing.common.*;
import com.campus.ticketing.dao.redis.*;
import com.campus.ticketing.dto.requests.Requests;
import com.campus.ticketing.dto.views.Views;
import com.campus.ticketing.enums.RegisterRole;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class PhoneCodeUseCases {
  private static final Logger LOG = LoggerFactory.getLogger(PhoneCodeUseCases.class);
  private final BusinessSupport b;
  private final ObjectProvider<RedisTransientDao> stores;
  private final RateLimitService rate;
  private final RedisKeyFactory keys;
  private final byte[] secret;
  private final boolean local;
  private final SecureRandom random = new SecureRandom();

  public PhoneCodeUseCases(
      BusinessSupport b,
      ObjectProvider<RedisTransientDao> stores,
      RateLimitService rate,
      Environment env) {
    this.b = b;
    this.stores = stores;
    this.rate = rate;
    String environment = env.getProperty("app.environment", "local");
    keys = new RedisKeyFactory(environment);
    local = environment.equals("local");
    secret = env.getProperty("app.rate.hmac-secret", "").getBytes(StandardCharsets.UTF_8);
  }

  private RedisTransientDao store() {
    var store = stores.getIfAvailable();
    if (!local || store == null || secret.length < 32)
      throw error(503, "PHONE_CODE_UNAVAILABLE", "本地验证码服务不可用；非本地环境尚未配置短信服务");
    return store;
  }

  private String key(String phone, RegisterRole role, Requests.PhoneCodePurpose purpose) {
    return keys.phoneCode(
        purpose.name(),
        RateLimitService.fingerprint(secret, "otp", "phone-role", phone + ":" + role.name()));
  }

  private String hash(UUID id, String code) {
    return RateLimitService.fingerprint(secret, "otp", "challenge", id + ":" + code);
  }

  public UseCaseResult<Views.PhoneCodeReceipt> issue(RequestContext c, Requests.PhoneCode r) {
    b.db();
    if (r.purpose() == Requests.PhoneCodePurpose.CHANGE_PHONE) {
      var user =
          b.actor(
              c,
              com.campus.ticketing.enums.Role.PARTICIPANT,
              com.campus.ticketing.enums.Role.ORGANIZER);
      valid(user.role().equals(r.role().name()), "验证码角色与当前账号不符");
      var current =
          b.need(com.campus.ticketing.entity.PhoneIdentityEntity.class, loginPhoneId(user), false);
      require(!current.phone().equals(r.phone()), "PHONE_UNCHANGED", "新手机号与当前手机号相同");
      var target =
          b.db()
              .one(
                  com.campus.ticketing.entity.PhoneIdentityEntity.class,
                  com.campus.ticketing.dao.RecordStore.Filter.eq("phone", r.phone()),
                  false)
              .orElse(null);
      if (target != null)
        require(
            b.db()
                    .count(
                        com.campus.ticketing.entity.UserEntity.class,
                        com.campus.ticketing.dao.RecordStore.Filter.and(
                            currentPhoneFilter(target.id()),
                            com.campus.ticketing.dao.RecordStore.Filter.eq("role", user.role()),
                            com.campus.ticketing.dao.RecordStore.Filter.nil("deletedAtMs")))
                == 0,
            "PHONE_ALREADY_BOUND",
            "该手机号已被同角色的其他账号绑定");
    }
    var dao = store();
    rate.check("phone-code", c, r.phone());
    UUID id = id();
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    String key = key(r.phone(), r.role(), r.purpose());
    try {
      // One phone/role cooldown is shared by login and registration.
      String cooldown = key(r.phone(), r.role(), Requests.PhoneCodePurpose.LOGIN) + ":cooldown";
      if (!dao.issuePhoneCode(key, cooldown, id.toString(), hash(id, code)))
        throw new RateLimitService.Limited(60);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw error(503, "PHONE_CODE_UNAVAILABLE", "验证码存储暂不可用");
    }
    // Deliberate local-only delivery, authorized for development; never returned by an API or
    // audit.
    LOG.info(
        "[LOCAL_PHONE_CODE] phone={} role={} purpose={} code={} validSeconds=300",
        r.phone(),
        r.role(),
        r.purpose(),
        code);
    return new UseCaseResult<>(
        200,
        new Views.PhoneCodeReceipt(id, b.time(b.now() + 300_000), 60, "LOCAL_CONSOLE"),
        List.of());
  }

  public void verify(
      String phone,
      RegisterRole role,
      Requests.PhoneCodePurpose purpose,
      UUID id,
      String code,
      boolean consume) {
    if (id == null || code == null || !code.matches("[0-9]{6}"))
      throw error(422, "PHONE_CODE_REQUIRED", "请先获取并填写六位手机号验证码");
    long result;
    try {
      result =
          store()
              .verifyPhoneCode(key(phone, role, purpose), id.toString(), hash(id, code), consume);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw error(503, "PHONE_CODE_UNAVAILABLE", "验证码校验服务暂不可用");
    }
    if (result == 0) throw error(422, "PHONE_CODE_EXPIRED", "验证码已过期、已使用或与手机号及角色不匹配，请重新获取");
    if (result != 1) throw error(422, "PHONE_CODE_INVALID", "验证码错误；连续错误五次后须重新获取");
  }
}
