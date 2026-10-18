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
  let linkFails = false;
  let revokeAfterLink = false;
  let role = 'owner';
  let targetRole = 'user';
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
        generateLink: (args: { type: string; email: string; options: { redirectTo: string } }) => {
          external.push(`link:${args.type}:${args.email}:${args.options.redirectTo}`);
          if (revokeAfterLink) authorized = false;
          return { data: { user: { id: 'invited' }, properties: { action_link: 'https://auth.example.test/invite' } }, error: linkFails ? { message: 'failed' } : null };
        },
        deleteUser: (id: string) => { external.push(`delete:${id}`); return { error: null }; },
      },
      resetPasswordForEmail: (email: string) => { external.push(`recovery:${email}`); return { error: null }; },
    },
    rpc: (_name: string, args: typeof calls[number]) => {
      calls.push(args);
      if (!authorized) return { data: null, error: { code: '42501', message: 'denied' } };
      if (args.operation === 'authorize' && (role === 'admin' && !['dashboard','photos','photo','review','audit'].includes(String(args.payload.operation)) ||
        (args.payload.id || args.payload.email && ['invite','invite_link'].includes(String(args.payload.operation))) &&
        (targetRole === 'owner' || role === 'superadmin' && targetRole === 'superadmin'))) {
        return { data: null, error: { code: 'LC403', message: 'Conta protegida' } };
      }
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
    failLink: () => { linkFails = true; }, revokeAfterLink: () => { revokeAfterLink = true; },
    asRole: (value: string, target = 'user') => { role = value; targetRole = target; },
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
  for (const op of ['dashboard', 'invite', 'invite_link', 'recovery', 'delete', 'photo']) assert((await f.request(op)).status === 403);
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
Deno.test('share invitation creates an access link without emailing or auditing credentials', async () => {
  const f = fixture();
  assert((await f.request('invite_link', { email: 'invalid' })).status === 400);
  assert(f.external.length === 0);
  const response = await f.request('invite_link', { email: ' new@test.local ' });
  assert(response.status === 200 && response.headers.get('Cache-Control') === 'no-store');
  const result = await response.json();
  assert(result.link === 'https://auth.example.test/invite' && Object.keys(result).length === 1);
  assert(f.external.join('|') === 'link:invite:new@test.local:leafcare://auth/reset-password');
  const event = f.calls.find(c => c.operation === 'record_event');
  assert(JSON.stringify(event?.payload) === JSON.stringify({ id: 'invited', action: 'invite' }));
});
Deno.test('failed invitation generation and revoked access never return an access link', async () => {
  const f = fixture(); f.failLink();
  const response = await f.request('invite_link', { email: 'new@test.local' });
  assert(response.status === 400 && !(await response.json()).link);
  assert(!f.calls.some(c => c.operation === 'record_event'));
  const revoked = fixture(); revoked.revokeAfterLink();
  const denied = await revoked.request('invite_link', { email: 'new@test.local' });
  assert(denied.status === 403 && !(await denied.json()).link);
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
Deno.test('reviewer cannot trigger privileged Auth or Storage effects but can consult photos', async () => {
  const f = fixture(); f.asRole('admin');
  for (const op of ['invite','invite_link','reinvite','recovery','delete','users','role','pause']) {
    const response = await f.request(op, { id: 'target', email: 'target@test.local' });
    assert(response.status === 403 && (await response.json()).code === 'operation_forbidden');
  }
  assert(f.external.length === 0);
  assert((await f.request('photos')).status === 200);
});
Deno.test('owner and peer protection is checked before external actions', async () => {
  for (const targetRole of ['owner','superadmin']) {
    const f = fixture(); f.asRole('superadmin', targetRole);
    for (const op of ['recovery','reinvite','delete']) assert((await f.request(op, { id: 'target' })).status === 403);
    assert(f.external.length === 0);
    assert(f.calls.every(c => c.operation === 'authorize' && c.payload.id === 'target'));
  }
});
Deno.test('authorization uses requested operation despite a forged payload operation', async () => {
  const f = fixture(); f.asRole('admin');
  assert((await f.request('invite', { email: 'target@test.local', operation: 'photos' })).status === 403);
  assert(f.external.length === 0 && f.calls[0].payload.operation === 'invite');
});
Deno.test('invitations cannot bypass protected target permissions using an email', async () => {
  for (const targetRole of ['owner','superadmin']) {
    const f = fixture(); f.asRole('superadmin', targetRole);
    for (const op of ['invite','invite_link']) assert((await f.request(op, { email: 'protected@test.local' })).status === 403);
    assert(f.external.length === 0);
  }
});
