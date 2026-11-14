#!/usr/bin/env python3
"""Trusted-main verifier and atomic promoter. Never executes candidate code."""
from __future__ import annotations
import argparse, base64, copy, json, os, pathlib, re, sys, urllib.error, urllib.request
import apk_catalog as catalog

STATE_PATH='catalogs/restaurante-be-state.json'
CANDIDATE_RE=r'candidates/restaurante-be/[0-9]{10}-[a-f0-9]{16}\.json'
IDENTITY_FIELDS=('application_id','certificate_sha256','version_name','version_code','min_sdk','target_sdk','debuggable','sha256','bytes')

def fingerprint(app):return {key:app[key] for key in IDENTITY_FIELDS}

def next_state(payload,current,state):
    """Retain each identity's ceiling even while a channel is revoked."""
    catalog.monotonic(payload,current)
    if current is None:
        catalog.require(state is None and payload['sequence']==1,'Bootstrap requires sequence 1 and no prior state')
        identities={}
    else:
        catalog.require(isinstance(state,dict) and set(state)=={'schema_version','sequence','payload_sha256','identities'},'Missing/invalid publication high-water state')
        catalog.require(state['schema_version']==1 and state['sequence']==current['sequence'] and state['payload_sha256']==catalog.digest(catalog.canonical(current)),'Publication state does not match current catalog')
        identities=copy.deepcopy(state['identities'])
        catalog.require(isinstance(identities,dict),'Invalid high-water identities')
        for key,app in identities.items():
            pieces=key.split('/')
            catalog.require(len(pieces)==2 and pieces[0] in catalog.POLICY and pieces[1] in catalog.ROLES,'Invalid high-water key')
            channel,role=pieces;policy=catalog.POLICY[channel]
            catalog.require(isinstance(app,dict) and set(app)==set(IDENTITY_FIELDS),'Invalid high-water fields')
            catalog.require(app['application_id']==policy['packages'][role] and app['certificate_sha256']==policy['certificate'] and catalog.number(app['version_code'],policy['minimum_code']),'Invalid high-water identity')
        for name,channel in current['channels'].items():
            for app in channel['apps']:
                catalog.require(identities.get(name+'/'+app['role'])==fingerprint(app),'High-water is inconsistent with current active catalog')
    for name,channel in payload['channels'].items():
        for app in channel['apps']:
            key=name+'/'+app['role'];old=identities.get(key);new=fingerprint(app)
            if old:
                catalog.require(new['version_code']>=old['version_code'],'Version rollback after revocation')
                if new['version_code']==old['version_code']:catalog.require(new==old,'Same versionCode changed after revocation')
            identities[key]=new
    return {'schema_version':1,'sequence':payload['sequence'],'payload_sha256':catalog.digest(catalog.canonical(payload)),'identities':identities}

class GitHub:
    def __init__(self,token):
        catalog.require(token,'GH_TOKEN is required');self.token=token
    def api(self,method,path,body=None,missing=False):
        catalog.require(path.startswith('/repos/'+catalog.REPOSITORY+'/'),'Unexpected repository API path')
        data=catalog.canonical(body) if body is not None else None
        request=urllib.request.Request('https://api.github.com'+path,data=data,method=method,headers={'Authorization':'Bearer '+self.token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'BeHost-APK-Catalog/1','Content-Type':'application/json'})
        try:
            with urllib.request.build_opener(catalog.NoRedirect()).open(request,timeout=40) as response:
                raw=response.read(4*1024*1024+1);catalog.require(len(raw)<=4*1024*1024,'GitHub API response too large');return catalog.decode(raw)
        except urllib.error.HTTPError as error:
            if missing and error.code==404:return None
            # Do not echo API bodies or request headers (credentials).
            raise RuntimeError('GitHub API '+method+' failed with HTTP '+str(error.code)) from None
    def head(self):return self.api('GET','/repos/'+catalog.REPOSITORY+'/git/ref/heads/main')['object']['sha']
    def file(self,path,commit,missing=False):
        catalog.require(re.fullmatch('[a-f0-9]{40}',commit) and re.fullmatch('[A-Za-z0-9._/-]+',path) and '..' not in path,'Unsafe GitHub file reference')
        value=self.api('GET','/repos/'+catalog.REPOSITORY+'/contents/'+path+'?ref='+commit,missing=missing)
        if value is None:return None
        catalog.require(value.get('type')=='file' and value.get('encoding')=='base64','Unexpected GitHub file format')
        return base64.b64decode(value['content'].replace('\n',''),validate=True)
    def changed_candidates(self,before,after):
        catalog.require(re.fullmatch('[a-f0-9]{40}',before) and re.fullmatch('[a-f0-9]{40}',after),'Invalid push commit')
        result=self.api('GET','/repos/'+catalog.REPOSITORY+'/compare/'+before+'...'+after)
        files=result.get('files',[]);catalog.require(len(files)<300,'Push too large for bounded candidate discovery')
        return [f['filename'] for f in files if f.get('status') in ('added','modified') and re.fullmatch(CANDIDATE_RE,f['filename'])]
    def commit(self,parent,files,message):
        prefix='/repos/'+catalog.REPOSITORY
        base=self.api('GET',prefix+'/git/commits/'+parent)['tree']['sha'];entries=[]
        for path,raw in files.items():
            blob=self.api('POST',prefix+'/git/blobs',{'content':base64.b64encode(raw).decode(),'encoding':'base64'})['sha']
            entries.append({'path':path,'mode':'100644','type':'blob','sha':blob})
        tree=self.api('POST',prefix+'/git/trees',{'base_tree':base,'tree':entries})['sha']
        commit=self.api('POST',prefix+'/git/commits',{'message':message,'tree':tree,'parents':[parent]})['sha']
        # A changed main points to a sibling, so non-force update rejects it.
        self.api('PATCH',prefix+'/git/refs/heads/main',{'sha':commit,'force':False})
        return commit

