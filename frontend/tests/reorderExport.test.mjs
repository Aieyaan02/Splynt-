import { test } from 'node:test';
import assert from 'node:assert/strict';
import { reorderCsv } from '../src/lib/reorderExport.ts';
const product = { storeId: 1, active: true, stockKnown: true, quantity: 1.25, lowStock: true, suggestedReorderQuantity: 4.75, name: 'Milk, "fresh"', barcode: '001234', reorderLevel: 2, targetStock: 6, updatedAt: '2026-10-12T12:00:00Z' };
test('exports only actionable items from the selected store', () => {
 const csv = reorderCsv([product, {...product, storeId: 2}, {...product, active: false}, {...product, stockKnown: false}, {...product, quantity: null}, {...product, lowStock: false}], 1, 'Shop');
 assert.equal(csv.split('\r\n').length, 3);
 assert.ok(csv.includes('"Milk, ""fresh"""'));
 assert.ok(csv.includes('"1.25","2","6","4.75"'));
});
test('neutralizes formulas and preserves legitimate negative numeric stock', () => {
 const csv = reorderCsv([{...product, name: '  =HYPERLINK("evil")', barcode: '+123', quantity: -2}], 1, '@Shop');
 assert.ok(csv.includes('"\'@Shop"'));
 assert.ok(csv.includes('"\'  =HYPERLINK'));
 assert.ok(csv.includes('"\'+123"'));
 assert.ok(csv.includes('"-2"'));
});
