import { makeHandler, type Dependencies } from './handler.ts';

function assert(value: unknown, message = 'Assertion failed'): asserts value {
  if (!value) throw new Error(message);
}
function fixture() {
  const calls: { operation: string; actor_id: string; payload: Record<string, unknown> }[] = [];
  const external: string[] = [];
  let authorized = true;
  let authenticated = true;
  let listingFails = false;
  let objects: { name: string }[] = [];
  let gone = false;
  const bucket = {
    download: (path: string) => { external.push(`download:${path}`); return { data: new Blob(['photo'], { type: 'image/jpeg' }), error: null }; },
    list: () => { external.push('list'); return { data: objects, error: listingFails ? { message: 'offline' } : null }; },
    remove: (paths: string[]) => { external.push(`remove:${paths.join(',')}`); objects = []; return { error: null }; },
  };
  const client = {
    auth: {
      getUser: () => ({ data: { user: authenticated ? { id: 'verified-user' } : null }, error: null }),
      admin: {
        inviteUserByEmail: (email: string) => { external.push(`invite:${email}`); return { data: { user: { id: 'invited' } }, error: null }; },
        deleteUser: (id: string) => { external.push(`delete:${id}`); return { error: null }; },
      },
      resetPasswordForEmail: (email: string) => { external.push(`recovery:${email}`); return { error: null }; },
    },
    rpc: (_name: string, args: typeof calls[number]) => {
      calls.push(args);
      if (!authorized) return { data: null, error: { code: '42501', message: 'denied' } };
      if (args.operation === 'review' && args.payload.version === 0) return { error: { code: '40001', message: 'Revisão mudou' } };
      const data = args.operation === 'photo' ? { object_path: 'owner/image.jpg' }
        : args.operation === 'email_target' ? { email: 'target@test.local', confirmed: true }
        : args.operation === 'delete_begin' ? { gone } : {};
      return { data, error: null };
    },
    storage: { from: () => bucket },
  };
  const handler = makeHandler({ client: client as unknown as Dependencies['client'] });
  const request = (operation: string, payload = {}, token = 'valid') => handler(new Request('http://local/admin', {
    method: 'POST', headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({ operation, payload, actor_id: 'forged-admin' }),
  }));
  return { calls, external, request, handler,
    deny: () => { authorized = false; }, logout: () => { authenticated = false; },
    failListing: () => { listingFails = true; },
    setObjects: (value: typeof objects) => { objects = value; },
    deleted: () => { gone = true; },
  };
}
Deno.test('missing/invalid session cannot invoke privileged RPC', async () => {
  const f = fixture();
  assert((await f.request('dashboard', {}, '')).status === 401);
  f.logout();
  assert((await f.request('dashboard')).status === 401);
  assert(f.calls.length === 0 && f.external.length === 0);
});
Deno.test('non-admin is denied before every service action', async () => {
  const f = fixture(); f.deny();
  for (const op of ['dashboard', 'invite', 'recovery', 'delete', 'photo']) assert((await f.request(op)).status === 403);
  assert(f.external.length === 0);
});
Deno.test('verified JWT supplies actor; forbidden operations cannot reach RPC', async () => {
  const f = fixture();
  assert((await f.request('dashboard')).status === 200);
  assert(f.calls.every(c => c.actor_id === 'verified-user'));
  assert((await f.request('record_event')).status === 400);
  assert((await f.request('delete_begin')).status === 400);
});
Deno.test('private image path comes from protected SQL; no-cache binary response', async () => {
  const f = fixture();
  const r = await f.request('photo', { id: 'id', object_path: 'other/private.jpg' });
  assert(r.status === 200 && await r.text() === 'photo');
  assert(r.headers.get('Cache-Control') === 'no-store');
  assert(f.external[0] === 'download:owner/image.jpg');
});
Deno.test('stale review returns conflict without external effects', async () => {
  const f = fixture(); assert((await f.request('review', { version: 0 })).status === 409);
  assert(f.external.length === 0);
});
Deno.test('invite validates email; recovery/reinvite resolved server-side and audited', async () => {
  const f = fixture(); assert((await f.request('invite', { email: 'invalid' })).status === 400);
  assert(f.external.length === 0);
  assert((await f.request('invite', { email: 'new@test.local' })).status === 200);
  assert((await f.request('recovery', { id: 'target', email: 'forged@test.local' })).status === 200);
  assert((await f.request('reinvite', { id: 'target' })).status === 200);
  assert(f.external.join('|') === 'invite:new@test.local|recovery:target@test.local|recovery:target@test.local');
  assert(f.calls.filter(c => c.operation === 'record_event').length === 3);
});
Deno.test('deletion batches objects before deleting auth; retries are idempotent', async () => {
  const f = fixture(); f.setObjects([{ name: 'a.jpg' }]);
  const first = await f.request('delete', { id: 'target', confirmation: 'target@test.local' });
  assert((await first.json()).pending === true);
  assert(!f.external.some(c => c.startsWith('delete:')));
  const second = await f.request('delete', { id: 'target', confirmation: 'target@test.local' });
  assert((await second.json()).pending === false);
  assert(f.external.includes('delete:target'));
  f.deleted(); const count = f.external.length;
  assert((await (await f.request('delete', { id: 'target' })).json()).pending === false);
  assert(f.external.length === count);
});
Deno.test('partial deletion stays resumable and logs failure', async () => {
  const f = fixture(); f.failListing();
  assert((await f.request('delete', { id: 'target' })).status === 503);
  assert(f.calls.some(c => c.operation === 'record_event' && c.payload.action === 'delete_failed'));
  assert(!f.external.some(c => c.startsWith('delete:')));
});
