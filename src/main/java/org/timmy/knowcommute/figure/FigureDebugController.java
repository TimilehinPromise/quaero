package org.timmy.knowcommute.figure;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(prefix = "readcast.figures.debug", name = "viewer-enabled", havingValue = "true")
@RequestMapping("/debug/figures")
public class FigureDebugController {

    private final FigureDebugCatalog catalog;

    public FigureDebugController(FigureDebugCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<Resource> index() {
        return asset("debug-ui/figures/index.html", MediaType.TEXT_HTML);
    }

    @GetMapping("/app.js")
    public ResponseEntity<Resource> javascript() {
        return asset("debug-ui/figures/app.js", MediaType.parseMediaType("text/javascript"));
    }

    @GetMapping("/styles.css")
    public ResponseEntity<Resource> styles() {
        return asset("debug-ui/figures/styles.css", MediaType.parseMediaType("text/css"));
    }

    @GetMapping("/api/runs")
    public List<FigureDebugRunSummary> runs() {
        return catalog.listRuns();
    }

    @GetMapping("/api/runs/{id}")
    public FigureDebugRunDetail run(@PathVariable String id) {
        return catalog.findRun(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Figure debug run not found"));
    }

    @GetMapping("/api/runs/{id}/figures/{ordinal}/image")
    public ResponseEntity<Resource> image(@PathVariable String id, @PathVariable int ordinal) {
        FigureDebugImage image = catalog.findImage(id, ordinal).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Figure debug image not found"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType(image.mediaType()))
                .body(new FileSystemResource(image.path()));
    }

    private static ResponseEntity<Resource> asset(String path, MediaType mediaType) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .body(new ClassPathResource(path));
    }

    private static MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
