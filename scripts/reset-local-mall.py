"""Reset only this project's local databases after an on-disk backup. Explicit --apply required."""
import argparse, datetime, hashlib, json, pathlib, subprocess

ROOT=pathlib.Path(__file__).resolve().parents[1]
DATABASES=['saas_tenant','saas_iam','saas_merchant','saas_member','saas_trade','saas_analytics']
MYSQL='smart-merchant-saas-mysql-1'
REDIS='smart-merchant-saas-redis-1'
def run(args, **kwargs):return subprocess.run(args,check=True,**kwargs)
parser=argparse.ArgumentParser();parser.add_argument('--apply',action='store_true');args=parser.parse_args()
if not args.apply:raise SystemExit('Explicit --apply required. Stop project backend first.')
for name in [MYSQL,REDIS]:
    details=json.loads(subprocess.check_output(['docker','inspect',name]))[0]
    assert details['Config']['Labels']['com.docker.compose.project']=='smart-merchant-saas'
details=json.loads(subprocess.check_output(['docker','inspect',MYSQL]))[0]
assert any(p['HostPort']=='13306' for p in details['NetworkSettings']['Ports']['3306/tcp'])
backup=ROOT/'runtime-logs'/'backups'/('before-mall-reset-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
backup.mkdir(parents=True)
dump=backup/'databases.sql'
with dump.open('wb') as output:
    run(['docker','exec',MYSQL,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF --databases '+' '.join(DATABASES)],stdout=output)
data=dump.read_bytes()
assert len(data)>10000 and b'Dump completed' in data
for name in DATABASES:assert f'USE `{name}`'.encode() in data
run(['docker','exec',REDIS,'redis-cli','SAVE'],stdout=subprocess.DEVNULL)
run(['docker','cp',REDIS+':/data/dump.rdb',str(backup/'redis.rdb')],stdout=subprocess.DEVNULL)
manifest={'databases':DATABASES,'mysqlContainer':MYSQL,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'redisBytes':(backup/'redis.rdb').stat().st_size}
(backup/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
sql=''.join(f'DROP DATABASE `{name}`; CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' for name in DATABASES)
run(['docker','exec','-i',MYSQL,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=sql.encode())
keys=subprocess.check_output(['docker','exec',REDIS,'redis-cli','--scan','--pattern','saas:*']).decode().splitlines()
for start in range(0,len(keys),100):run(['docker','exec',REDIS,'redis-cli','UNLINK',*keys[start:start+100]],stdout=subprocess.DEVNULL)
print(json.dumps({'backup':str(backup),'databaseCount':len(DATABASES),'removedProjectCacheKeys':len(keys)},ensure_ascii=False))
