const tid=process.argv[2],token=process.env.AUDIT_TOKEN;
const ws=new WebSocket('ws://127.0.0.1:18788/ws');
const timer=setTimeout(()=>{console.log('timeout');ws.close();},7000);
ws.onopen=()=>ws.send(`CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${token}\n\n\0`);
ws.onmessage=async e=>{
 if(String(e.data).startsWith('CONNECTED')){
  ws.send(`SUBSCRIBE\nid:test\ndestination:/topic/teams/${tid}\n\n\0`);
  setTimeout(async()=>{
   await fetch(`http://127.0.0.1:18789/api/teams/${tid}/playback`,{method:'POST',headers:{'Authorization':'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({playbackStatus:'playing'})});
  },300);
 }else if(String(e.data).startsWith('MESSAGE')){console.log('received cross-instance');clearTimeout(timer);ws.close();}
};
