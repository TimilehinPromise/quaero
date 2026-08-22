package org.timmy.knowcommute.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.timmy.knowcommute.user.DeviceUser;

@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final DeviceIdentityService identityService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && DeviceUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public DeviceUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return identityService.resolve(webRequest.getHeader(DEVICE_ID_HEADER));
    }
}
