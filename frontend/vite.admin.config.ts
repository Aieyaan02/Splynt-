import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
export default defineConfig({
 root:'admin',plugins:[react()],publicDir:'../public',
 build:{outDir:'../dist-admin',emptyOutDir:true},
 server:{host:'127.0.0.1',port:5174,strictPort:true,proxy:{'/api':{target:process.env.SPLYNT_API_PROXY ?? 'http://127.0.0.1:8087',changeOrigin:true}}}
});
