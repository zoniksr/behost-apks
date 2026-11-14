#!/usr/bin/env python3
"""Public metadata only. Verify APKs, sign locally, and promote a signed envelope.

The private signing key is an explicit external argument and is never copied.
Python standard library plus Android SDK and OpenSSL (or PHP OpenSSL) suffice.
"""
from __future__ import annotations
import argparse, base64, concurrent.futures, datetime, hashlib, json, os, pathlib
import re, shutil, subprocess, sys, tempfile, urllib.error, urllib.parse, urllib.request

REPOSITORY='zoniksr/behost-apks'
CATALOG_PATH='catalogs/restaurante-be.json'
CATALOG_URL=f'https://raw.githubusercontent.com/{REPOSITORY}/main/{CATALOG_PATH}'
KEY_ID='rb-catalog-14af64a908279c32'
PUBLIC_SHA='14af64a908279c327d5fc350c2afe606aa14b8ea6ce30c8eb72a17eabadd9daa'
ROLES=('hostess','mesero','cocina','valet')
CHANNELS=('native_qa','legacy_qa','stable')
MAX_APK=100*1024*1024
PUBLIC_KEY=pathlib.Path(__file__).resolve().parents[1]/'catalogs/catalog-public.pem'
POLICY={
 'native_qa':{'minimum_code':4,'backend':'6.7.6','certificate':'6657da45261be21b21184ddf6bca3ea64fc5488398531a589a6086a26abed4eb','packages':{r:'mx.behost.restaurantenative.candidate.'+r for r in ROLES}},
 'legacy_qa':{'minimum_code':22,'backend':'6.6.8','certificate':'cb532e0aca0d7e83fe5ab1e01eb2980eb9c59e76e54a342257e82d91ef2fc18d','packages':dict(zip(ROLES,('mx.behost.restbe.host1.qa','mx.behost.restbe.mesero1.qa','mx.behost.restbe.cocina1.qa','mx.behost.restbe.valet01.qa')))}
}
APP_KEYS={'role','application_id','version_name','version_code','min_sdk','target_sdk','debuggable','certificate_sha256','sha256','bytes','file','url'}
def require(value,message):
    if not value:raise ValueError(message)
def canonical(value):return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False).encode('utf-8')
def digest(data):return hashlib.sha256(data).hexdigest()
def pairs(items):
    result={}
    for k,v in items:
        require(k not in result,'Duplicate JSON key');result[k]=v
    return result
def decode(data):
    return json.loads(data,object_pairs_hook=pairs,parse_float=lambda _:(_ for _ in ()).throw(ValueError('Floats forbidden')),parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Non-finite JSON forbidden')))
def number(value,minimum,maximum=2**31-1):return type(value) is int and minimum<=value<=maximum
def version(value):
    require(isinstance(value,str) and re.fullmatch(r'(?:0|[1-9][0-9]{0,5})\.(?:0|[1-9][0-9]{0,5})\.(?:0|[1-9][0-9]{0,5})',value),'Invalid backend version')
    return tuple(map(int,value.split('.')))
def safe_file(value):return isinstance(value,str) and bool(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,179}\.apk',value)) and '..' not in value
def apk_url(value,filename):
    require(isinstance(value,str),'Missing APK URL')
    pattern=r'https://raw\.githubusercontent\.com/zoniksr/behost-apks/([a-f0-9]{40})/(restaurante-be/(?:[A-Za-z0-9][A-Za-z0-9._-]*/)+[A-Za-z0-9][A-Za-z0-9._-]*\.apk)'
    found=re.fullmatch(pattern,value)
    require(found and safe_file(filename),'Unapproved APK URL or filename')
    parts=found.group(2).split('/')
    require(all('..' not in p for p in parts) and parts[-1]==filename,'Unsafe APK path')
    return found.group(1)
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):raise ValueError('HTTP redirects are forbidden')
def download(url,limit,missing=False):
    opener=urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(urllib.request.Request(url,headers={'User-Agent':'BeHost-APK-Catalog/1','Cache-Control':'no-cache'}),timeout=40) as response:
            require(response.status==200 and response.url==url,'Unexpected HTTP response')
            data=response.read(limit+1);require(len(data)<=limit,'HTTP response exceeds size limit');return data
    except urllib.error.HTTPError as error:
        if missing and error.code==404:return None
        raise
def command(args,env=None,cwd=None,input_bytes=None):
    result=subprocess.run([str(a) for a in args],input=input_bytes,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,cwd=cwd,timeout=180)
    require(result.returncode==0,'Command failed: '+pathlib.Path(str(args[0])).name+' '+result.stderr.decode('utf-8','replace')[-3000:])
    return result.stdout.decode('utf-8','replace')
