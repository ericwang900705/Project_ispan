import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const vite = fileURLToPath(new URL('../node_modules/vite/bin/vite.js', import.meta.url))
const children = []
let stopping = false
function stop(code = 0) {
  if (stopping) return
  stopping = true
  process.exitCode = code
  for (const child of children) {
    if (child.exitCode === null) child.kill('SIGTERM')
  }
}
for (const mode of ['member', 'staff']) {
  const child = spawn(
    process.execPath,
    [vite, '--host', '127.0.0.1', '--mode', mode],
    { stdio: 'inherit' }
  )
  children.push(child)
  child.on('error', error => {
    console.error(error.message);
    stop(1)
  })
  child.on('exit', (code, signal) => {
    if (!stopping) stop(code ?? (signal ? 1 : 0))
  })
}
process.on('SIGINT', () => stop())
process.on('SIGTERM', () => stop())
