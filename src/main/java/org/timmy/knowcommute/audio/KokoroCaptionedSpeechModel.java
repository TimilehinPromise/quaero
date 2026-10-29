package org.timmy.knowcommute.audio;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class KokoroCaptionedSpeechModel implements CaptionedSpeechModel {

    private final TtsProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;
    private final URI endpoint;

    KokoroCaptionedSpeechModel(TtsProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        this.endpoint = URI.create(captionedSpeechUrl(properties.baseUrl()));
    }

    @Override
    public CaptionedSpeech call(String text) {
        try {
            var request = HttpRequest.newBuilder(endpoint)
                    .timeout(properties.timeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            new KokoroRequest(properties.model(), text, properties.voice(),
                                    properties.responseFormat(), properties.speed()))))
                    .build();
            if (properties.hasBasicAuth()) {
                request = HttpRequest.newBuilder(request, (name, value) -> true)
                        .header("Authorization", basicAuth(properties))
                        .build();
            }

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AudioGenerationException(
                        "Kokoro captioned speech returned HTTP " + response.statusCode());
            }
            return decode(json.readValue(response.body(), KokoroResponse.class));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioGenerationException("Interrupted waiting for Kokoro captioned speech", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new AudioGenerationException("Could not read Kokoro captioned speech", e);
        }
    }

    private CaptionedSpeech decode(KokoroResponse response) {
        if (response == null || response.audio() == null || response.audio().isBlank()) {
            throw new AudioGenerationException("Kokoro returned no audio");
        }
        if (!"audio/mpeg".equals(response.audioFormat())) {
            throw new AudioGenerationException("Kokoro returned an unexpected audio format");
        }
        if (response.timestamps() == null) {
            throw new AudioGenerationException("Kokoro returned no word timestamps");
        }

        byte[] audio = Base64.getDecoder().decode(response.audio());
        List<SpeechCaption> captions = new ArrayList<>(response.timestamps().size());
        double previousStart = 0;
        int rejected = 0;
        for (KokoroTimestamp caption : response.timestamps()) {
            if (!usable(caption) || caption.startTime() < previousStart) {
                rejected++;
                continue;
            }
            captions.add(new SpeechCaption(caption.word(), caption.startTime(), caption.endTime()));
            previousStart = caption.startTime();
        }
        if (captions.isEmpty()) {
            throw new AudioGenerationException("Kokoro returned no usable word timestamps");
        }
        if (rejected > 0) {
            log.warn("Skipped {} of {} unusable Kokoro word timestamps", rejected, response.timestamps().size());
        }
        return new CaptionedSpeech(audio, List.copyOf(captions));
    }

    private boolean usable(KokoroTimestamp caption) {
        return caption != null && caption.word() != null
                && caption.startTime() != null && caption.endTime() != null
                && Double.isFinite(caption.startTime()) && Double.isFinite(caption.endTime())
                && caption.startTime() >= 0 && caption.endTime() >= caption.startTime();
    }

    private static String captionedSpeechUrl(String baseUrl) {
        String root = baseUrl.replaceAll("/+$", "");
        if (root.endsWith("/v1")) {
            root = root.substring(0, root.length() - 3);
        }
        return root + "/dev/captioned_speech";
    }

    private static String basicAuth(TtsProperties properties) {
        String pair = properties.basicAuthUser() + ":" + properties.basicAuthPassword();
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private record KokoroRequest(String model, String input, String voice,
                                 @JsonProperty("response_format") String responseFormat, double speed,
                                 boolean stream, @JsonProperty("return_timestamps") boolean returnTimestamps) {
        private KokoroRequest(String model, String input, String voice, String responseFormat, double speed) {
            this(model, input, voice, responseFormat, speed, false, true);
        }
    }

    private record KokoroResponse(String audio, @JsonProperty("audio_format") String audioFormat,
                                  List<KokoroTimestamp> timestamps) {
    }

    private record KokoroTimestamp(String word, @JsonProperty("start_time") Double startTime,
                                   @JsonProperty("end_time") Double endTime) {
    }
}
