# Deploying

The API runs on **Render** (Docker, free tier) and the web app on **Vercel** (static, free
Hobby tier). In production the browser calls the API directly: there is no nginx proxy as in
`docker compose`, so CORS on the API must allow the Vercel origin, and the web build must know
the API's URL.

```
browser ──▶ https://<project>.vercel.app          (static files, Vercel)
   └──────▶ https://<service>.onrender.com/api/…   (Spring Boot in Docker, Render)
```

Do Render first (you need its URL for Vercel), then Vercel, then go back to Render once to set
the CORS origin. Dashboard labels change from time to time; each step says what the button does
so it's easy to find if it has been renamed.

## Environment variables

| Where | Variable | Value | When it takes effect |
| --- | --- | --- | --- |
| Render (API) | `APP_CORS_ALLOWED_ORIGINS` | Your Vercel URL exactly, e.g. `https://work-request-service.vercel.app` (no trailing slash; several origins comma-separated; **no wildcards**, the app refuses to start with one) | On the next deploy or restart |
| Render (API) | `PORT` | `8080` (already set by `render.yaml`) | |
| Vercel (web) | `VITE_API_URL` | Your Render URL, e.g. `https://work-request-service-api.onrender.com` (https, no trailing slash) | **At build time only**: redeploy after changing it |

Nothing secret is needed: the service has no database, no keys and no auth. Optional API
settings, all with defaults in `backend/src/main/resources/application.yml`:
`APP_BATCH_MAX_SIZE` (5000 requests), `APP_REQUEST_MAX_BODY_SIZE` (2MB).

## 1. Render: the API

1. Make sure `main` (with `render.yaml` at the repo root) is pushed to GitHub.
2. Sign in at <https://dashboard.render.com> and connect your GitHub account if asked.
3. Click **New +** → **Blueprint** (deploys everything described in a `render.yaml`).
4. Pick the `Work-Request-Service` repository. Render reads `render.yaml` and lists one web
   service, `work-request-service-api`, on the free plan.
5. It asks for the value of `APP_CORS_ALLOWED_ORIGINS` (marked `sync: false`). You don't know
   the Vercel URL yet: enter `http://localhost:5173` for now. You'll change it in step 3.
6. Click **Apply** (sometimes labelled **Deploy Blueprint**). The first build compiles the
   backend inside Docker and takes several minutes; follow it under **Logs**.
7. When the service shows **Live**, copy its URL from the top of the service page, e.g.
   `https://work-request-service-api.onrender.com`. Render may add a suffix if the name is taken.
8. Check it:

   ```bash
   API=https://work-request-service-api.onrender.com
   curl -s $API/actuator/health                       # {"status":"UP",...}
   curl -s $API/api/work-requests/sample | head -c 200 # the 13-request sample
   ```

Render uses `/actuator/health` (from `render.yaml`) to decide when a deploy is healthy, and
redeploys automatically on every push to `main` (`autoDeploy: true`).

## 2. Vercel: the web app

1. Sign in at <https://vercel.com> with GitHub.
2. Click **Add New…** → **Project**, then **Import** next to `Work-Request-Service`.
3. On the configure screen:
   - **Root Directory**: click **Edit** and choose `web`. This matters; the repo root has no web app.
   - **Framework Preset**: Vite (detected). Build, output and install commands come from
     `web/vercel.json` (`npm run build`, `dist`, `npm ci`); leave the overrides off.
   - **Environment Variables**: add `VITE_API_URL` = your Render URL from step 1.7.
4. Click **Deploy**. If `VITE_API_URL` is missing or isn't an `https://` URL, the build fails on
   purpose with `VITE_API_URL must be the deployed API's https URL on Vercel`: add or fix the
   variable under **Settings → Environment Variables** and click **Redeploy**.
5. Copy the production URL (**Domains** on the project page), e.g.
   `https://work-request-service.vercel.app`.

## 3. Render again: allow the Vercel origin

1. In the Render service, open **Environment**.
2. Set `APP_CORS_ALLOWED_ORIGINS` to the Vercel URL from step 2.5, exactly as the browser shows
   it (`https://…vercel.app`, no trailing slash). To keep local development working too:
   `https://work-request-service.vercel.app,http://localhost:5173`.
3. Click **Save Changes** (choose **Save and deploy** if offered). The service restarts with the
   new value.

## 4. Verify

```bash
API=https://work-request-service-api.onrender.com
WEB=https://work-request-service.vercel.app

# The API answers the browser's CORS preflight for the Vercel origin (expect 200 and the origin echoed):
curl -s -o /dev/null -w "%{http_code}\n" -X OPTIONS "$API/api/work-requests/batch" \
  -H "Origin: $WEB" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: Content-Type"
curl -s -D - -o /dev/null -X OPTIONS "$API/api/work-requests/batch" \
  -H "Origin: $WEB" -H "Access-Control-Request-Method: POST" | grep -i access-control-allow-origin

# A batch round trip (expect 6 accepted requests):
curl -s $API/api/work-requests/sample | curl -s -X POST -H "Content-Type: application/json" \
  --data-binary @- $API/api/work-requests/batch | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["accepted"]))'

# The web app is served (expect 200), including deep links:
curl -s -o /dev/null -w "%{http_code}\n" $WEB/
curl -s -o /dev/null -w "%{http_code}\n" $WEB/anything
```

Then in a private browser window: open the Vercel URL, click **Load sample** and **Run all**,
and expect 6 requests in the queue with REQ-101 tagged "escalated from Medium"; then
**Shuffle & re-run** shows "Output identical". The **Agent runs** tab works without the API.

## Free-tier behaviour

- **Render free web services spin down after about 15 minutes without traffic.** The next
  request starts the container again, which can take up to a minute or so (a Docker start plus
  Spring Boot on a small CPU). The request waits rather than failing, but the UI looks stuck
  meanwhile. Before a demo, open `$API/actuator/health` and wait for `UP`.
- Free instances have 512 MB of memory; the container sizes its heap from that limit.
- Free-tier hours and limits change; check Render's current free plan if the service stops
  starting.
- Vercel serves the static site from its CDN: no cold start.

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| UI says "API not reachable — is the backend running?" | Cold start in progress, or CORS blocked (the browser reports a blocked request as a network error) | Wait for `/actuator/health`; check the browser console for a CORS error; make `APP_CORS_ALLOWED_ORIGINS` match the Vercel URL exactly |
| Works on the production URL but not on a preview deployment | Preview URLs are different origins, and wildcards aren't allowed | Add the preview origin to `APP_CORS_ALLOWED_ORIGINS`, or test on production |
| Vercel build fails with `VITE_API_URL must be …` | Variable missing, or not https | Set it and redeploy |
| Changed `VITE_API_URL` but the site still calls the old URL | It is baked in at build time | Redeploy on Vercel |
| Render build fails on `RUN --mount=type=cache` | The builder lacks BuildKit cache mounts (Render's does at the time of writing) | Remove `--mount=type=cache,target=/root/.m2` from both `RUN` lines in `backend/Dockerfile`; builds get slower but work |
| Render deploy never becomes healthy | Startup failed: often a bad `APP_CORS_ALLOWED_ORIGINS` (a `*` is refused) | Read the service **Logs**; fix the variable |
