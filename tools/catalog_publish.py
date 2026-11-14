#!/usr/bin/env python3
"""One command: inspect four local APKs, git-publish immutable blobs, sign a proposal.

Requires an authenticated, clean checkout of zoniksr/behost-apks and an external
RSA3072 signing key. No secret is sent to GitHub. The workflow does promotion.
"""
from __future__ import annotations
import argparse, json, os, pathlib, re, sys, tempfile
import apk_catalog as catalog
from catalog_ci import fingerprint

def git(repo,*args,input_bytes=None,env=None):
    return catalog.command(['git','-c','credential.interactive=never','-C',repo,*args],input_bytes=input_bytes,env=env).strip()
def repo_check(repo,key):
    repo=pathlib.Path(repo).resolve();key=pathlib.Path(key).resolve()
    catalog.require(repo.is_dir() and key.is_file(),'Repository and signing key must exist')
    catalog.require(not key.is_relative_to(repo),'Signing key must remain outside the public repository')
    root=pathlib.Path(git(repo,'rev-parse','--show-toplevel')).resolve();catalog.require(root==repo,'Use repository root')
    origin=git(repo,'remote','get-url','origin')
    catalog.require(origin in ('https://github.com/zoniksr/behost-apks.git','https://github.com/zoniksr/behost-apks','git@github.com:zoniksr/behost-apks.git','ssh://git@github.com/zoniksr/behost-apks.git'),'Origin must be the approved repository without embedded credentials')
    catalog.require(not git(repo,'status','--porcelain'),'Repository must be clean; publisher never overwrites local work')
    catalog.require(git(repo,'rev-parse','--show-object-format')=='sha1','GitHub SHA1 repository required')
    git(repo,'var','GIT_AUTHOR_IDENT');git(repo,'var','GIT_COMMITTER_IDENT')
    return repo,key
def fetch_main(repo):
    git(repo,'fetch','origin','refs/heads/main');return git(repo,'rev-parse','FETCH_HEAD')
def git_commit(repo,parent,files,message):
    """Write a new tree with a temporary index; no checkout/staging mutations."""
    with tempfile.TemporaryDirectory(prefix='be-catalog-git-') as tmp:
        env=os.environ.copy();env['GIT_INDEX_FILE']=str(pathlib.Path(tmp)/'index')
        git(repo,'read-tree',parent,env=env)
        for name,data in files.items():
            catalog.require(re.fullmatch('[A-Za-z0-9._/-]+',name) and '..' not in name,'Unsafe destination path')
            blob=git(repo,'hash-object','-w','--stdin',input_bytes=data,env=env)
            git(repo,'update-index','--add','--cacheinfo','100644',blob,name,env=env)
        tree=git(repo,'write-tree',env=env)
        return git(repo,'commit-tree',tree,'-p',parent,input_bytes=(message+'\n').encode(),env=env)
def already_published(current,channel,apps,backend):
    if not current:return False
    old=current['channels'][channel]
    return old['status']=='active' and old['min_backend_version']==backend and {a['role']:fingerprint(a) for a in old['apps']}=={a['role']:fingerprint(a) for a in apps}