def crypto(payload,signature=None,key=None,php=None):
    require(PUBLIC_KEY.is_file() and digest(PUBLIC_KEY.read_bytes())==PUBLIC_SHA,'Pinned public key changed')
    with tempfile.TemporaryDirectory(prefix='be-catalog-crypto-') as d:
        tmp=pathlib.Path(d);message=tmp/'payload';sig=tmp/'signature';message.write_bytes(payload)
        if signature is not None:sig.write_bytes(signature)
        openssl=shutil.which('openssl')
        if openssl:
            if key:
                details=command([openssl,'pkey','-in',key,'-text_pub','-noout'])
                require('3072 bit' in details,'Signing key must be RSA3072')
                command([openssl,'dgst','-sha256','-sign',key,'-out',sig,message])
            command([openssl,'dgst','-sha256','-verify',PUBLIC_KEY,'-signature',sig,message])
        elif php or shutil.which('php'):
            executable=php or shutil.which('php')
            if key:
                code='$k=openssl_pkey_get_private(file_get_contents($argv[1]));$d=openssl_pkey_get_details($k);if(!$d||$d["bits"]!==3072||$d["type"]!==OPENSSL_KEYTYPE_RSA)exit(2);if(!openssl_sign(file_get_contents($argv[2]),$s,$k,OPENSSL_ALGO_SHA256))exit(3);if(file_put_contents($argv[3],$s)!==strlen($s))exit(4);'
                command([executable,'-r',code,key,message,sig])
            code='exit(openssl_verify(file_get_contents($argv[1]),file_get_contents($argv[2]),file_get_contents($argv[3]),OPENSSL_ALGO_SHA256)===1?0:1);'
            command([executable,'-r',code,message,sig,PUBLIC_KEY])
        else:raise ValueError('OpenSSL executable or --php with OpenSSL extension is required')
        return sig.read_bytes()
def validate(payload):
    require(isinstance(payload,dict) and set(payload)=={'schema_version','product','sequence','issued_at','channels'},'Unexpected payload keys')
    require(payload['schema_version']==1 and type(payload['schema_version']) is int and payload['product']=='restaurante-be','Wrong catalog identity')
    require(number(payload['sequence'],1,2**63-2),'Invalid sequence')
    require(isinstance(payload['issued_at'],str) and re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ',payload['issued_at']),'Invalid issued_at')
    issued=datetime.datetime.fromisoformat(payload['issued_at'].replace('Z','+00:00'))
    require(issued<=datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds=300),'issued_at is in the future')
    require(isinstance(payload['channels'],dict) and set(payload['channels'])==set(CHANNELS),'Wrong channels')
    for name,channel in payload['channels'].items():
        require(isinstance(channel,dict) and set(channel)=={'status','version','version_code','min_backend_version','production_tested','apps'},'Unexpected channel fields')
        require(channel['status'] in ('active','disabled') and channel['production_tested'] is False and isinstance(channel['apps'],list),'Invalid channel state')
        version(channel['min_backend_version'])
        if name in POLICY:require(version(channel['min_backend_version'])>=version(POLICY[name]['backend']),'Channel below backend minimum')
        if channel['status']=='disabled':
            require(channel['apps']==[] and channel['version']=='' and channel['version_code']==0 and type(channel['version_code']) is int,'Disabled channel must be empty');continue
        require(name in POLICY,'Stable has no authorized production identity')
        policy=POLICY[name]
        require(number(channel['version_code'],policy['minimum_code'],2100000000),'Channel below policy minimum')
        require(isinstance(channel['version'],str) and re.fullmatch(r'[0-9][A-Za-z0-9._+\-]{0,79}',channel['version']),'Invalid channel version')
        require(len(channel['apps'])==4,'A channel requires four apps')
        found=set()
        for app in channel['apps']:
            require(isinstance(app,dict) and set(app)==APP_KEYS,'Unexpected app fields')
            role=app['role'];require(role in ROLES and role not in found,'Duplicate/invalid role');found.add(role)
            require(app['application_id']==policy['packages'][role] and app['certificate_sha256']==policy['certificate'],'Unauthorized Android identity or signer')
            require(app['version_name']==channel['version'] and app['version_code']==channel['version_code'] and type(app['version_code']) is int,'Mixed app versions')
            require(number(app['min_sdk'],21,100) and number(app['target_sdk'],app['min_sdk'],100) and type(app['debuggable']) is bool and (name!='legacy_qa' or app['debuggable'] is False),'Invalid SDK/debug metadata')
            require(number(app['bytes'],1,MAX_APK) and isinstance(app['sha256'],str) and re.fullmatch('[a-f0-9]{64}',app['sha256']),'Invalid binary size/hash')
            apk_url(app['url'],app['file'])
    return payload
