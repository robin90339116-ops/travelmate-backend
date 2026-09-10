import json, urllib.request, urllib.error, subprocess, time, pathlib, concurrent.futures, os, shutil, sys
SCRIPTS=pathlib.Path(__file__).resolve().parent
ROOT=SCRIPTS.parent
JAVA=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
BASE='http://127.0.0.1:18787'
results=[]
def req(method,path,body=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    r=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(r,timeout=4) as s: return s.status,json.loads(s.read())
    except urllib.error.HTTPError as e:
        try: data=json.loads(e.read())
        except Exception: data={}
        return e.code,data
def check(name,expected,actual):
    row={'test':name,'pass':expected==actual,'expected':expected,'actual':actual}
    results.append(row); print(json.dumps(row,ensure_ascii=False),flush=True)
def login(phone): return req('POST','/api/auth/login',{'phone':phone,'code':'246810','deviceName':'audit'})[1]['data']
log=open(ROOT/'runtime.log','w')
p=subprocess.Popen([JAVA,'-jar',str(ROOT/'target/travelmate-backend-1.0.0.jar'),'--server.port=18787','--server.address=127.0.0.1',
 '--spring.profiles.active=default','--spring.datasource.url=jdbc:h2:mem:smoke;MODE=MySQL','--spring.datasource.driver-class-name=org.h2.Driver',
 '--spring.jpa.hibernate.ddl-auto=create-drop','--app.sms.dev-enabled=true','--app.sms.dev-code=246810',
 '--app.jwt.secret=smoke-only-secret-at-least-32-bytes-long','--app.ai.api-key=','--app.map.amap-web-key='],stdout=log,stderr=log)
try:
    for _ in range(100):
        try:
            if req('GET','/api/health')[0]==200: break
        except Exception: time.sleep(.2)
    check('health',200,req('GET','/api/health')[0])
    check('three cities',3,len(req('GET','/api/catalog/cities')[1]['data']))
    time.sleep(1)
    check('three cities after startup settles',3,len(req('GET','/api/catalog/cities')[1]['data']))
    check('route detail',200,req('GET','/api/catalog/routes/route-beijing-axis')[0])
    check('missing route',404,req('GET','/api/catalog/routes/absent')[0])
    check('anonymous favorites',401,req('GET','/api/favorites')[0])
    check('bad login code',400,req('POST','/api/auth/login',{'phone':'13800000000','code':'wrong'})[0])
    a,b,c=[login(x) for x in ['13800000001','13800000002','13800000003']]
    ta,tb,tc=[x['accessToken'] for x in [a,b,c]]
    check('sessions current marker',True,any(s['current'] for s in req('GET','/api/auth/sessions',token=ta)[1]['data']['sessions']))
    rotated=req('POST','/api/auth/refresh',{'refreshToken':a['refreshToken']})
    check('refresh rotation',200,rotated[0])
    check('old refresh replay rejected',401,req('POST','/api/auth/refresh',{'refreshToken':a['refreshToken']})[0])
    fav=req('POST','/api/favorites',{'targetType':'point','title':'audit'},ta)
    check('create favorite',200,fav[0])
    check('favorite isolation',0,len(req('GET','/api/favorites',token=tb)[1]['data']))
    check('foreign favorite delete',404,req('DELETE','/api/favorites/'+str(fav[1]['data']['id']),token=tb)[0])
    check('empty favorite rejected',400,req('POST','/api/favorites',{'targetType':'','title':''},ta)[0])
    team=req('POST','/api/teams',{'routeId':'route-beijing-axis'},ta)[1]['data']; tid=str(team['teamId'])
    check('outsider room read rejected',403,req('GET','/api/teams/'+tid,token=tc)[0])
    check('join team',200,req('POST','/api/teams/join',{'teamCode':team['teamCode']},tb)[0])
    check('non-owner kick rejected',403,req('DELETE',f'/api/teams/{tid}/members/{a["user"]["id"]}',token=tb)[0])
    check('outsider playback rejected',403,req('POST',f'/api/teams/{tid}/playback',{'playbackStatus':'playing'},tc)[0])
    check('invalid playback rejected',400,req('POST',f'/api/teams/{tid}/playback',{'playbackStatus':'NOT_A_STATE'},ta)[0])
    ws=subprocess.run(['node',str(SCRIPTS/'ws.mjs'),tid,str(a['user']['id'])],capture_output=True,text=True,timeout=10)
    print('WEBSOCKET '+ws.stdout.strip(),flush=True)
    check('anonymous websocket impersonation blocked',False,req('GET','/api/teams/'+tid,token=ta)[1]['data']['currentPointId']=='forged-by-anonymous')
    ws=subprocess.run(['node',str(SCRIPTS/'ws.mjs'),tid,str(a['user']['id'])],env={**os.environ,'AUDIT_TOKEN':ta},capture_output=True,text=True,timeout=10)
    check('authenticated websocket receives broadcast',True,'received team broadcast' in ws.stdout)
    check('transfer owner',200,req('POST',f'/api/teams/{tid}/transfer/{b["user"]["id"]}',{},ta)[0])
    req('POST',f'/api/teams/{tid}/leave',{},tb)
    state=req('GET','/api/teams/'+tid,token=ta)[1]['data']
    check('owner leave preserves valid ownership',True,any(m['userId']==state['ownerId'] for m in state['members']))
    check('AI missing credential',503,req('POST','/api/ai/explanations',{'spotId':'1'},ta)[0])
    check('vision missing media',400,req('POST','/api/ai/vision',{'spotId':'1'},ta)[0])
    check('map missing credential',503,req('POST','/api/map/search',{'keyword':'北京'})[0])
    check('map blank keyword',400,req('POST','/api/map/search',{'keyword':''})[0])
    job=req('POST','/api/ai/explanations/async',{'spotId':'1'},ta)[1]['data']
    time.sleep(.3)
    r=urllib.request.Request(BASE+job['streamUrl'] if 'streamUrl' in job else BASE+'/api/ai/jobs/'+job['jobId']+'/stream',headers={'Authorization':'Bearer '+ta})
    try:
        with urllib.request.urlopen(r,timeout=2) as s: outcome=bool(s.readline())
    except TimeoutError: outcome=False
    check('failed async job returns terminal event on later subscription',True,outcome)
    check('logout all',200,req('POST','/api/auth/logout-all',{},ta)[0])
    check('revoked access rejected',401,req('GET','/api/favorites',token=ta)[0])
    check('revoked refresh rejected',401,req('POST','/api/auth/refresh',{'refreshToken':rotated[1]['data']['refreshToken']})[0])
finally:
    p.terminate()
    try:p.wait(timeout=8)
    except subprocess.TimeoutExpired:p.kill();p.wait()
    log.close()
    (ROOT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
sys.exit(0 if len(results)>=31 and all(r['pass'] for r in results) else 1)
