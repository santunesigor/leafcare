import { makeRetentionHandler } from './index.ts';

type Call = { url: string; method: string; body: string };

function fakeBackend(pages: string[][], { removeFails = false, scalarRows = false } = {}) {
  const calls: Call[] = [];
  const fetcher: typeof fetch = async (input, init) => {
    const call = { url: String(input), method: init?.method ?? 'GET', body: String(init?.body ?? '') };
    calls.push(call);
    if (call.url.includes('/rest/v1/rpc/training_photos_to_delete')) {
      const names = pages.shift() ?? [];
      return Response.json(scalarRows ? names : names.map(object_name => ({ object_name })));
    }
    return removeFails ? new Response('fail', { status: 500 }) : Response.json({});
  };
  return { calls, fetcher };
}

const run = async (fetcher: typeof fetch) => {
  const handler = makeRetentionHandler({ projectUrl: 'https://unit.test', serviceKey: 'test-key', fetcher });
  const response = await handler(new Request('https://unit.test/cleanup', { method: 'POST' }));
  return { status: response.status, body: await response.json() };
};

Deno.test('retention removes exactly the copies the database lists for deletion', async () => {
  const { calls, fetcher } = fakeBackend([['user-a/old.jpg', 'user-b/v14.jpg']]);
  const { status, body } = await run(fetcher);
  const removals = calls.filter(call => call.method === 'DELETE');
  if (status !== 200 || body.deleted !== 2 || removals.length !== 1 ||
    !removals[0].url.endsWith('/storage/v1/object/training-photos') ||
    JSON.parse(removals[0].body).prefixes.join(',') !== 'user-a/old.jpg,user-b/v14.jpg') {
    throw new Error(`Unexpected retention result: ${status} ${JSON.stringify(body)} ${JSON.stringify(calls)}`);
  }
  const listing = calls[0];
  if (listing.method !== 'POST' || !listing.url.startsWith('https://unit.test/rest/v1/rpc/training_photos_to_delete')) {
    throw new Error(`Copies to delete must come from the database: ${JSON.stringify(listing)}`);
  }
});

Deno.test('retention accepts names returned as plain strings', async () => {
  const { calls, fetcher } = fakeBackend([['user-a/old.jpg']], { scalarRows: true });
  const { status, body } = await run(fetcher);
  const removal = calls.find(call => call.method === 'DELETE');
  if (status !== 200 || body.deleted !== 1 || JSON.parse(removal?.body ?? '{}').prefixes?.join(',') !== 'user-a/old.jpg') {
    throw new Error(`Unexpected retention result: ${status} ${JSON.stringify(body)}`);
  }
});

Deno.test('retention deletes nothing when no copy must go', async () => {
  const { calls, fetcher } = fakeBackend([[]]);
  const { status, body } = await run(fetcher);
  if (status !== 200 || body.deleted !== 0 || calls.some(call => call.method === 'DELETE')) {
    throw new Error(`Unexpected retention result: ${status} ${JSON.stringify(body)}`);
  }
});

Deno.test('retention keeps reading full pages and stops on names already removed', async () => {
  const full = Array.from({ length: 1000 }, (_, index) => `user-a/${index}.jpg`);
  const { calls, fetcher } = fakeBackend([full, full]);
  const { status, body } = await run(fetcher);
  const listings = calls.filter(call => call.url.includes('/rpc/'));
  const removals = calls.filter(call => call.method === 'DELETE');
  if (status !== 200 || body.deleted !== 1000 || listings.length !== 2 || removals.length !== 10) {
    throw new Error(`Unexpected paging: ${status} ${JSON.stringify(body)} ${listings.length} ${removals.length}`);
  }
});

Deno.test('retention reports a storage failure', async () => {
  const { fetcher } = fakeBackend([['user-a/old.jpg']], { removeFails: true });
  const { status } = await run(fetcher);
  if (status !== 503) throw new Error(`Expected 503, got ${status}`);
});

Deno.test('retention rejects methods other than POST', async () => {
  const handler = makeRetentionHandler({ projectUrl: 'https://unit.test', serviceKey: 'test-key' });
  const response = await handler(new Request('https://unit.test/cleanup', { method: 'GET' }));
  if (response.status !== 405) throw new Error(`Expected 405, got ${response.status}`);
});
