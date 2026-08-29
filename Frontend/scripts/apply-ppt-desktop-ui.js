import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const flowPath = path.resolve(__dirname, '../src/components/ppt/PresentationFlow.vue')
const templatePath = path.resolve(__dirname, '../src/components/ppt/PresentationFlow.desktop.template.vue')
const stylePath = path.resolve(__dirname, '../src/components/ppt/PresentationFlow.desktop.css')

const source = fs.readFileSync(flowPath, 'utf8')
const scriptMatch = source.match(/<script>[\s\S]*<\/script>/)
if (!scriptMatch) throw new Error('script block not found')

const template = fs.readFileSync(templatePath, 'utf8').trim()
const styles = fs.readFileSync(stylePath, 'utf8').trim()

const patchedScript = scriptMatch[0].replace(
  'updateOutlineItemLevel(index, event) {\n      const selected = this.outlineLevels[Number(event?.detail?.value || 0)] || this.outlineLevels[1]',
  'updateOutlineItemLevel(index, event) {\n      const raw = event?.target?.value ?? event?.detail?.value\n      const selected = this.outlineLevels[Number(raw || 0)] || this.outlineLevels[1]',
)

const next = `${template}\n\n${patchedScript}\n\n<style scoped>\n${styles}\n</style>\n`
fs.writeFileSync(flowPath, next)
console.log('Applied desktop UI to PresentationFlow.vue')
