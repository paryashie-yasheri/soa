import { readFileSync, writeFileSync } from 'node:fs'
import { render } from '../.prerender/entry-server.js'

const index = new URL('../dist/index.html', import.meta.url)
const placeholder = '<div id="root"></div>'
const template = readFileSync(index, 'utf8')
if (!template.includes(placeholder)) throw new Error('Vite HTML root placeholder is missing')
writeFileSync(index, template.replace(placeholder, `<div id="root">${render()}</div>`))
console.log('Prerendered the app shell into dist/index.html')
