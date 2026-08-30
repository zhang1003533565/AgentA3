import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const src = path.resolve(__dirname, '../../mini_program_app/subpackage_learning/resourceGenerate/AIPresentationFlow.vue')
const dest = path.resolve(__dirname, '../src/components/ppt/PresentationFlow.vue')

let content = fs.readFileSync(src, 'utf8')
content = content.replace(/from '@\/api\/ppt\.js'/g, "from '../../api/ppt.js'")
content = content.replace(/<view/g, '<div')
content = content.replace(/<\/view>/g, '</div>')
content = content.replace(/<text/g, '<span')
content = content.replace(/<\/text>/g, '</span>')
content = content.replace(/<scroll-view/g, '<div')
content = content.replace(/<\/scroll-view>/g, '</div>')
content = content.replace(/<image/g, '<img')
content = content.replace(/\s+mode="aspectFill"/g, '')
content = content.replace(/\s+scroll-x/g, '')
content = content.replace(/\s+scroll-with-animation/g, '')
content = content.replace(/\s+:scroll-into-view="[^"]*"/g, '')
content = content.replace(/\s+:show-scrollbar="false"/g, '')
content = content.replace(/\s+show-scrollbar="false"/g, '')
content = content.replace(/@tap\.stop/g, '@click.stop')
content = content.replace(/@tap/g, '@click')
content = content.replace(/(\d+(?:\.\d+)?)rpx/g, (_, n) => `${Math.round(parseFloat(n) * 0.52 * 10) / 10}px`)
content = content.replace('<script>', "<script>\nimport '../../utils/uniShim.js'\n")

fs.mkdirSync(path.dirname(dest), { recursive: true })
fs.writeFileSync(dest, content)
console.log(`Wrote ${dest} (${content.split('\n').length} lines)`)
