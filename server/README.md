# J~Net App Store — Server

Self-hosted Node.js server that powers the **J~Net App Store**. It provides:

- a web **admin panel** to curate the store (add apps via a **GitHub link** or by
  **uploading an APK file**),
- a public **catalog API** consumed by the J~Net App Store Android app,
- hosting for uploaded APK files.

The Android app points at **`https://apps.jnetai.com`** by default (changeable in
the app's About screen).

## Port

The server listens on **port `8090`** by default (override with `PORT`).
Configure your reverse proxy / static domain **`apps.jnetai.com`** to forward to
`127.0.0.1:8090`.

## Run

```bash
cd server
npm install
PORT=8090 ADMIN_USER=admin ADMIN_PASSWORD='choose-a-strong-password' npm start
```

Then open `http://<host>:8090/` and log in with the admin credentials.

## API

| Method | Path                        | Auth  | Purpose                                   |
|--------|-----------------------------|-------|-------------------------------------------|
| GET    | `/api/health`               | no    | Liveness check                            |
| GET    | `/api/apps`                 | no    | Store catalog JSON (used by the app)      |
| GET    | `/apk/:file`                | no    | Download an uploaded APK                  |
| GET    | `/api/admin/apps`           | basic | List apps (raw, editable)                 |
| POST   | `/api/admin/apps`           | basic | Create app                                |
| PUT    | `/api/admin/apps/:id`       | basic | Update app                                |
| DELETE | `/api/admin/apps/:id`       | basic | Delete app                                |
| POST   | `/api/admin/resolve-github` | basic | Resolve GitHub link → tag + APK URL       |
| POST   | `/api/admin/upload`         | basic | Upload an APK (field name `apk`)          |

Catalog data is persisted in `data/store.json`; uploaded APKs live in `uploads/`.

## systemd (example)

```ini
[Unit]
Description=J~Net App Store server
After=network.target

[Service]
WorkingDirectory=/opt/jnet-app-store/server
Environment=PORT=8090
Environment=ADMIN_USER=admin
Environment=ADMIN_PASSWORD=change-me
ExecStart=/usr/bin/node server.js
Restart=always

[Install]
WantedBy=multi-user.target
```

## nginx reverse proxy (apps.jnetai.com → :8090)

```nginx
server {
    listen 443 ssl;
    server_name apps.jnetai.com;

    ssl_certificate     /etc/letsencrypt/live/apps.jnetai.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/apps.jnetai.com/privkey.pem;

    client_max_body_size 260m;   # allow large APK uploads

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Error handling & diagnostics

Every failure is logged with a stable error code (`ERR-*`) to stdout and to
`logs/store.log`, including request method, path, status and timing. These logs
are kept permanently so issues can be traced quickly.
