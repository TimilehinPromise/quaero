package org.timmy.knowcommute.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/** Runs against a real S3-compatible server: presigned URLs cannot be verified against a mock. */
@Testcontainers
class R2AudioStorageTest {

    private static final String BUCKET = "readcast-audio-test";

    @Container
    private static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

    private static AudioProperties properties;

    @BeforeAll
    static void createBucket() {
        properties = new AudioProperties(BUCKET, MINIO.getS3URL(), MINIO.getUserName(),
                MINIO.getPassword(), "us-east-1", Duration.ofMinutes(15), Duration.ofDays(30));

        try (S3Client admin = S3Client.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(properties.endpoint()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .build()) {
            admin.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    private final AudioStorage storage = new R2AudioStorage(properties);

    @Test
    void storesAudioAndReturnsTheKey() {
        String key = storage.store("audio/stored.mp3", "fake mp3 bytes".getBytes());

        assertThat(key).isEqualTo("audio/stored.mp3");
    }

    @Test
    void presignedUrlActuallyDownloadsTheObject() throws Exception {
        byte[] payload = "the narration bytes".getBytes();
        storage.store("audio/playable.mp3", payload);

        URI url = storage.presignedUrl("audio/playable.mp3");
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(payload);
    }

    @Test
    void presignedUrlIsTimeLimitedRatherThanPermanent() {
        URI url = storage.presignedUrl("audio/playable.mp3");

        assertThat(url.getQuery())
                .contains("X-Amz-Expires")
                .contains("X-Amz-Signature");
    }

    @Test
    void unsignedAccessIsRefused() throws Exception {
        storage.store("audio/private.mp3", "secret".getBytes());
        URI unsigned = URI.create(properties.endpoint() + "/" + BUCKET + "/audio/private.mp3");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(unsigned).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void deletesAnObject() throws Exception {
        storage.store("audio/temporary.mp3", "bytes".getBytes());
        URI url = storage.presignedUrl("audio/temporary.mp3");

        storage.delete("audio/temporary.mp3");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void listsOnlyObjectsOlderThanTheCutoff() {
        storage.store("audio/fresh.mp3", "bytes".getBytes());

        assertThat(storage.keysOlderThan(Instant.now().minus(Duration.ofDays(1))))
                .doesNotContain("audio/fresh.mp3");
        assertThat(storage.keysOlderThan(Instant.now().plus(Duration.ofMinutes(1))))
                .contains("audio/fresh.mp3");
    }
}
