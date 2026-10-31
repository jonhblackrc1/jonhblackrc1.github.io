# Música motivadora original de 10 s, 128 BPM: intro con redoble, groove épico y golpe final.
import numpy as np, wave
sr=44100; bpm=128; b=60/bpm; T=10; n=int(sr*T)
L=np.zeros(n); R=np.zeros(n); rng=np.random.default_rng(1)
def add(sig,start,pan=0.0):
    s=int(start*sr); e=min(n,s+len(sig))
    if e<=s: return
    L[s:e]+=sig[:e-s]*(1-pan); R[s:e]+=sig[:e-s]*(1+pan)
def hz(m): return 440*2**((m-69)/12)
def saw(f,t): return 2*((f*t)%1)-1
def brass(m,start,dur,vol=.09,pan=0):
    t=np.arange(int(dur*sr))/sr; f=hz(m)
    w=sum(saw(f*d,t) for d in (0.995,1,1.005))/3
    env=np.minimum(1,t/0.03)*np.minimum(1,(dur-t)/0.05)*(0.75+0.25*np.exp(-t*4))
    add(w*env*vol,start,pan)
def lead(m,start,dur,vol=.12):
    t=np.arange(int(dur*sr))/sr; f=hz(m)
    w=np.sign(np.sin(2*np.pi*f*t*(1+0.004*np.sin(2*np.pi*5.5*t))))*.6+np.sin(2*np.pi*f*t)*.4
    add(w*np.minimum(1,t/0.01)*np.minimum(1,(dur-t)/0.03)*vol,start)
def kick(start,vol=.9):
    t=np.arange(int(.25*sr))/sr
    add(np.sin(2*np.pi*(50+110*np.exp(-t*25))*t)*np.exp(-t*12)*vol,start)
def snare(start,vol=.35):
    t=np.arange(int(.2*sr))/sr
    add((rng.standard_normal(len(t))*np.exp(-t*22)+np.sin(2*np.pi*190*t)*np.exp(-t*30))*vol,start)
def hat(start,vol=.07):
    t=np.arange(int(.05*sr))/sr; add(np.diff(rng.standard_normal(len(t)+1))*np.exp(-t*80)*vol,start,.3)
def crash(start,vol=.25):
    t=np.arange(int(2.2*sr))/sr; add(np.diff(rng.standard_normal(len(t)+1))*np.exp(-t*2)*vol,start)
bar=4*b
# Intro (compás 0): redoble que acelera + riser
for i,tt in enumerate(np.concatenate([np.arange(0,2*b,b/2),np.arange(2*b,3*b,b/4),np.arange(3*b,4*b,b/8)])):
    snare(tt,0.12+0.28*tt/bar)
t=np.arange(int(bar*sr))/sr
add(np.sin(2*np.pi*(200+800*(t/bar)**2)*t)*0.05*(t/bar),0)
brass(62,0,bar,0.05)
# Groove (compases 1-3): D A Bm G
prog=[[50,62,66,69],[45,61,64,69],[47,62,66,71],[43,62,67,71]]
mel=[(74,0,.5),(76,.5,.5),(78,1,1),(81,2,.75),(78,2.75,.25),(76,3,1),
     (73,4,.5),(74,4.5,.5),(76,5,1),(78,6,.5),(76,6.5,.5),(81,7,1),
     (83,8,1.5),(81,9.5,.5),(78,10,1),(81,11,1)]
for k in range(3):
    t0=bar*(k+1)
    if k==0: crash(t0)
    for j in range(2):
        c=prog[(k*2+j)%4]; h=t0+j*2*b
        for x in range(4): brass(c[0]-12 if x%2==0 else c[0],h+x*b/2*1,b/2*0.9,0.16)  # bajo
        for x in range(4): brass(c[0]-12,h+x*b/2+2*b/2*0,b/2*0.9,0.0)
        for m in c[1:]: brass(m,h,2*b*0.95,0.07,pan=(m%3-1)*.4)
    for x in range(4): kick(t0+x*b)
    for x in (1,3): snare(t0+x*b,.3); snare(t0+x*b+0.01,.15)
    for x in range(8): hat(t0+x*b/2+b/4)
    # bajo en corcheas
    for x in range(8):
        c=prog[(k*2+x//4)%4]; brass(c[0]-12,t0+x*b/2,b/2*0.85,0.15)
for m,st,du in mel: lead(m,bar+st*b,du*b*0.95)
# relleno de tambores antes del golpe final
for x in range(4): snare(bar*4-b+x*b/4,.25)
# Golpe final (7.5 s): acorde triunfal sostenido
t0=bar*4; crash(t0,.3); kick(t0,1.0)
for m in (38,50,62,66,69,74,78): brass(m,t0,10-t0,0.08 if m>50 else 0.15)
lead(86,t0,1.2,.1); lead(81,t0+1.2,1.2,.08)
for x in range(3): kick(t0+b*2*(x+1),.6); snare(t0+b*2*(x+1),.2)
fade=np.ones(n); fl=int(.6*sr); fade[-fl:]=np.linspace(1,0,fl)
st=np.stack([L,R],1)*fade[:,None]; st=np.tanh(st*1.3); st/=np.abs(st).max()*1.12
with wave.open('/tmp/musica10.wav','wb') as w:
    w.setnchannels(2);w.setsampwidth(2);w.setframerate(sr);w.writeframes((st*32767).astype(np.int16).tobytes())
