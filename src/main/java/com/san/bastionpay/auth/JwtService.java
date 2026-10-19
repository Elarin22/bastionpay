package com.san.bastionpay.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.san.bastionpay.member.Member;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Access Token 발급·검증. 서명 알고리즘은 HS256 하나로 고정해 alg=none 같은 변조를 거부한다.
 * Refresh Token 은 {@link RefreshTokenService} 가 맡는다.
 */
@Service
public class JwtService {

    private static final String ISSUER = "bastionpay";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        byte[] secret = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret 은 32바이트 이상이어야 합니다. (.env 의 JWT_SECRET 확인)");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.props = props;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = nimbus;
    }

    public String issueAccessToken(Member member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(member.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .claim("nick", member.getNickname())
                .claim("role", member.getRole().name())
                .claim("tier", member.getTier().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Optional<AuthMember> parse(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String role = jwt.getClaimAsString("role");
            if (role == null) return Optional.empty();
            // 등급 도입 전에 발급된 토큰에는 tier 가 없다
            String tier = jwt.getClaimAsString("tier");
            return Optional.of(new AuthMember(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("nick"),
                    role, tier == null ? "STANDARD" : tier));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