def publish(args):
    repo,key=repo_check(args.repo,args.key);files={}
    for item in args.apk:
        role,separator,path=item.partition('=');catalog.require(separator and role in catalog.ROLES and role not in files,'Use exactly four distinct --apk ROLE=PATH options');files[role]=pathlib.Path(path).resolve()
    catalog.require(set(files)==set(catalog.ROLES),'Exactly four role APKs are required')
    current=catalog.read_current(None,args.php)
    apps=[];local=[]
    for role in catalog.ROLES:
        path=files[role]
        metadata,evidence=catalog.inspect_apk(path,args.sdk,args.java)
        # Stable unique names permit four local files all named app-debug.apk,
        # and make retry paths independent of the operator's local filenames.
        filename=f"RestauranteBe_{role}_{args.channel}_code{metadata['version_code']}.apk"
        apps.append(dict(metadata,role=role,file=filename,url='https://raw.githubusercontent.com/'+catalog.REPOSITORY+'/'+'0'*40+'/restaurante-be/validation/'+filename));local.append(dict(evidence,role=role,**metadata))
    provisional=json.loads(json.dumps(current));provisional['sequence']+=1
    provisional['channels'][args.channel]={'status':'active','version':apps[0]['version_name'],'version_code':apps[0]['version_code'],'min_backend_version':args.backend_version,'production_tested':False,'apps':apps}
    catalog.validate(provisional);catalog.monotonic(provisional,current)
    if already_published(current,args.channel,apps,args.backend_version):
        return {'passed':True,'action':'already-current','sequence':current['sequence'],'apps_verified_locally':4,'local_sdk_evidence':local}
    set_id=catalog.digest(catalog.canonical({a['role']:fingerprint(a) for a in apps}))
    branch='artifacts/restaurante-be/'+args.channel+'/'+set_id[:24]
    existing=git(repo,'ls-remote','--heads','origin','refs/heads/'+branch).split()
    if existing:
        commit=existing[0];catalog.require(re.fullmatch('[a-f0-9]{40}',commit),'Invalid artifact commit')
    else:
        head=fetch_main(repo);entries={}
        for app in apps:
            path=f"restaurante-be/releases/{args.channel}/{app['version_code']}-{set_id[:16]}/{app['file']}"
            data=files[app['role']].read_bytes();catalog.require(catalog.digest(data)==app['sha256'] and len(data)==app['bytes'],'Local APK changed after SDK verification');entries[path]=data
        commit=git_commit(repo,head,entries,'Publish verified Restaurante Be '+args.channel+' APK set '+set_id[:16])
        git(repo,'push','origin',commit+':refs/heads/'+branch)
    urls={a['role']:f"https://raw.githubusercontent.com/{catalog.REPOSITORY}/{commit}/restaurante-be/releases/{args.channel}/{a['version_code']}-{set_id[:16]}/{a['file']}" for a in apps}
    # Download and inspect the committed files before ever signing a proposal.
    for app in apps:
        remote,_=catalog.remote_app(app['role'],urls[app['role']],args.sdk,args.java)
        catalog.require(fingerprint(remote)==fingerprint(app),'Immutable artifact round-trip differs from local APK')
    current=catalog.read_current(None,args.php)
    raw,payload,evidence=catalog.prepare({'channels':{args.channel:{'min_backend_version':args.backend_version,'urls':urls}}},current,key,args.sdk,args.java,args.php)
    if raw is None:return {'passed':True,'action':'already-current','sequence':payload['sequence'],'artifact_commit':commit}
    candidate=f"candidates/restaurante-be/{payload['sequence']:010d}-{catalog.digest(catalog.canonical(payload))[:16]}.json"
    head=fetch_main(repo)
    # A normal non-force push fails if main advanced; rerun safely, never overwrite.
    proposal_commit=git_commit(repo,head,{candidate:raw},'Propose signed Restaurante Be catalog sequence '+str(payload['sequence']))
    git(repo,'push','origin',proposal_commit+':refs/heads/main')
    return {'passed':True,'action':'proposal-pushed-await-workflow','sequence':payload['sequence'],'payload_sha256':catalog.digest(catalog.canonical(payload)),'candidate':candidate,'artifact_commit':commit,'proposal_commit':proposal_commit,'apps_verified_locally':4,'local_sdk_evidence':local,'round_trip_sdk_evidence':evidence,'private_key_uploaded':False,'workflow':'https://github.com/'+catalog.REPOSITORY+'/actions/workflows/restaurante-be-catalog.yml'}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--repo',required=True);p.add_argument('--key',required=True);p.add_argument('--channel',choices=tuple(catalog.POLICY),required=True);p.add_argument('--backend-version',required=True);p.add_argument('--apk',action='append',required=True);p.add_argument('--sdk');p.add_argument('--java');p.add_argument('--php');p.add_argument('--report',required=True);args=p.parse_args()
    report=publish(args);out=pathlib.Path(args.report);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n','utf-8');print(json.dumps({k:v for k,v in report.items() if not k.endswith('evidence')}))
if __name__=='__main__':
    try:main()
    except Exception as error:print('FAILED: '+str(error),file=sys.stderr);sys.exit(1)
