package org.timmy.knowcommute.lens;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.timmy.knowcommute.user.DeviceUser;

/**
 * Saving runs the check first and the write after, in its own short transaction: the check is a
 * model call, and an external call never holds a database transaction open.
 */
@Service
public class ListenerProfileService {

    private final ListenerProfileRepository profiles;
    private final ProfileCheck check;
    private final TransactionTemplate transactions;
    private final String generatorPrompt;

    public ListenerProfileService(ListenerProfileRepository profiles, ProfileCheck check,
                                  PlatformTransactionManager transactionManager,
                                  @Value("classpath:prompts/listener-profile-generator.txt") Resource generatorPrompt) {
        this.profiles = profiles;
        this.check = check;
        this.transactions = new TransactionTemplate(transactionManager);
        this.generatorPrompt = read(generatorPrompt);
    }

    public ListenerProfileView get(DeviceUser user) {
        return profiles.findByUserId(user.getId())
                .map(ListenerProfileView::from)
                .orElseThrow(ListenerProfileNotFoundException::new);
    }

    public ListenerProfileView save(DeviceUser user, String profileText) {
        String text = normalise(profileText);
        String hash = sha256(text);

        Optional<ListenerProfile> existing = profiles.findByUserId(user.getId());
        if (existing.isPresent() && existing.get().getProfileHash().equals(hash)) {
            return ListenerProfileView.from(existing.get());
        }

        ProfileCheck.Verdict verdict = check.check(text);
        if (!verdict.accepted()) {
            throw new ProfileRejectedException(verdict);
        }

        return transactions.execute(status -> {
            ListenerProfile profile = profiles.findByUserId(user.getId())
                    .map(found -> {
                        found.replace(text, hash);
                        return found;
                    })
                    .orElseGet(() -> ListenerProfile.of(user.getId(), text, hash));
            return ListenerProfileView.from(profiles.saveAndFlush(profile));
        });
    }

    public void delete(DeviceUser user) {
        transactions.executeWithoutResult(status -> profiles.deleteByUserId(user.getId()));
    }

    public ProfilePromptView generatorPrompt() {
        return new ProfilePromptView(generatorPrompt);
    }

    /** Line breaks are kept: the generated profile uses one heading per line. */
    private static String normalise(String text) {
        return text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