def verify_envelope(raw,php=None):
    require(len(raw)<=256*1024,'Envelope too large');envelope=decode(raw)
    require(isinstance(envelope,dict) and set(envelope)=={'schema','key_id','algorithm','payload_base64','signature_base64'},'Unexpected envelope fields')
    require(envelope['schema']=='behost-apk-envelope-v1' and envelope['key_id']==KEY_ID and envelope['algorithm']=='RS256','Untrusted envelope identity')
    payload=base64.b64decode(envelope['payload_base64'],validate=True);signature=base64.b64decode(envelope['signature_base64'],validate=True)
    require(base64.b64encode(payload).decode()==envelope['payload_base64'] and base64.b64encode(signature).decode()==envelope['signature_base64'],'Noncanonical base64')
    require(len(payload)<=128*1024 and len(signature)==384,'Invalid envelope sizes')
    crypto(payload,signature=signature,php=php)
    value=decode(payload);require(canonical(value)==payload,'Payload is not canonical JSON')
    return validate(value)
def sign(payload,key,php=None):
    data=canonical(validate(payload));signature=crypto(data,key=key,php=php)
    envelope={'schema':'behost-apk-envelope-v1','key_id':KEY_ID,'algorithm':'RS256','payload_base64':base64.b64encode(data).decode(),'signature_base64':base64.b64encode(signature).decode()}
    raw=canonical(envelope)+b'\n';verify_envelope(raw,php);return raw
def monotonic(candidate,current):
    if current is None:return True
    require(candidate['sequence']>=current['sequence'],'Catalog sequence rollback')
    if candidate['sequence']==current['sequence']:
        require(canonical(candidate)==canonical(current),'Same sequence with different payload');return False
    for name in POLICY:
        old=current['channels'][name];new=candidate['channels'][name]
        if old['status']!='active' or new['status']!='active':continue
        require(new['version_code']>=old['version_code'],'Android versionCode rollback')
        if new['version_code']==old['version_code']:
            prior={a['role']:a for a in old['apps']}
            for app in new['apps']:
                before=prior[app['role']]
                require(all(app[k]==before[k] for k in ('application_id','certificate_sha256','sha256','bytes','version_name','version_code','min_sdk','target_sdk','debuggable')),'Same versionCode changed binary/identity')
    return True
def sdk_tools(sdk):
    root=pathlib.Path(sdk or os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or '')
    versions=[p for p in (root/'build-tools').glob('*') if re.fullmatch(r'\d+\.\d+\.\d+',p.name)]
    for folder in sorted(versions,key=lambda p:tuple(map(int,p.name.split('.'))),reverse=True):
        signer=folder/('apksigner.bat' if os.name=='nt' else 'apksigner');aapt=folder/('aapt2.exe' if os.name=='nt' else 'aapt2');align=folder/('zipalign.exe' if os.name=='nt' else 'zipalign')
        if all(p.is_file() for p in (signer,aapt,align)):return signer,aapt,align
    raise ValueError('Android SDK apksigner, aapt2 and zipalign are required')
def inspect_apk(path,sdk=None,java=None):
    path=pathlib.Path(path);require(path.is_file() and 0<path.stat().st_size<=MAX_APK,'Invalid APK size')
    signer,aapt,align=sdk_tools(sdk);env=os.environ.copy()
    if java:env['JAVA_HOME']=str(java)
    signature=command([signer,'verify','--verbose','--print-certs',path],env)
    certificates=set(v.replace(':','').lower() for v in re.findall(r'(?m)^(?:Signer #\d+:?|V\d+(?:\.\d+)? Signer:) certificate SHA-256 digest: ([0-9a-fA-F:]+)\s*$',signature))
    require(len(certificates)==1 and re.search(r'(?m)^Number of signers: 1\s*$',signature),'Multiple/missing APK signers')
    badging=command([aapt,'dump','badging',path],env)
    package=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging);require(package,'Missing package metadata')
    minimum=re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'",badging);target=re.search(r"targetSdkVersion:'(\d+)'",badging);require(minimum and target,'Missing SDK metadata')
    command([align,'-c','4',path],env)
    data=path.read_bytes()
    return {'application_id':package[1],'version_code':int(package[2]),'version_name':package[3],'min_sdk':int(minimum[1]),'target_sdk':int(target[1]),'debuggable':'application-debuggable' in badging,'certificate_sha256':next(iter(certificates)),'bytes':len(data),'sha256':digest(data)}, {'signature':signature,'badging':badging,'zipalign_verified':True}
