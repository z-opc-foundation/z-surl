import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadConfigFromFile } from 'vite'

const root = path.dirname(fileURLToPath(import.meta.url))
const workspace = path.resolve(root, '../../..')
const matches = (key, url) => key.startsWith('^') ? new RegExp(key).test(url) : url.startsWith(key)

for (const local of [false, true]) {
    test(`actual Vite config preserves routing and aliases (LOCAL=${local})`, async () => {
        const previous = process.env.LOCAL_SIBLINGS
        let config
        try {
            process.env.LOCAL_SIBLINGS = local ? '1' : '0'
            config = (await loadConfigFromFile({ command: 'serve', mode: 'test' }, path.join(root, 'vite.config.js'), root)).config
        } finally {
            if (previous === undefined) delete process.env.LOCAL_SIBLINGS
            else process.env.LOCAL_SIBLINGS = previous
        }
        assert.equal(config.base, '/')
        const proxy = config.server.proxy
        assert.deepEqual(Object.keys(proxy), ['/api', '/s/'])
        const matching = url => Object.keys(proxy).filter(key => matches(key, url))
        for (const url of ['/api/ctc/surl/shorten', '/api/ctc/surl/info?mapKey=fixture', '/api/ctc/surl?mapKey=fixture', '/s/encoded%2Fkey']) {
            assert.equal(matching(url).length, 1, url)
        }
        for (const url of ['/', '/z-surl/home', '/z-surl/console', '/z-surl/docs', '/z-surl/login', '/src/main.tsx', '/src/api.ts', '/assets/app.js', '/icon.png', '/@vite/client', '/something']) {
            assert.equal(matching(url).length, 0, url)
        }
        for (const value of Object.values(proxy)) {
            assert.equal(value.target, 'http://localhost:8080')
            assert.equal(value.changeOrigin, true)
            assert.equal(value.rewrite, undefined)
        }
        assert.deepEqual(config.build.rollupOptions, {})
        assert.deepEqual(config.server.fs.allow, [local ? workspace : path.resolve(root, '..')])
        const aliases = config.resolve.alias
        assert.equal(aliases.find(a => a.find === '@').replacement, path.join(root, 'src'))
        assert.equal(aliases.find(a => a.find === '@fc').replacement, path.resolve(root, '../z-surl-component/src/index.ts'))
        const siblings = aliases.filter(a => a.find instanceof RegExp)
        assert.equal(siblings.length, local ? 4 : 0)
        if (local) {
            const expected = {
                '@yuku123/z-surl-component/pages': path.resolve(root, '../z-surl-component/src/pages.ts'),
                '@yuku123/z-surl-component': path.resolve(root, '../z-surl-component/src/index.ts'),
                '@yuku123/z-frontend-common/ui': path.join(workspace, 'z-frontend-common/src/components/ui/index.js'),
                '@yuku123/z-frontend-common': path.join(workspace, 'z-frontend-common/src/index.js'),
            }
            for (const [specifier, file] of Object.entries(expected)) {
                const hits = siblings.filter(a => a.find.test(specifier))
                assert.equal(hits.length, 1, specifier)
                assert.equal(hits[0].replacement, file)
                assert.ok(path.isAbsolute(file) && existsSync(file), file)
                assert.equal(hits[0].find.test(`${specifier}/unrelated`), false)
            }
        }
    })
}
