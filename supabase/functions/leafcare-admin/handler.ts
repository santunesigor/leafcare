import { createClient, type SupabaseClient } from 'npm:@supabase/supabase-js@2.57.4';

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export type Dependencies = { client: SupabaseClient;  };
const json = (data: unknown, status = 200) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
const externalActions = new Set(['invite', 'reinvite', 'recovery', 'delete']);
const rpcActions = new Set(['dashboard', 'users', 'photos', 'photo', 'audit', 'review', 'role', 'pause']);

export function makeHandler({ client }: Dependencies) {
  return async (request: Request): Promise<Response> => {
    try {
      if (request.method !== 'POST') throw new ApiError(405, 'Método não permitido');
      const token = request.headers.get('Authorization')?.match(/^Bearer (.+)$/i)?.[1];
      if (!token) throw new ApiError(401, 'Entre novamente na sua conta');
      const { data: { user }, error } = await client.auth.getUser(token);
      if (error || !user) throw new ApiError(401, 'Sessão inválida');
      const body = await request.json();
      const operation = body?.operation;
      const payload = body?.payload ?? {};
      if ((!externalActions.has(operation) && !rpcActions.has(operation)) || typeof payload !== 'object' || Array.isArray(payload)) {
        throw new ApiError(400, 'Operação inválida');
      }
      const command = async (op: string, data: Record<string, unknown> = {}) => {
        const result = await client.rpc('admin_command', { actor_id: user.id, operation: op, payload: data });
        if (result.error) {
          const status = result.error.code === '42501' ? 403 : result.error.code === '40001' ? 409 : 400;
          throw new ApiError(status, result.error.code === '42501' ? 'Acesso administrativo negado' : result.error.message);
        }
        return result.data;
      };
      // No action can use the service client before current-role verification.
      await command('authorize');
      if (operation === 'photo') {
        const row = await command('photo', { id: payload.id });
        if (!row.object_path) throw new ApiError(404, 'Foto indisponível');
        const { data, error: failure } = await client.storage.from('analysis-photos').download(row.object_path);
        if (failure || !data) throw new ApiError(404, 'Não foi possível carregar a foto');
        return new Response(data, { headers: { 'Content-Type': data.type || 'image/jpeg', 'Cache-Control': 'no-store' } });
      }
      if (rpcActions.has(operation)) return json(await command(operation, payload));
      if (operation === 'invite') {
        const email = String(payload.email ?? '').trim();
        if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new ApiError(400, 'E-mail inválido');
        const { data, error: failure } = await client.auth.admin.inviteUserByEmail(email, { redirectTo: 'leafcare://auth/reset-password' });
        if (failure) throw new ApiError(400, 'Não foi possível enviar o convite. Confira a conta e tente novamente.');
        await command('record_event', { id: data.user.id, action: 'invite' });
        return json({});
      }
      if (operation === 'reinvite' || operation === 'recovery') {
        const target = await command('email_target', { id: payload.id });
        if (!target.email) throw new ApiError(404, 'Conta indisponível');
        const result = operation === 'recovery' || target.confirmed
          ? await client.auth.resetPasswordForEmail(target.email, { redirectTo: 'leafcare://auth/reset-password' })
          : await client.auth.admin.inviteUserByEmail(target.email, { redirectTo: 'leafcare://auth/reset-password' });
        if (result.error) throw new ApiError(400, 'Não foi possível enviar o e-mail. Tente novamente.');
        await command('record_event', { id: payload.id, action: operation });
        return json({});
      }
      if (operation === 'delete') {
        const beginning = await command('delete_begin', payload);
        if (beginning.gone) return json({ pending: false });
        try {
          // Batch deletion is resumable; always list from offset zero after removing a batch.
          const bucket = client.storage.from('analysis-photos');
          const { data: objects, error: listingError } = await bucket.list(payload.id, { limit: 100, offset: 0 });
          if (listingError) throw listingError;
          if (objects?.length) {
            const { error: removeError } = await bucket.remove(objects.map(o => `${payload.id}/${o.name}`));
            if (removeError) throw removeError;
            return json({ pending: true });
          }
          // Recheck the caller before each destructive service operation.
          await command('authorize');
          const { error: deleteError } = await client.auth.admin.deleteUser(payload.id);
          if (deleteError) throw deleteError;
          await command('record_event', { id: payload.id, action: 'delete_complete' });
          return json({ pending: false });
        } catch {
          await command('record_event', { id: payload.id, action: 'delete_failed' });
          throw new ApiError(503, 'Exclusão parcial. A conta está marcada; use Retomar exclusão.');
        }
      }
      throw new ApiError(400, 'Operação inválida');
    } catch (error) {
      if (error instanceof ApiError) return json({ error: error.message }, error.status);
      return json({ error: 'Falha ao processar a operação. Tente novamente.' }, 500);
    }
  };
}

export function productionHandler() {
  const url = Deno.env.get('SUPABASE_URL');
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!url || !key) throw new Error('Backend administrativo não configurado');
  return makeHandler({ client: createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } }) });
}