def verify_candidate(candidate_raw,sdk=None,java=None,php=None):
    payload=catalog.verify_envelope(candidate_raw,php)
    evidence=catalog.verify_apks(payload,sdk,java)
    return {'passed':True,'action':'verified','sequence':payload['sequence'],'envelope_sha256':catalog.digest(candidate_raw),'payload_sha256':catalog.digest(catalog.canonical(payload)),'sdk_executed':True,'apps_verified':len(evidence),'sdk_evidence':evidence}

def verified_evidence(raw,payload,report):
    catalog.require(isinstance(report,dict) and report.get('passed') is True and report.get('action')=='verified' and report.get('sdk_executed') is True,'Successful read-only verification required')
    catalog.require(report.get('envelope_sha256')==catalog.digest(raw) and report.get('payload_sha256')==catalog.digest(catalog.canonical(payload)) and report.get('sequence')==payload['sequence'],'Verification belongs to different candidate bytes')
    expected=[a for c in payload['channels'].values() for a in c['apps']];evidence=report.get('sdk_evidence')
    catalog.require(isinstance(evidence,list) and len(evidence)==len(expected) and report.get('apps_verified')==len(expected),'Incomplete APK verification')
    for actual,app in zip(evidence,expected):
        catalog.require(all(actual.get(k)==app[k] for k in ('role','url','sha256','bytes')) and actual.get('zipalign_verified') is True,'Verification metadata does not match signed APK')
    return evidence

def promote(api,candidate_raw,verified,php=None):
    # SDK tools only run in the preceding read-only job. This writer rechecks
    # the exact artifact bytes and signature and performs only bounded JSON/Git operations.
    payload=catalog.verify_envelope(candidate_raw,php)
    evidence=verified_evidence(candidate_raw,payload,verified)
    payload_sha=catalog.digest(catalog.canonical(payload))
    for attempt in range(3):
        head=api.head();current_raw=api.file(catalog.CATALOG_PATH,head,missing=True)
        state_raw=api.file(STATE_PATH,head,missing=True)
        current=catalog.verify_envelope(current_raw,php) if current_raw else None
        state=catalog.decode(state_raw) if state_raw else None
        updated=next_state(payload,current,state)
        if current and payload['sequence']==current['sequence']:
            return {'passed':True,'action':'already-current','sequence':payload['sequence'],'payload_sha256':payload_sha,'commit':head,'apps_verified':len(evidence),'sdk_evidence':evidence}
        files={catalog.CATALOG_PATH:candidate_raw,STATE_PATH:catalog.canonical(updated)+b'\n',f"catalogs/history/restaurante-be/{payload['sequence']:010d}-{payload_sha[:16]}.json":candidate_raw}
        try:
            commit=api.commit(head,files,'Promote signed Restaurante Be catalog sequence '+str(payload['sequence']))
        except RuntimeError:
            if api.head()==head or attempt==2:raise
            continue
        catalog.require(api.file(catalog.CATALOG_PATH,commit)==candidate_raw,'Promotion round-trip bytes differ')
        catalog.require(api.file(STATE_PATH,commit)==files[STATE_PATH],'Promotion state round-trip differs')
        return {'passed':True,'action':'promoted','sequence':payload['sequence'],'payload_sha256':payload_sha,'commit':commit,'apps_verified':len(evidence),'sdk_evidence':evidence}
    raise ValueError('Concurrent promotion did not settle; rerun against current main')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=('verify','promote'));p.add_argument('--candidate');p.add_argument('--commit');p.add_argument('--before');p.add_argument('--sdk');p.add_argument('--java');p.add_argument('--php');p.add_argument('--report',required=True);p.add_argument('--candidate-file',required=True);p.add_argument('--verified-report');args=p.parse_args()
    out=pathlib.Path(args.report);out.parent.mkdir(parents=True,exist_ok=True)
    try:
        catalog.require(os.environ.get('GITHUB_REPOSITORY')==catalog.REPOSITORY,'Workflow repository is not authorized')
        api=GitHub(os.environ.get('GH_TOKEN'))
        if args.mode=='verify':
            candidate=args.candidate
            if not candidate:
                catalog.require(args.before,'Push before commit required');paths=api.changed_candidates(args.before,args.commit);catalog.require(len(paths)==1,'Exactly one candidate must be added/modified per push');candidate=paths[0]
            catalog.require(re.fullmatch(CANDIDATE_RE,candidate),'Invalid candidate path')
            raw=api.file(candidate,args.commit);payload=catalog.verify_envelope(raw,args.php)
            expected=f"candidates/restaurante-be/{payload['sequence']:010d}-{catalog.digest(catalog.canonical(payload))[:16]}.json"
            catalog.require(candidate==expected,'Candidate filename does not identify signed payload')
            result=verify_candidate(raw,args.sdk,args.java,args.php)
            pathlib.Path(args.candidate_file).write_bytes(raw)
        else:
            catalog.require(args.verified_report,'Read-only job report required')
            raw=pathlib.Path(args.candidate_file).read_bytes();verified=catalog.decode(pathlib.Path(args.verified_report).read_bytes())
            result=promote(api,raw,verified,args.php)
    except Exception as error:
        out.write_text(json.dumps({'passed':False,'action':args.mode,'error':str(error)},indent=2)+'\n','utf-8');raise
    out.write_text(json.dumps(result,indent=2)+'\n','utf-8');print(json.dumps({k:v for k,v in result.items() if k!='sdk_evidence'}))
if __name__=='__main__':
    try:main()
    except Exception as error:print('FAILED: '+str(error),file=sys.stderr);sys.exit(1)
