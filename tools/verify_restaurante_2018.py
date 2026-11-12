"""Audit immutable public Restaurante APKs. Does not build, resign or modify them."""
import glob, hashlib, json, os, pathlib, re, subprocess, time, urllib.request, zipfile
OUT=pathlib.Path('dist');OUT.mkdir(exist_ok=True)
COMMIT='fa4e37834fd8c81bbfd1a10c01db3ea8d534b456'
CERT='cb532e0aca0d7e83fe5ab1e01eb2980eb9c59e76e54a342257e82d91ef2fc18d'
SPECS=[('mesero','Mesero','mx.behost.restbe.mesero1.qa',2414593,'6efdfbdb5cfd3661597a76444c8a808332e4ef7f2be663dfcab56f18a4ab849d'),('cocina','Cocina','mx.behost.restbe.cocina1.qa',2417814,'a563f7df9859370916ff70a57a1c8941bc6e91a54d184f68f64953554c7a43b9'),('host','Hostess','mx.behost.restbe.host1.qa',2372837,'c0ead911fef9b282c95c974f8e4abf03a9b55129526428e5b7bad1f7a908ac04'),('valet','Valet','mx.behost.restbe.valet01.qa',2435320,'374e7419692e0271e9b7a496d93a2933c111c21464616e38dbc4e3a289667559')]
def certificates(text):
    # v37 labels scheme signers as V3.0 Signer; older releases use Signer #1.
    values=re.findall(r'(?m)^(?:Signer #\d+|V\d+(?:\.\d+)? Signer): certificate SHA-256 digest: ([0-9a-fA-F:]+)\s*$',text)
    return set(v.replace(':','').lower() for v in values)
assert certificates('Signer #1 certificate SHA-256 digest: '+CERT)==set() # missing colon is not the known format
assert certificates('Signer #1: certificate SHA-256 digest: '+CERT)=={CERT}
assert certificates('V3.0 Signer: certificate SHA-256 digest: '+CERT)=={CERT}
# The old apksigner format has no colon after #1 in some releases; support that explicit form too.
def valid_certificate(text):
    values=certificates(text)|set(v.lower() for v in re.findall(r'(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)\s*$',text))
    return values=={CERT} and bool(re.search(r'(?m)^Number of signers: 1\s*$',text))
assert valid_certificate('Number of signers: 1\nV3.0 Signer: certificate SHA-256 digest: '+CERT)
assert not valid_certificate('Number of signers: 2\nV3.0 Signer: certificate SHA-256 digest: '+CERT)
SDK=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/usr/local/lib/android/sdk'
dirs=[pathlib.Path(p) for p in glob.glob(SDK+'/build-tools/*') if re.fullmatch(r'\d+\.\d+\.\d+',pathlib.Path(p).name)]
dirs=[p for p in dirs if all((p/f).is_file() for f in ('aapt','apksigner','zipalign'))]
TOOL=sorted(dirs,key=lambda p:tuple(map(int,p.name.split('.'))))[-1] if dirs else None
report={'version':'2.0.18-qa1','source_commit':COMMIT,'recompiled':False,'native_modifications':False,'physical_device_tested':False,'production_certified':False,'sdk':str(TOOL) if TOOL else None,'apps':[],'errors':[]}
def run(args):
    r=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=90)
    if r.returncode:raise ValueError('SDK: '+r.stdout[:5000])
    return r.stdout
for role,title,package,size,digest in SPECS:
    name='Restaurante_Be_'+title+'_v2.0.18-qa1.apk';a={'role':role,'file':name,'expected_package':package};report['apps'].append(a)
    try:
        url='https://raw.githubusercontent.com/zoniksr/behost-apks/'+COMMIT+'/restaurante-be/2.0.18-qa1/'+name
        for attempt in range(3):
            try:
                with urllib.request.urlopen(url,timeout=60) as r:data=r.read(size+1)
                break
            except Exception:
                if attempt==2:raise
                time.sleep(2)
        a.update(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
        if a['bytes']!=size or a['sha256']!=digest:raise ValueError('SHA-256 o tamano no coincide')
        path=OUT/name;path.write_bytes(data)
        with zipfile.ZipFile(path) as z:
            if z.testzip() or not {'AndroidManifest.xml','classes.dex'}.issubset(z.namelist()):raise ValueError('ZIP Android invalido')
        if TOOL is None:raise ValueError('SDK tools no disponible')
        signed=run([str(TOOL/'apksigner'),'verify','--verbose','--print-certs',str(path)])
        a['signature_output']=signed
        if not valid_certificate(signed):raise ValueError('Certificado o numero de firmantes diferente')
        a.update(signature_verified=True,certificate_sha256=CERT)
        badge=run([str(TOOL/'aapt'),'dump','badging',str(path)])
        a['package_line']=badge.splitlines()[0]
        for key,expected in [('name',package),('versionCode','22'),('versionName','2.0.18-qa1')]:
            m=re.search(r"\b"+key+r"='([^']+)'",a['package_line'])
            if not m or m.group(1)!=expected:raise ValueError('Identidad/version no coincide: '+key)
        a['debuggable']='application-debuggable' in badge
        if a['debuggable']:raise ValueError('APK depurable: no publicar')
        run([str(TOOL/'zipalign'),'-c','4',str(path)])
        a.update(identity_verified=True,alignment_verified=True,status='verified')
    except Exception as exc:
        a.update(status='failed',error=str(exc));report['errors'].append(role+': '+str(exc))
    print(json.dumps(a,ensure_ascii=False),flush=True)
report['status']='verified' if not report['errors'] else 'failed'
(OUT/'VERIFICACION_ANDROID.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(OUT/'SHA256SUMS.txt').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name for p in sorted(OUT.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt')+'\n')
if report['errors']:raise SystemExit('No publicar: revisar auditoria.')
