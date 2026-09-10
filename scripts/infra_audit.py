import subprocess, pathlib, os, time, json, urllib.request, urllib.error, base64, threading, shutil, sys
from http.server import HTTPServer,BaseHTTPRequestHandler
SCRIPTS=pathlib.Path(__file__).resolve().parent
ROOT=SCRIPTS.parent
JAVA=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
checks=[]; processes=[]; logs=[]
class Provider(BaseHTTPRequestHandler):
    failing=False
    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length','0')))
        self.send_response(503 if Provider.failing else 200);self.send_header('Content-Type','application/json');self.end_headers()
        self.wfile.write(json.dumps({'choices':[{'message':{'content':'受控替身生成的讲解'}}]}).encode())
    def log_message(self,*args):pass
def req(port,method,path,body=None,token=None):
    h={'Content-Type':'application/json'}
    if token:h['Authorization']='Bearer '+token
    r=urllib.request.Request(f'http://127.0.0.1:{port}'+path,data=json.dumps(body).encode() if body is not None else None,headers=h,method=method)
    try:
        with urllib.request.urlopen(r,timeout=5) as s:return s.status,json.loads(s.read())
    except urllib.error.HTTPError as e:return e.code,json.loads(e.read())
def check(name,yes):
    checks.append({'test':name,'pass':bool(yes)});print(json.dumps(checks[-1],ensure_ascii=False),flush=True)
def start(port):
    log=open(ROOT/f'prod-{port}.log','w');logs.append(log)
    env={**os.environ,'JWT_SECRET':'isolated-test-only-secret-at-least-32-bytes-long',
      'DATASOURCE_URL':'jdbc:mysql://127.0.0.1:23306/travelmate?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
      'DATASOURCE_USER':'travelmate','DATASOURCE_PASSWORD':'validation-only','REDIS_PORT':'26379',
      'RABBITMQ_PORT':'25672','RABBITMQ_USER':'travelmate','RABBITMQ_PASSWORD':'validation-only',
      'DASHSCOPE_API_KEY':'test-only','AI_BASE_URL':'http://127.0.0.1:18890','AMAP_WEB_KEY':''}
    p=subprocess.Popen([JAVA,'-jar',str(ROOT/'target/travelmate-backend-1.0.0.jar'),'--spring.profiles.active=prod,mq,redis',f'--server.port={port}','--server.address=127.0.0.1'],env=env,stdout=log,stderr=log);processes.append(p)
    for _ in range(100):
        try:
            if req(port,'GET','/api/health')[0]==200:return
        except Exception: pass
        if p.poll() is not None:raise RuntimeError('server startup failed')
        time.sleep(.2)
    raise RuntimeError('startup timeout')
provider=HTTPServer(('127.0.0.1',18890),Provider);threading.Thread(target=provider.serve_forever,daemon=True).start()
try:
    start(18788)
    check('MySQL production startup',True)
    check('production development-code login disabled',req(18788,'POST','/api/auth/login',{'phone':'13800000000','code':'246810'})[0]==503)
    phone='15'+str(int(time.time()))[-9:]
    user=req(18788,'POST','/api/auth/register',{'phone':phone,'password':'test-only-long-password'})[1]['data'];token=user['accessToken']
    for path in ['/api/catalog/cities','/api/catalog/routes/route-beijing-axis']:
        first=req(18788,'GET',path);second=req(18788,'GET',path)
        check('Redis repeated cache read '+path,first[0]==second[0]==200 and first[1]==second[1])
    start(18789)
    check('shared MySQL session on second instance',req(18789,'GET','/api/auth/sessions',token=token)[0]==200)
    job=req(18788,'POST','/api/ai/explanations/async',{'spotId':'1'},token)[1]['data']
    state={}
    for _ in range(40):
        state=req(18789,'GET','/api/ai/jobs/'+job['jobId'],token=token)[1]['data']
        if state['status'] in ['completed','failed']:break
        time.sleep(.25)
    check('RabbitMQ consumption and cross-instance job result',state.get('status')=='completed')
    team=req(18788,'POST','/api/teams',{},token)[1]['data']
    ws=subprocess.run(['node',str(SCRIPTS/'crossws.mjs'),str(team['teamId'])],env={**os.environ,'AUDIT_TOKEN':token},capture_output=True,text=True,timeout=12)
    check('Redis cross-instance team broadcast','received cross-instance' in ws.stdout)
    auth={'Authorization':'Basic '+base64.b64encode(b'travelmate:validation-only').decode()}
    with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:25673/api/queues/%2F/guide.job.dlq',headers=auth),timeout=3) as s:
        before=json.loads(s.read()).get('messages',0)
    Provider.failing=True
    failed=req(18788,'POST','/api/ai/explanations/async',{'spotId':'1'},token)[1]['data']
    for _ in range(40):
        state=req(18789,'GET','/api/ai/jobs/'+failed['jobId'],token=token)[1]['data']
        if state['status']=='failed':break
        time.sleep(.25)
    check('MQ failed job has persistent terminal state',state.get('status')=='failed')
    count=0
    for _ in range(12):
        r=urllib.request.Request('http://127.0.0.1:25673/api/queues/%2F/guide.job.dlq',headers={'Authorization':'Basic '+base64.b64encode(b'travelmate:validation-only').decode()})
        with urllib.request.urlopen(r,timeout=3) as s:count=json.loads(s.read()).get('messages',0)
        if count>before:break
        time.sleep(1)
    check('failed AI message routed to dead-letter queue',count>before)
finally:
    for p in processes:p.terminate()
    for p in processes:
        try:p.wait(timeout=10)
        except subprocess.TimeoutExpired:p.kill();p.wait()
    for log in logs:log.close()
    provider.shutdown();provider.server_close()
    (ROOT/'infra-results.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
sys.exit(0 if checks and all(r['pass'] for r in checks) else 1)
