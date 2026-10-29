/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend base URL. Empty string means same origin (the nginx container proxies /api). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
