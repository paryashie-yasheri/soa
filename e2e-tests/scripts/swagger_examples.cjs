// Read-only browser check: opens request editors without clicking Execute.
// Install playwright and Chromium, then: node e2e-tests/scripts/swagger_examples.cjs
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ ignoreHTTPSErrors: true });
    await page.goto(process.env.SOA_SWAGGER_URL || 'https://localhost:61811/ui/');
    for (const [id, root, fields] of [
      ['operations-Organizations-createOrganization', 'organization', ['name', 'coordinates', 'type', 'postalAddress']],
      ['operations-Organizations-updateOrganization', 'organization', ['name', 'coordinates', 'type', 'postalAddress']],
      ['operations-Employees-addOrganizationEmployee', 'employee', ['name']],
    ]) {
      const operation = page.locator('#' + id);
      await operation.locator('.opblock-summary').click();
      await operation.getByRole('button', { name: 'Try it out', exact: true }).click();
      const xml = await operation.locator('textarea').inputValue();
      const parsed = await page.evaluate(value => {
        const doc = new DOMParser().parseFromString(value, 'application/xml');
        return { root: doc.documentElement.tagName,
          error: !!doc.querySelector('parsererror'),
          children: [...doc.documentElement.children].map(child => child.tagName) };
      }, xml);
      assert.equal(parsed.error, false, xml);
      assert.equal(parsed.root, root, xml);
      for (const field of fields) assert.ok(parsed.children.includes(field), xml);
      assert.ok(!xml.includes('&lt;?xml'), xml);
      assert.ok(!xml.includes('&lt;' + root), xml);
      console.log('PASS rendered XML request editor:', id);
    }
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
