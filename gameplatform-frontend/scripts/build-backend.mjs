import { spawnSync } from 'node:child_process'
import { cp, mkdir, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const frontend = fileURLToPath(new URL('../', import.meta.url))
const build = spawnSync(process.execPath, [path.join(frontend, 'node_modules/vite/bin/vite.js'), 'build'], {
  cwd: frontend, stdio: 'inherit'
})
if (build.status !== 0) process.exit(build.status || 1)
const target = path.resolve(frontend, '../gameplatform-backend/src/main/resources/static')
await mkdir(target, { recursive: true })
await rm(path.join(target, 'assets'), { recursive: true, force: true })
await cp(path.join(frontend, 'dist'), target, { recursive: true })
console.log('Frontend copied to backend src/main/resources/static')
