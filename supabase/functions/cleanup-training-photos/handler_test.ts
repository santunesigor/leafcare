import { makeRetentionHandler } from './index.ts';

Deno.test('retention removes only photos at least 90 days old', async () => {
  const deletedPaths: string[] = [];
  const now = Date.parse('2026-10-19T00:00:00Z');
  const fetcher: typeof fetch = async (input, init) => {
    const url = String(input);
    if (url.endsWith('/object/list/training-photos')) {
      const body = JSON.parse(String(init?.body)) as { prefix: string };
      return Response.json(body.prefix === ''
        ? [{ name: 'user-a', id: null }]
        : [
            { name: 'old.jpg', id: 'old-id', created_at: '2026-07-20T00:00:00Z' },
            { name: 'threshold.jpg', id: 'threshold-id', created_at: '2026-07-21T00:00:00Z' },
            { name: 'recent.jpg', id: 'new-id', created_at: '2026-07-22T00:00:00Z' },
          ]);
    }
    deletedPaths.push(...JSON.parse(String(init?.body)).prefixes);
    return Response.json({});
  };
  const handler = makeRetentionHandler({ projectUrl: 'https://unit.test', serviceKey: 'test-key', fetcher, now });
  const response = await handler(new Request('https://unit.test/cleanup', { method: 'POST' }));
  const body = await response.json();
  if (response.status !== 200 || body.deleted !== 2 || deletedPaths.join(',') !== 'user-a/old.jpg,user-a/threshold.jpg') {
    throw new Error(`Unexpected retention result: ${response.status} ${JSON.stringify(body)} ${deletedPaths}`);
  }
});

Deno.test('retention rejects methods other than POST', async () => {
  const handler = makeRetentionHandler({ projectUrl: 'https://unit.test', serviceKey: 'test-key' });
  const response = await handler(new Request('https://unit.test/cleanup', { method: 'GET' }));
  if (response.status !== 405) throw new Error(`Expected 405, got ${response.status}`);
});
