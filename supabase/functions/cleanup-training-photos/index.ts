const BUCKET = 'training-photos';
// Matches PostgREST's default max rows; a full page means more may be waiting.
const PAGE_SIZE = 1000;
const DELETE_BATCH_SIZE = 100;

type RetentionDependencies = {
  projectUrl: string;
  serviceKey: string;
  fetcher?: typeof fetch;
};

/**
 * Removes the training copies the database says must go
 * (training_photos_to_delete): v1.4 copies 90 days after upload, and any copy
 * whose analysis, consent, photo authorization or account no longer allows
 * keeping it. Permanent v1.5 copies that are still authorized are never listed.
 */
export async function deleteExpiredTrainingPhotos({ projectUrl, serviceKey, fetcher = fetch }: RetentionDependencies): Promise<number> {
  const headers = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, 'Content-Type': 'application/json' };
  const removed = new Set<string>();

  while (true) {
    const response = await fetcher(`${projectUrl}/rest/v1/rpc/training_photos_to_delete?limit=${PAGE_SIZE}`, {
      method: 'POST', headers, body: '{}',
    });
    if (!response.ok) throw new Error(`deletion listing failed (${response.status})`);
    // A one-column TABLE may come back as objects or as bare strings, depending on PostgREST.
    const rows = await response.json() as ({ object_name: string } | string)[];
    // Names already removed in this run mean storage did not drop them: stop instead of looping.
    const pending = rows.map(row => typeof row === 'string' ? row : row.object_name).filter(name => !removed.has(name));
    for (let index = 0; index < pending.length; index += DELETE_BATCH_SIZE) {
      const batch = pending.slice(index, index + DELETE_BATCH_SIZE);
      const removal = await fetcher(`${projectUrl}/storage/v1/object/${BUCKET}`, {
        method: 'DELETE', headers, body: JSON.stringify({ prefixes: batch }),
      });
      if (!removal.ok) throw new Error(`storage remove failed (${removal.status})`);
      batch.forEach(name => removed.add(name));
    }
    if (rows.length < PAGE_SIZE || pending.length === 0) break;
  }
  return removed.size;
}

export function makeRetentionHandler(dependencies: RetentionDependencies) {
  return async (request: Request): Promise<Response> => {
    if (request.method !== 'POST') return Response.json({ error: 'method_not_allowed' }, { status: 405 });
    try {
      const deleted = await deleteExpiredTrainingPhotos(dependencies);
      return Response.json({ deleted });
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
