import numpy as np, wave
sr=44100; bpm=120; beat=60/bpm; T=27
n=int(sr*T); out=np.zeros(n)
def note(f,start,dur,vol=.18,kind='sq'):
    s=int(start*sr); L=int(dur*sr); t=np.arange(L)/sr
    w=np.sign(np.sin(2*np.pi*f*t))*.5+np.sin(2*np.pi*f*t)*.5 if kind=='sq' else np.sin(2*np.pi*f*t)
    env=np.minimum(1,t/0.01)*np.exp(-t*3)
    e=min(n,s+L); out[s:e]+=(w*env*vol)[:e-s]
def hz(m): return 440*2**((m-69)/12)
chords=[[60,64,67],[67,71,74],[69,72,76],[65,69,72]]  # C G Am F
mel=[76,74,72,74,76,76,76,None,74,74,74,None,76,79,79,None]
for bar in range(int(T/(4*beat))+1):
    c=chords[bar%4]; t0=bar*4*beat
    note(hz(c[0]-24),t0,4*beat,.25,'sin')
    for i in range(8): note(hz(c[i%3]),t0+i*beat/2,beat/2,.07)
    for i in range(4):  # bombo y caja
        s=int((t0+i*beat)*sr); L=int(.15*sr); tt=np.arange(L)/sr
        k=np.sin(2*np.pi*(120*np.exp(-tt*20)+45)*tt)*np.exp(-tt*18)*.5 if i%2==0 else np.random.randn(L)*np.exp(-tt*30)*.15
        e=min(n,s+L); out[s:e]+=k[:e-s]
    if bar>=2:
        for i,m in enumerate(mel[(bar%2)*8:(bar%2)*8+8]):
            if m: note(hz(m+12),t0+i*beat/2,beat/2,.06,'sin')
fade=np.ones(n); fl=int(1.2*sr); fade[-fl:]=np.linspace(1,0,fl)
out=out*fade; out/=np.abs(out).max()*1.25
d=(out*32767).astype(np.int16)
with wave.open('/tmp/musica.wav','wb') as w: w.setnchannels(1);w.setsampwidth(2);w.setframerate(sr);w.writeframes(d.tobytes())
