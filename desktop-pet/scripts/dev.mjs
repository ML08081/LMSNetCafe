import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const projectDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageDir = (name) => path.dirname(require.resolve(`${name}/package.json`))
const viteCli = path.join(packageDir('vite'), 'bin', 'vite.js')
const tscCli = path.join(packageDir('typescript'), 'bin', 'tsc')
const electronPath = require('electron')
const children = []

function launch(command, args, env = process.env) {
  const child = spawn(command, args, { cwd: projectDir, env, stdio: 'inherit' })
  children.push(child)
  return child
}

launch(process.execPath, [tscCli, '-p', 'tsconfig.electron.json', '--watch'])
launch(process.execPath, [viteCli, '--host', '127.0.0.1', '--port', '5174'])

for (let attempt = 0; attempt < 100; attempt += 1) {
  const compiled = fs.existsSync(path.join(projectDir, 'dist-electron', 'main.js'))
  try {
    if (compiled && (await fetch('http://127.0.0.1:5174')).ok) break
  } catch {}
  await new Promise((resolve) => setTimeout(resolve, 200))
}

const electron = launch(electronPath, ['.'], { ...process.env, VITE_DEV_SERVER_URL: 'http://127.0.0.1:5174' })
electron.on('exit', () => process.exit(0))

function cleanup() { children.forEach((child) => child.kill()) }
process.on('SIGINT', cleanup)
process.on('SIGTERM', cleanup)
process.on('exit', cleanup)
