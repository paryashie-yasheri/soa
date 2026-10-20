import { copyFileSync, mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontend = fileURLToPath(new URL('..', import.meta.url))
const swagger = dirname(createRequire(import.meta.url).resolve('swagger-ui-dist/package.json'))
const publicDir = join(frontend, 'public')
mkdirSync(join(publicDir, 'openapi'), { recursive: true })
mkdirSync(join(publicDir, 'docs'), { recursive: true })

for (const service of ['organization-service', 'orgdirectory-service']) {
  copyFileSync(join(frontend, '..', 'openapi', `${service}.yaml`), join(publicDir, 'openapi', `${service}.yaml`))
}
for (const asset of ['swagger-ui.css', 'swagger-ui-bundle.js', 'swagger-ui-standalone-preset.js', 'LICENSE', 'NOTICE']) {
  copyFileSync(join(swagger, asset), join(publicDir, 'docs', asset))
}
copyFileSync(join(frontend, 'docs', 'index.html'), join(publicDir, 'docs', 'index.html'))
