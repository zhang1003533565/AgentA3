import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const projectRoot = new URL('../', import.meta.url)

test('React 19 下在应用入口加载 antd v5 官方兼容补丁', async () => {
  const packageJson = JSON.parse(await readFile(new URL('package.json', projectRoot), 'utf8'))
  const entry = await readFile(new URL('src/main.jsx', projectRoot), 'utf8')

  assert.ok(packageJson.dependencies['@ant-design/v5-patch-for-react-19'])
  assert.match(entry, /^import '@ant-design\/v5-patch-for-react-19'/)
})
