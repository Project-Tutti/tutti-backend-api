package com.tutti.server.domain.user.dto.response;

import com.tutti.server.domain.user.entity.Profile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {

    private String id;
    private String email;
    private String name;
    private String provider;
    private String avatarUrl;

    public static UserProfileResponse from(Profile profile) {
        return UserProfileResponse.builder()
                .id(profile.getId().toString())
                .email(profile.getEmail())
                .name(profile.getName())
                .provider(profile.getProvider().name().toLowerCase())
                .avatarUrl(profile.getAvatarUrl())
                .build();
    }
}
