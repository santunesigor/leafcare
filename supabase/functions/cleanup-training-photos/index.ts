const BUCKET = 'training-photos';
const RETENTION_DAYS = 90;
const PAGE_SIZE = 1000;
const DELETE_BATCH_SIZE = 100;

type StorageEntry = { name: string; id?: string | null; created_at?: string | null };
type RetentionDependencies = {
  projectUrl: string;
  serviceKey: string;
  fetcher?: typeof fetch;
  now?: number;
};

export async function deleteExpiredTrainingPhotos({ projectUrl, serviceKey, fetcher = fetch, now = Date.now() }: RetentionDependencies): Promise<number> {
  const headers = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, 'Content-Type': 'application/json' };
  const cutoff = now - RETENTION_DAYS * 24 * 60 * 60 * 1000;
  const directories = [''];
  const expired: string[] = [];

  while (directories.length) {
    const prefix = directories.pop()!;
    for (let offset = 0; ; offset += PAGE_SIZE) {
      const response = await fetcher(`${projectUrl}/storage/v1/object/list/${BUCKET}`, {
        method: 'POST', headers,
        body: JSON.stringify({ prefix, limit: PAGE_SIZE, offset, sortBy: { column: 'created_at', order: 'asc' } }),
      });
      if (!response.ok) throw new Error(`storage list failed (${response.status})`);
      const entries = await response.json() as StorageEntry[];
      for (const entry of entries) {
        const path = prefix ? `${prefix}/${entry.name}` : entry.name;
        if (!entry.id) {
          directories.push(path);
        } else if (entry.created_at && Date.parse(entry.created_at) <= cutoff) {
          expired.push(path);
        }
      }
      if (entries.length < PAGE_SIZE) break;
    }
  }

  for (let index = 0; index < expired.length; index += DELETE_BATCH_SIZE) {
    const response = await fetcher(`${projectUrl}/storage/v1/object/${BUCKET}`, {
      method: 'DELETE', headers,
      body: JSON.stringify({ prefixes: expired.slice(index, index + DELETE_BATCH_SIZE) }),
    });
    if (!response.ok) throw new Error(`storage remove failed (${response.status})`);
  }
  return expired.length;
}

export function makeRetentionHandler(dependencies: RetentionDependencies) {
  return async (request: Request): Promise<Response> => {
    if (request.method !== 'POST') return Response.json({ error: 'method_not_allowed' }, { status: 405 });
    try {
      const deleted = await deleteExpiredTrainingPhotos(dependencies);
      return Response.json({ deleted, retention_days: RETENTION_DAYS });
    } catch (error) {
      console.error('Training photo retention failed', error instanceof Error ? error.message : 'unknown error');
      return Response.json({ error: 'retention_failed' }, { status: 503 });
    }
  };
}

if (import.meta.main) {
  const projectUrl = Deno.env.get('SUPABASE_URL');
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!projectUrl || !serviceKey) throw new Error('Supabase function environment is not configured');
  Deno.serve(makeRetentionHandler({ projectUrl, serviceKey }));
}
