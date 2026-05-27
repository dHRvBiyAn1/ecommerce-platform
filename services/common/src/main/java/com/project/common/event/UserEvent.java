package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder(builderMethodName = "userEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEvent extends BaseEvent {

    public enum Type {
        CREATED, LOGGED_IN, LOGGED_OUT, PASSWORD_CHANGED, ROLE_CHANGED,
        EMAIL_VERIFIED, MFA_ENABLED, MFA_DISABLED, ACCOUNT_LOCKED,
        PROFILE_UPDATED, DELETED
    }

    private Type type;
    private UUID userId;
    private String email;
    private String displayName;
}
