package com.campus.ticketing.dto.requests;

import com.campus.ticketing.common.*;
import com.campus.ticketing.enums.*;
import com.campus.ticketing.validation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class Requests {
  private Requests() {}

  public record Empty() {}

  public record Login(
      @NotNull @CodePointLength(min = 1, max = 60) String username,
      @NotNull @Size(max = 72) String password) {}

  public record Register(
      @NotNull @CodePointLength(min = 1, max = 60) String username,
      @NotNull
          @Pattern(
              regexp = "(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}",
              message = "密码须为8～72位ASCII字母和数字组合")
          String password,
      @Email(message = "邮箱格式不正确，例如12345@qq.com") @Size(max = 254) String email,
      @NotNull @Pattern(regexp = "[0-9]{11}") String phone,
      @NotNull RegisterRole role,
      @NotNull UUID verificationId,
      @NotNull @Pattern(regexp = "[0-9]{6}") String verificationCode) {
    public Register {
      email = email == null || email.isBlank() ? null : email.strip();
    }

    public Register(
        String username, String password, String email, String phone, RegisterRole role) {
      this(username, password, email, phone, role, null, null);
    }
  }

  public enum PhoneCodePurpose {
    LOGIN,
    REGISTER,
    RESET_PASSWORD,
    RECOVERY_CONTACT,
    CHANGE_PHONE
  }

  public record PhoneCode(
      @NotNull @Pattern(regexp = "1[0-9]{10}") String phone,
      @NotNull RegisterRole role,
      @NotNull PhoneCodePurpose purpose) {}

  public record PhoneLogin(
      @NotNull @Pattern(regexp = "1[0-9]{10}") String phone,
      @NotNull RegisterRole role,
      @NotNull UUID verificationId,
      @NotNull @Pattern(regexp = "[0-9]{6}") String verificationCode,
      @NotNull PhoneCodePurpose purpose) {}

  public record Recovery(
      @NotNull @CodePointLength(min = 1, max = 60) String account,
      @NotNull @Pattern(regexp = "1[0-9]{10}") String phone,
      @NotNull RegisterRole role,
      @NotNull @Pattern(regexp = "1[0-9]{10}") String currentPhone,
      @NotNull @CodePointLength(min = 20, max = 2000) String statement,
      @NotNull @CodePointLength(min = 20, max = 2000) String pastBehavior,
      @NotNull UUID verificationId,
      @NotNull @Pattern(regexp = "[0-9]{6}") String verificationCode) {}

  public record PhonePasswordReset(
      @NotNull @Pattern(regexp = "1[0-9]{10}") String phone,
      @NotNull RegisterRole role,
      @NotNull UUID verificationId,
      @NotNull @Pattern(regexp = "[0-9]{6}") String verificationCode,
      @NotNull
          @Pattern(
              regexp = "(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}",
              message = "新密码须为8～72位ASCII字母和数字组合")
          String newPassword) {}

  public record ChangePhone(
      @NotNull @Pattern(regexp = "1[0-9]{10}") String newPhone,
      @NotNull UUID verificationId,
      @NotNull @Pattern(regexp = "[0-9]{6}") String verificationCode) {}

  public record Profile(@NotNull @CodePointLength(min = 1, max = 60) String displayName) {}

  public record Password(
      @NotNull String oldPassword,
      @NotNull @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}")
          String newPassword) {}

  public record Closure(
      @NotNull String password, @NotNull @Pattern(regexp = "CLOSE") String confirmation) {}

  public record Registration(
      @NotNull UUID requestId,
      @NotNull UUID sessionId,
      @NotNull UUID ticketTypeId,
      @NotNull @CodePointLength(min = 1, max = 60) String attendeeName) {}

  public record Cancel(@CodePointLength(max = 200) @JsonProperty(required = true) String reason) {}

  public record Attempt(@NotNull UUID attemptId) {}

  public record Refund(
      @NotNull UUID attemptId, @NotNull @CodePointLength(min = 1, max = 200) String reason) {}

  public record Ticket(@NotNull UUID ticketId) {}

  public record Version(@NotNull @Min(0) Long expectedVersion) {}

  public record VersionReason(
      @NotNull @Min(0) Long expectedVersion,
      @NotNull @CodePointLength(min = 1, max = 200) String reason) {}

  public record Draft(
      @NotNull @Min(0) Long expectedVersion,
      @CodePointLength(min = 1, max = 60) @JsonProperty(required = true) String name,
      @JsonProperty(required = true) UUID categoryId,
      @CodePointLength(max = 500) @JsonProperty(required = true) String description,
      @NotNull @Size(max = 20) List<@NotNull @Valid SessionDraft> sessions,
      @CodePointLength(min = 1, max = 60) String proposedCategoryName) {
    public Draft(
        Long expectedVersion,
        String name,
        UUID categoryId,
        String description,
        List<SessionDraft> sessions) {
      this(expectedVersion, name, categoryId, description, sessions, null);
    }
  }

  public record SessionDraft(
      @NotNull UUID id,
      @CodePointLength(min = 1, max = 60) @JsonProperty(required = true) String name,
      @BeijingTime(granularity = 60) @JsonProperty(required = true) OffsetDateTime startsAt,
      @BeijingTime(granularity = 60) @JsonProperty(required = true) OffsetDateTime endsAt,
      @CodePointLength(min = 1, max = 60) @JsonProperty(required = true) String location,
      @Min(1) @Max(9999) @JsonProperty(required = true) Integer capacity,
      @BeijingTime(granularity = 60) @JsonProperty(required = true) OffsetDateTime salesOpensAt,
      @BeijingTime(granularity = 60) @JsonProperty(required = true)
          OffsetDateTime registrationClosesAt,
      @NotNull @Size(max = 10) List<@NotNull @Valid TicketTypeDraft> ticketTypes) {}

  public record TicketTypeDraft(
      @NotNull UUID id,
      @CodePointLength(min = 1, max = 60) @JsonProperty(required = true) String name,
      @Min(1) @Max(9999) @JsonProperty(required = true) Integer capacity,
      @Min(0) @Max(9999990) @JsonProperty(required = true) Long priceJiao,
      @NotNull Boolean saleStopped) {}

  public record PublishedEdit(
      @NotNull @Min(0) Long expectedVersion,
      @Valid PatchField<@CodePointLength(max = 500) String> description,
      @PatchNonNull @Valid
          PatchField<@NotNull @Size(max = 20) List<@NotNull @Valid SessionEdit>> sessions) {}

  public record SessionEdit(
      @NotNull UUID id,
      @PatchNonNull @Valid PatchField<@CodePointLength(min = 1, max = 60) String> location,
      @PatchNonNull @Valid PatchField<@Min(1) @Max(9999) Integer> capacity,
      @PatchNonNull @Valid
          PatchField<@NotNull @Size(max = 10) List<@NotNull @Valid TypeEdit>> ticketTypes) {}

  public record TypeEdit(
      @NotNull UUID id,
      @PatchNonNull @Valid PatchField<@Min(1) @Max(9999) Integer> capacity,
      @PatchNonNull PatchField<Boolean> saleStopped) {}

  public record Export(
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = OmittedNonNullDeserializer.class)
          UUID sessionId,
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = OmittedNonNullDeserializer.class)
          UUID ticketTypeId,
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = OmittedNonNullDeserializer.class)
          OrderStatus orderStatus,
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = OmittedNonNullDeserializer.class)
          CheckinStatus checkinStatus) {}

  public record Report(
      @NotNull UUID sessionId,
      @NotNull @CodePointLength(min = 1, max = 200) String reason,
      @NotNull @CodePointLength(min = 1, max = 2000) String description,
      @NotNull @CodePointLength(min = 1, max = 2000) String evidenceText) {}

  public record RestrictionAppeal(
      @NotNull UUID restrictionId,
      @NotNull @CodePointLength(min = 10, max = 1000) String reason,
      @CodePointLength(max = 2000) String evidenceText) {}

  public record ResolveAppeal(
      @NotNull AppealStatus decision, @NotNull @CodePointLength(min = 2, max = 1000) String note) {}

  public record CreateAccount(
      @NotNull @CodePointLength(min = 1, max = 60) String username,
      @NotNull @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}")
          String initialPassword,
      @NotNull ManagedRole role,
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = OmittedNonNullDeserializer.class)
          UUID sessionId) {}

  public record UpdateAccount(
      @NotNull @Min(0) Long expectedVersion,
      @PatchNonNull PatchField<Boolean> enabled,
      @PatchNonNull PatchField<UUID> assignedSessionId) {}

  public record ResetPassword(
      @NotNull @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}")
          String initialPassword,
      @NotNull @CodePointLength(min = 1, max = 200) String reason,
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = OmittedNonNullDeserializer.class)
          UUID recoveryRequestId) {}

  public record ResolveRecovery(
      @NotNull RecoveryDecision decision,
      @NotNull @CodePointLength(min = 10, max = 2000) String verificationNote,
      @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,72}") String initialPassword,
      @CodePointLength(min = 1, max = 200) String reason) {
    public ResolveRecovery(RecoveryDecision decision, String verificationNote) {
      this(decision, verificationNote, null, null);
    }
  }

  public record CreateCategory(@NotNull @CodePointLength(min = 1, max = 60) String name) {}

  public record UpdateCategory(
      @NotNull @Min(0) Long expectedVersion,
      @PatchNonNull @Valid PatchField<@CodePointLength(min = 1, max = 60) String> name,
      @PatchNonNull PatchField<Boolean> enabled) {}

  public record Takedown(@NotNull @Min(0) Long expectedVersion, @NotNull UUID investigationId) {}

  public record Investigation(
      @NotNull UUID eventId,
      @NotNull List<@NotNull UUID> reportIds,
      @NotNull @CodePointLength(min = 1, max = 120) String factKey,
      @NotNull Conclusion conclusion,
      @NotNull @CodePointLength(min = 1, max = 200) String reason,
      @NotNull @CodePointLength(min = 1, max = 2000) String evidenceText) {}

  public record Restriction(
      @NotNull UUID phoneIdentityId,
      @NotNull @Size(min = 1) List<@NotNull UUID> basisFactIds,
      @NotNull @CodePointLength(min = 1, max = 200) String reason) {}

  public record PosterUpload(
      @NotNull @Min(0) Long expectedVersion, @NotNull byte[] bytes, @NotNull String contentType) {}
}