def remote_app(role,url,sdk=None,java=None):
    filename=urllib.parse.urlsplit(url).path.rsplit('/',1)[-1];apk_url(url,filename)
    data=download(url,MAX_APK)
    with tempfile.TemporaryDirectory(prefix='be-catalog-apk-') as folder:
        path=pathlib.Path(folder)/filename;path.write_bytes(data);metadata,evidence=inspect_apk(path,sdk,java)
    return dict(metadata,role=role,file=filename,url=url),dict(evidence,role=role,url=url,sha256=metadata['sha256'],bytes=metadata['bytes'])
def disabled(backend='6.7.6'):return {'status':'disabled','version':'','version_code':0,'min_backend_version':backend,'production_tested':False,'apps':[]}
def verify_apks(payload,sdk=None,java=None):
    jobs=[a for c in payload['channels'].values() for a in c['apps']]
    def verify(app):
        actual,evidence=remote_app(app['role'],app['url'],sdk,java)
        require(actual==app,'Downloaded APK metadata does not match signed catalog');return evidence
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:return list(pool.map(verify,jobs))
def prepare(spec,current,key,sdk=None,java=None,php=None):
    payload={'schema_version':1,'product':'restaurante-be','sequence':current['sequence']+1 if current else 1,'issued_at':datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),'channels':json.loads(json.dumps(current['channels'])) if current else {n:disabled() for n in CHANNELS}}
    evidence=[]
    require(isinstance(spec,dict) and set(spec)=={'channels'} and isinstance(spec['channels'],dict),'Expected channels input')
    for name,choice in spec['channels'].items():
        require(name in CHANNELS,'Unknown channel')
        if choice.get('status')=='disabled':payload['channels'][name]=disabled(choice.get('min_backend_version','6.7.6'));continue
        require(name in POLICY and set(choice['urls'])==set(ROLES),'Exactly four approved roles required')
        jobs=[(r,choice['urls'][r]) for r in ROLES]
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(lambda x:remote_app(*x,sdk,java),jobs))
        apps=[x[0] for x in results];evidence.extend(x[1] for x in results)
        payload['channels'][name]={'status':'active','version':apps[0]['version_name'],'version_code':apps[0]['version_code'],'min_backend_version':choice['min_backend_version'],'production_tested':False,'apps':apps}
    validate(payload);monotonic(payload,current)
    if current and payload['channels']==current['channels']:return None,current,evidence
    return sign(payload,key,php),payload,evidence
def read_current(path,php,bootstrap=False):
    raw=pathlib.Path(path).read_bytes() if path else download(CATALOG_URL,256*1024,missing=bootstrap)
    if raw is None:require(bootstrap,'Current catalog missing');return None
    return verify_envelope(raw,php)
def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='action',required=True)
    for name in ('prepare','verify'):
        p=sub.add_parser(name);p.add_argument('--sdk');p.add_argument('--java');p.add_argument('--php');p.add_argument('--current');p.add_argument('--report',required=True)
        if name=='prepare':p.add_argument('--input',required=True);p.add_argument('--key',required=True);p.add_argument('--output',required=True);p.add_argument('--bootstrap',action='store_true')
        else:p.add_argument('--candidate',required=True);p.add_argument('--skip-sdk',action='store_true',help='Signature/policy tests only; cannot authorize promotion')
    args=parser.parse_args()
    if args.action=='prepare':
        current=read_current(args.current,args.php,args.bootstrap)
        raw,payload,evidence=prepare(decode(pathlib.Path(args.input).read_bytes()),current,args.key,args.sdk,args.java,args.php)
        if raw:pathlib.Path(args.output).write_bytes(raw)
        report={'passed':True,'action':'prepared' if raw else 'no-op','sequence':payload['sequence'],'payload_sha256':digest(canonical(payload)),'apps_verified':len(evidence),'sdk_evidence':evidence,'private_key_included':False}
    else:
        payload=verify_envelope(pathlib.Path(args.candidate).read_bytes(),args.php)
        if args.current:monotonic(payload,read_current(args.current,args.php))
        evidence=[] if args.skip_sdk else verify_apks(payload,args.sdk,args.java)
        report={'passed':True,'action':'verified','sequence':payload['sequence'],'payload_sha256':digest(canonical(payload)),'sdk_executed':not args.skip_sdk,'apps_verified':len(evidence),'sdk_evidence':evidence}
    pathlib.Path(args.report).parent.mkdir(parents=True,exist_ok=True);pathlib.Path(args.report).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='sdk_evidence'}))
if __name__=='__main__':
    try:main()
    except Exception as error:print('FAILED: '+str(error),file=sys.stderr);sys.exit(1)
