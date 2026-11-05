# know-commute

## Figure debug workbench

Run the backend with the builder-only viewer enabled:

```bash
READCAST_FIGURE_DEBUG_VIEWER_ENABLED=true ./mvnw spring-boot:test-run
```

Open `http://localhost:8020/debug/figures`. The viewer reads `target/figure-debug` directly and
supports both existing legacy traces and new runs with `run.json` decision/insertion metadata.

For a remote dev deployment, set `READCAST_FIGURE_DEBUG_DIR=/data/figure-debug`,
`READCAST_FIGURE_DEBUG_VIEWER_ENABLED=true`, and `READCAST_FIGURE_DEBUG_VIEWER_TOKEN` to a private
password. The browser will prompt for Basic authentication; any username works. The named
`readcast-figure-debug` volume persists traces across redeploys. Keep the viewer disabled in production.
