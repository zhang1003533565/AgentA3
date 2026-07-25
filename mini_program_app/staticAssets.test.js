const assert = require('node:assert/strict')
const { existsSync, readFileSync, readdirSync } = require('node:fs')
const { extname, join, relative } = require('node:path')
const test = require('node:test')

const ROOT = __dirname
const SOURCE_EXTENSIONS = new Set(['.css', '.html', '.js', '.json', '.scss', '.ts', '.vue'])
const SKIPPED_DIRECTORIES = new Set(['.git', 'node_modules', 'static', 'unpackage'])

function sourceFiles(directory) {
  const files = []
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!SKIPPED_DIRECTORIES.has(entry.name)) {
        files.push(...sourceFiles(join(directory, entry.name)))
      }
      continue
    }
    if (SOURCE_EXTENSIONS.has(extname(entry.name))) {
      files.push(join(directory, entry.name))
    }
  }
  return files
}

test('every literal /static reference resolves to a repository asset', () => {
  const missing = []
  const referencePattern = /(["'`])\/static\/([^"'`?#]+)\1/g

  for (const file of sourceFiles(ROOT)) {
    const source = readFileSync(file, 'utf8')
    for (const match of source.matchAll(referencePattern)) {
      if (match[2].includes('${')) continue
      const logicalPath = `/static/${match[2]}`
      const assetPath = join(ROOT, decodeURIComponent(logicalPath.slice(1)))
      if (!existsSync(assetPath)) {
        missing.push(`${relative(ROOT, file)} -> ${logicalPath}`)
      }
    }
  }

  assert.deepEqual(missing.sort(), [])
})
