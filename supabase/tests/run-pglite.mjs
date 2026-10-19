import { PGlite } from '@electric-sql/pglite';
import { uuid_ossp } from '@electric-sql/pglite/contrib/uuid_ossp';
import { readFileSync, readdirSync } from 'node:fs';
const repo=process.argv[2];
if(!repo)throw new Error('Informe o caminho de supabase');
const read=p=>readFileSync(`${repo}/${p}`,'utf8');
const migrations=readdirSync(`${repo}/migrations`).sort();
async function setup(beforeHierarchy=false){
 const db=new PGlite({extensions:{uuid_ossp}});
 await db.exec(read('tests/bootstrap.sql'));
 for(const f of migrations){if(beforeHierarchy&&f.includes('admin_role_hierarchy'))break; await db.exec(read(`migrations/${f}`));}
 return db;
}
const db=await setup();
console.log((await db.query('select version()')).rows[0]);
for(const f of ['admin.sql','admin-hierarchy.sql','training-consent.sql','legal-acceptance.sql']){await db.exec(read(`tests/${f}`));console.log(`${f}: passed`);}
await db.exec("INSERT INTO auth.users(id,email,email_confirmed_at) VALUES('00000000-0000-0000-0000-000000000009','bootstrap@test.local',now())");
const bootstrap=read('scripts/bootstrap-superadmin.sql').replace(":'superadmin_email'","'bootstrap@test.local'");
await db.exec(bootstrap);await db.exec(bootstrap);
const result=await db.query("SELECT (SELECT count(*) FROM public.admin_audit WHERE action='bootstrap')=1 AND EXISTS(SELECT 1 FROM public.account_controls WHERE role='owner') AS ok");
if(!result.rows[0].ok)throw new Error('Bootstrap failed');
console.log('Bootstrap and idempotence: passed');await db.close();
for(const mode of ['bootstrap','single','ambiguous']){
 const upgrade=await setup(true);
 await upgrade.exec("INSERT INTO auth.users(id,email,email_confirmed_at) VALUES('00000000-0000-0000-0000-000000000001','first@test.local',now()); UPDATE public.account_controls SET role='superadmin'");
 if(mode!=='single')await upgrade.exec("INSERT INTO auth.users(id,email) VALUES('00000000-0000-0000-0000-000000000002','second@test.local'); UPDATE public.account_controls SET role='superadmin'");
 if(mode==='bootstrap')await upgrade.exec("INSERT INTO public.admin_audit(actor_id,target_id,action) VALUES('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','bootstrap')");
 try {await upgrade.exec(read('migrations/20261014120000_admin_role_hierarchy.sql'));if(mode==='ambiguous')throw new Error('Ambiguous migration accepted');}
 catch(e){if(mode!=='ambiguous'||!e.message.includes('Proprietário ambíguo'))throw e;await upgrade.exec('ROLLBACK');}
 const owners=await upgrade.query("SELECT user_id::text FROM public.account_controls WHERE role='owner'");
 if(mode==='ambiguous'?owners.rows.length!==0:owners.rows[0]?.user_id!=='00000000-0000-0000-0000-000000000001')throw new Error('Unexpected owner');
 console.log(`Migration ${mode}: passed`);await upgrade.close();
}
