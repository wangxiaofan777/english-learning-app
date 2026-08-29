// vite 环境变量类型（构建时注入，VITE_ 前缀）
interface ImportMetaEnv {
  /** 小程序/App 端后端地址，如 https://api.example.com；H5 端不使用（同源反代） */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
