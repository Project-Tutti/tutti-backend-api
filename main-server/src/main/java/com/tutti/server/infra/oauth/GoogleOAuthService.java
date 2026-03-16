package com.tutti.server.infra.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Google ID Token 검증 서비스.
 *
 * <h3>인증 흐름</h3>
 * 
 * <pre>
 * [프론트엔드]
 *   1. Google Sign-In SDK로 사용자 로그인
 *   2. ID Token 획득
 *   3. POST /api/auth/social { provider: "google", code: "{idToken}" }
 *
 * [백엔드 — 이 서비스]
 *   4. Google 공개키로 ID Token 서명 검증
 *   5. audience(Client ID) 일치 확인
 *   6. 이메일, 이름, 프로필 이미지 추출 → GoogleUserInfo 반환
 * </pre>
 *
 * <h3>보안</h3>
 * <ul>
 * <li>Google 공개키는 {@code GoogleIdTokenVerifier}가 자동으로 캐싱/갱신합니다.</li>
 * <li>audience 검증으로 다른 앱의 토큰 사용을 차단합니다.</li>
 * <li>만료된 토큰은 자동으로 거부됩니다.</li>
 * </ul>
 */
@Slf4j
@Service
public class GoogleOAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuthService(@Value("${google.oauth.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    /**
     * Google ID Token을 검증하고 사용자 정보를 추출합니다.
     *
     * @param idTokenString 프론트엔드에서 전달받은 Google ID Token
     * @return 검증된 사용자 정보 (이메일, 이름, 아바타 URL)
     * @throws BusinessException OAUTH_SERVER_ERROR — 토큰 검증 실패
     */
    public GoogleUserInfo verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.warn("Google ID Token 검증 실패: 유효하지 않은 토큰");
                throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String avatarUrl = (String) payload.get("picture");

            // 이메일이 없으면 사용 불가
            if (email == null || email.isBlank()) {
                log.warn("Google ID Token에 이메일 정보가 없음");
                throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
            }

            log.info("Google 사용자 인증 성공: email={}", email);

            return new GoogleUserInfo(
                    email,
                    name != null ? name : email.split("@")[0],
                    avatarUrl);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google ID Token 검증 중 오류: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_SERVER_ERROR);
        }
    }
}
