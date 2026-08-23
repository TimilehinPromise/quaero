package org.timmy.knowcommute.audio;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Component
public class R2AudioStorage implements AudioStorage {

    private final AudioProperties properties;

    private volatile S3Client client;
    private volatile S3Presigner presigner;

    public R2AudioStorage(AudioProperties properties) {
        this.properties = properties;
    }

    /**
     * Built on first use, not at construction. The AWS SDK rejects blank credentials eagerly,
     * which would stop the whole application booting on a machine that has no R2 keys — an
     * unreasonable price for a feature most local work never touches.
     */
    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = clientBuilder().build();
                }
            }
        }
        return client;
    }

    private S3Presigner presigner() {
        if (presigner == null) {
            synchronized (this) {
                if (presigner == null) {
                    presigner = presignerBuilder().build();
                }
            }
        }
        return presigner;
    }

    private software.amazon.awssdk.services.s3.S3ClientBuilder clientBuilder() {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials());
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder;
    }

    private S3Presigner.Builder presignerBuilder() {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials());
        if (hasEndpoint()) {
            // The presigner needs the same addressing style as the client, or it signs a
            // virtual-host URL (bucket.host/key) for an object written path-style.
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }
        return builder;
    }

    private StaticCredentialsProvider credentials() {
        if (isBlank(properties.accessKey()) || isBlank(properties.secretKey())) {
            throw new AudioStorageException(
                    "R2 credentials are not configured; set R2_ACCESS_KEY and R2_SECRET_KEY", null);
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private boolean hasEndpoint() {
        return !isBlank(properties.endpoint());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String store(String key, byte[] mp3) {
        try {
            client().putObject(PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType("audio/mpeg")
                    .build(), RequestBody.fromBytes(mp3));
            return key;
        } catch (S3Exception e) {
            throw new AudioStorageException("Could not store audio at " + key, e);
        }
    }

    @Override
    public URI presignedUrl(String key) {
        return presigner().presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.presignTtl())
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket()).key(key).build())
                        .build())
                .url().toString().transform(URI::create);
    }

    @Override
    public void delete(String key) {
        client().deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket()).key(key).build());
    }

    @Override
    public List<String> keysOlderThan(Instant cutoff) {
        return client().listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(properties.bucket()).build())
                .contents().stream()
                .filter(object -> object.lastModified().isBefore(cutoff))
                .map(object -> object.key())
                .toList();
    }
}
