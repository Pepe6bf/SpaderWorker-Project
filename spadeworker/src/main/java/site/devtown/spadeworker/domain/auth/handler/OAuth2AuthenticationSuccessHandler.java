package site.devtown.spadeworker.domain.auth.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;
import site.devtown.spadeworker.domain.auth.model.info.OAuth2UserInfo;
import site.devtown.spadeworker.domain.auth.model.info.OAuth2UserInfoFactory;
import site.devtown.spadeworker.domain.auth.repository.OAuth2AuthorizationRequestBasedOnCookieRepository;
import site.devtown.spadeworker.domain.auth.repository.UserRefreshTokenRepository;
import site.devtown.spadeworker.domain.auth.service.JwtService;
import site.devtown.spadeworker.domain.auth.token.AuthToken;
import site.devtown.spadeworker.domain.auth.token.UserRefreshToken;
import site.devtown.spadeworker.domain.user.model.constant.AuthProviderType;
import site.devtown.spadeworker.global.util.CookieUtil;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static site.devtown.spadeworker.domain.auth.repository.OAuth2AuthorizationRequestBasedOnCookieRepository.REDIRECT_URI_PARAM_COOKIE_NAME;
import static site.devtown.spadeworker.domain.auth.repository.OAuth2AuthorizationRequestBasedOnCookieRepository.REFRESH_TOKEN;

@Slf4j
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtService jwtService;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final OAuth2AuthorizationRequestBasedOnCookieRepository authorizationRequestRepository;
    private final List<String> authorizedRedirectUris;
    private final Integer cookieMaxAge;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        // ?????? ?????? ?????? ??????
        if (response.isCommitted()) {
            logger.debug("????????? ?????? ????????????????????? [" + targetUrl + "]??? ??????????????? ??? ??? ????????????.");
            return;
        }

        clearAuthenticationAttributes(request, response);
        // ??????????????? ??????
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * ?????? ?????? ??? ????????????????????? ?????????????????? ???????????? ?????? ??????
     */
    protected String determineTargetUrl(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        Optional<String> redirectUri = CookieUtil.getCookie(request, REDIRECT_URI_PARAM_COOKIE_NAME)
                .map(Cookie::getValue);

        // ????????? ??????????????? URI ?????? ??????
        if (redirectUri.isPresent() && !isAuthorizedRedirectUri(redirectUri.get())) {
            throw new IllegalArgumentException("???????????? ?????? ??????????????? URI ?????????.");
        }

        String targetUrl = redirectUri.orElse(getDefaultTargetUrl());

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        AuthProviderType providerType = AuthProviderType.valueOf(authToken.getAuthorizedClientRegistrationId().toUpperCase());

        OidcUser user = ((OidcUser) authentication.getPrincipal());
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(providerType, user.getAttributes());
        Collection<? extends GrantedAuthority> authorities = ((OidcUser) authentication.getPrincipal()).getAuthorities();

        // access token ??????
        AuthToken accessToken = jwtService.createAccessToken(
                userInfo.getId(),
                authorities
        );

        log.info("[Access-Token] = {}", accessToken.getTokenValue());

        // refresh ?????? ??????
        AuthToken refreshToken = jwtService.createRefreshToken(
                userInfo.getId(),
                authorities
        );

        // refresh ?????? DB??? ??????
        Optional<UserRefreshToken> userRefreshToken = userRefreshTokenRepository.findByPersonalId(userInfo.getId());
        if (userRefreshToken.isPresent()) {
            userRefreshToken.get().changeTokenValue(refreshToken.getTokenValue());
        } else {
            userRefreshTokenRepository.saveAndFlush(
                    UserRefreshToken.of(
                            userInfo.getId(),
                            refreshToken.getTokenValue()
                    )
            );
        }

        CookieUtil.deleteCookie(request, response, REFRESH_TOKEN);
        CookieUtil.addCookie(
                response,
                REFRESH_TOKEN,
                refreshToken.getTokenValue(),
                cookieMaxAge
        );

        return UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("token", accessToken.getTokenValue())
                .build().toUriString();
    }

    /**
     * ????????? ??????????????? URI ??? ????????? ??????
     */
    private boolean isAuthorizedRedirectUri(String uri) {
        URI clientRedirectUri = URI.create(uri);

        return authorizedRedirectUris
                .stream()
                .anyMatch(authorizedRedirectUri -> {
                    // Host & Port ??? ?????????
                    URI authorizedURI = URI.create(authorizedRedirectUri);
                    return authorizedURI.getHost().equalsIgnoreCase(clientRedirectUri.getHost())
                            && authorizedURI.getPort() == clientRedirectUri.getPort();
                });
    }

    /**
     * ?????? ?????? ?????????
     */
    protected void clearAuthenticationAttributes(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        super.clearAuthenticationAttributes(request);
        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}
