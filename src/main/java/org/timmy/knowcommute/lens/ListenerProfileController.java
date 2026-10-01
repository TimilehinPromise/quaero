package org.timmy.knowcommute.lens;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.timmy.knowcommute.security.CurrentUser;
import org.timmy.knowcommute.user.DeviceUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lens")
public class ListenerProfileController {

    private final ListenerProfileService profileService;

    @GetMapping("/profile")
    public ListenerProfileView get(@CurrentUser DeviceUser user) {
        return profileService.get(user);
    }

    @PutMapping("/profile")
    public ListenerProfileView save(@CurrentUser DeviceUser user,
                                    @Valid @RequestBody SaveListenerProfileRequest request) {
        return profileService.save(user, request.profile());
    }

    @DeleteMapping("/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser DeviceUser user) {
        profileService.delete(user);
    }

    @GetMapping("/profile-prompt")
    public ProfilePromptView profilePrompt() {
        return profileService.generatorPrompt();
    }
}
