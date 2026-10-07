# Vue + Spring Boot

```
final/
├── backend/   Spring Boot 4.1 (Java 25, Maven) — http://localhost:8080
└── frontend/  Vue 3 + Vite + TypeScript + Router + Pinia + axios — http://localhost:5173
```

## 啟動

後端：
```
cd backend
./mvnw spring-boot:run
```

前端（另開一個終端機）：
```
cd frontend
npm run dev
```

開啟 http://localhost:5173。前端的 `/api/*` 請求會透過 Vite proxy 轉送到 `localhost:8080`（設定在 `frontend/vite.config.ts`），開發時不需處理 CORS。

範例 API：`GET /api/hello?name=xxx`（`backend/src/main/java/com/example/backend/controller/HelloController.java`）
