import hashlib, urllib.request, urllib.error, http.cookiejar, json, time
HOST='192.168.1.1'; USER='admin'; PASS=__import__('os').environ.get('RCI_PASS','CHANGE_ME')
jar=http.cookiejar.CookieJar()
op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
try: op.open('http://'+HOST+'/auth',timeout=5)
except urllib.error.HTTPError as e:
    realm=e.headers.get('X-NDM-Realm','') or ''
    chal=e.headers.get('X-NDM-Challenge','') or ''
md5=hashlib.md5((USER+':'+realm+':'+PASS).encode()).hexdigest()
sha=hashlib.sha256((chal+md5).encode()).hexdigest()
op.open(urllib.request.Request('http://'+HOST+'/auth',data=json.dumps({'login':USER,'password':sha}).encode(),headers={'Content-Type':'application/json'}),timeout=5)
print('AUTH OK')

SHOW_PATHS = [
    'show/version','show/interface','show/interface/stat',
    'show/media','show/media/Media0',
    'show/ip/route','show/ip/dhcp','show/ip/host','show/ip/name-server',
    'show/ip/arp','show/ip6/route','show/ip6/name-server',
    'show/system','show/cpu','show/log','show/ndns','show/mws',
    'show/components','show/user','show/known/hosts',
    'show/associations/wifi','show/running-config','show/startup-config',
    'show/counters','show/sk/cli','show/cli',
]
CLI_PARSES = ['show running-config','show interface','show media','show version','show log']

result = {'host':HOST,'show':{},'parse':{}}

for p in SHOW_PATHS:
    try:
        r=op.open('http://'+HOST+'/rci/'+p,timeout=6)
        body=r.read().decode()
        keys=list(json.loads(body).keys())[:5] if body.strip().startswith('{') else []
        result['show'][p]={'status':r.status,'size':len(body),'keys':keys}
        print(f'OK  {p}  size={len(body)}  keys={keys}')
    except urllib.error.HTTPError as e:
        result['show'][p]={'status':e.code}
        print(f'{e.code} {p}')
    except Exception as ex:
        result['show'][p]={'err':str(ex)}
        print(f'ERR {p}: {ex}')
    time.sleep(0.2)

print('\n--- parse CLI ---')
for cmd in CLI_PARSES:
    try:
        body=json.dumps({'parse':cmd}).encode()
        r=op.open(urllib.request.Request('http://'+HOST+'/rci/',data=body,headers={'Content-Type':'application/json'},method='POST'),timeout=8)
        txt=r.read().decode()
        result['parse'][cmd]={'status':r.status,'size':len(txt),'sample':txt[:200]}
        print(f'OK  parse "{cmd}"  size={len(txt)}')
    except urllib.error.HTTPError as e:
        result['parse'][cmd]={'status':e.code}
        print(f'{e.code} parse "{cmd}"')
    except Exception as ex:
        result['parse'][cmd]={'err':str(ex)}
        print(f'ERR parse "{cmd}": {ex}')
    time.sleep(0.2)

open('/data/data/com.termux/files/home/rci_api.json','w').write(json.dumps(result,indent=2,ensure_ascii=False))
print('\nSaved: ~/rci_api.json')
