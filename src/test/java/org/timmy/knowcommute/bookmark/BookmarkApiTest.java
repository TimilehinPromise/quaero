package org.timmy.knowcommute.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetAddress;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.timmy.knowcommute.ApiTest;
import org.timmy.knowcommute.user.DeviceUserRepository;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ApiTest
class BookmarkApiTest {

    private static final String DEVICE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ARTICLE = "https://example.com/an-article";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookmarkRepository bookmarks;

    @Autowired
    private DeviceUserRepository users;

    @MockitoBean
    private HostResolver hostResolver;

    @BeforeEach
    void clearLibraries() {
        bookmarks.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    private void resolvesPublicly() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] {InetAddress.getByName("93.184.216.34")});
    }

    private String body(String url) {
        return "{\"url\":\"" + url + "\",\"summaryLength\":\"MEDIUM\"}";
    }

    @Test
    void createsBookmarkAsPendingAndProvisionsUser() throws Exception {
        resolvesPublicly();

        mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ARTICLE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.url").value(ARTICLE));

        assertThat(users.findByDeviceId(DEVICE_ID)).isPresent();
    }

    @Test
    void rejectsRequestWithoutDeviceId() throws Exception {
        resolvesPublicly();

        mockMvc.perform(post("/api/v1/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ARTICLE)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void rejectsNonUuidDeviceId() throws Exception {
        resolvesPublicly();

        mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ARTICLE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUrlPointingAtCloudMetadata() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] {InetAddress.getByName("169.254.169.254")});

        mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("http://169.254.169.254/latest/meta-data/")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid URL"))
                .andExpect(jsonPath("$.type").value("urn:readcast:problem:invalid-url"));
    }

    @Test
    void rejectsDuplicateUrl() throws Exception {
        resolvesPublicly();
        String url = "https://example.com/duplicate";

        mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(url)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(url)))
                .andExpect(status().isConflict());
    }

    @Test
    void listsOnlyTheCallersOwnBookmarks() throws Exception {
        resolvesPublicly();
        String otherDevice = "22222222-2222-2222-2222-222222222222";

        mockMvc.perform(post("/api/v1/bookmarks")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("https://example.com/mine")));

        mockMvc.perform(get("/api/v1/bookmarks").header("X-Device-Id", otherDevice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/bookmarks").header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cannotReadAnotherUsersBookmark() throws Exception {
        resolvesPublicly();
        String otherDevice = "33333333-3333-3333-3333-333333333333";

        String response = mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://example.com/private")))
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/bookmarks/{id}", id).header("X-Device-Id", otherDevice))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIsSoftAndHidesTheBookmark() throws Exception {
        resolvesPublicly();

        String response = mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://example.com/to-delete")))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        mockMvc.perform(delete("/api/v1/bookmarks/{id}", id).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/bookmarks/{id}", id).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isNotFound());

        assertThat(bookmarks.findById(id)).isPresent();
        assertThat(bookmarks.findById(id).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void rejectsBlankUrl() throws Exception {
        mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\",\"summaryLength\":\"MEDIUM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retriesAFailedBookmarkFromTheStartOfThePipeline() throws Exception {
        resolvesPublicly();

        String response = mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://example.com/failed")))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        Bookmark failed = bookmarks.findById(id).orElseThrow();
        failed.fail("gave up");
        bookmarks.saveAndFlush(failed);

        mockMvc.perform(post("/api/v1/bookmarks/{id}/retry", id).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.lastError").doesNotExist());

        Bookmark requeued = bookmarks.findById(id).orElseThrow();
        assertThat(requeued.getAttempts()).isZero();
        assertThat(requeued.getNextAttemptAt()).isNull();
    }

    @Test
    void refusesToRetryABookmarkThatIsStillMoving() throws Exception {
        resolvesPublicly();

        String response = mockMvc.perform(post("/api/v1/bookmarks")
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://example.com/active")))
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/bookmarks/{id}/retry", id).header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:readcast:problem:not-recoverable"));
    }
}
