export class AudioDirector {
  constructor(settings = {}) {
    this.ctx = null;
    this.master = null;
    this.musicGain = null;
    this.sfxGain = null;
    this.musicOn = settings.music !== false;
    this.sfxOn = settings.sfx !== false;
    this.running = false;
    this.nodes = [];
    this.beatTimer = null;
    this.phase = 0;
  }

  ensure() {
    if (this.ctx) return;
    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return;
    this.ctx = new Ctx();
    this.master = this.ctx.createGain();
    this.master.gain.value = .72;
    this.master.connect(this.ctx.destination);
    this.musicGain = this.ctx.createGain();
    this.musicGain.gain.value = this.musicOn ? .22 : 0;
    this.musicGain.connect(this.master);
    this.sfxGain = this.ctx.createGain();
    this.sfxGain.gain.value = this.sfxOn ? .52 : 0;
    this.sfxGain.connect(this.master);
  }

  async resume() {
    this.ensure();
    try { await this.ctx?.resume(); } catch (_) {}
  }
  async suspend() { try { await this.ctx?.suspend(); } catch (_) {} }

  setMusic(v) {
    this.musicOn = Boolean(v); this.ensure();
    if (this.musicGain && this.ctx) this.musicGain.gain.setTargetAtTime(this.musicOn ? .22 : 0, this.ctx.currentTime, .08);
  }
  setSfx(v) {
    this.sfxOn = Boolean(v); this.ensure();
    if (this.sfxGain && this.ctx) this.sfxGain.gain.setTargetAtTime(this.sfxOn ? .52 : 0, this.ctx.currentTime, .04);
  }

  startMusic() {
    this.ensure();
    if (!this.ctx || this.running) return;
    this.running = true;
    const now = this.ctx.currentTime;
    const freqs = [55,82.41,110];
    freqs.forEach((f,i)=>{
      const osc=this.ctx.createOscillator(), gain=this.ctx.createGain(), filter=this.ctx.createBiquadFilter();
      osc.type=i===0?'sine':'triangle'; osc.frequency.value=f;
      filter.type='lowpass'; filter.frequency.value=330+i*120; filter.Q.value=.7;
      gain.gain.value=i===0?.045:.022;
      osc.connect(filter); filter.connect(gain); gain.connect(this.musicGain); osc.start(now);
      this.nodes.push({osc,gain,filter});
    });
    this.scheduleBeat();
  }

  stopMusic() {
    this.running=false;
    clearTimeout(this.beatTimer); this.beatTimer=null;
    for(const n of this.nodes){try{n.osc.stop();}catch(_){}}
    this.nodes=[];
  }

  scheduleBeat() {
    if (!this.running || !this.ctx) return;
    const seq=[0,0,7,3,0,10,7,3];
    const base=110*Math.pow(2,(seq[this.phase%seq.length])/12);
    this.phase++;
    this.tone(base,.12,.018,'triangle',this.musicGain);
    if(this.phase%4===1)this.noise(.055,.014,this.musicGain,150);
    this.beatTimer=setTimeout(()=>this.scheduleBeat(),430);
  }

  tone(freq,dur=.1,vol=.08,type='sine',dest=this.sfxGain,slide=1) {
    this.ensure(); if(!this.ctx||!dest)return;
    const t=this.ctx.currentTime, o=this.ctx.createOscillator(), g=this.ctx.createGain();
    o.type=type; o.frequency.setValueAtTime(freq,t); o.frequency.exponentialRampToValueAtTime(Math.max(20,freq*slide),t+dur);
    g.gain.setValueAtTime(Math.max(.0001,vol),t); g.gain.exponentialRampToValueAtTime(.0001,t+dur);
    o.connect(g); g.connect(dest); o.start(t); o.stop(t+dur+.02);
  }

  noise(dur=.08,vol=.04,dest=this.sfxGain,cutoff=800) {
    this.ensure(); if(!this.ctx||!dest)return;
    const len=Math.max(1,Math.floor(this.ctx.sampleRate*dur)), b=this.ctx.createBuffer(1,len,this.ctx.sampleRate), d=b.getChannelData(0);
    for(let i=0;i<len;i++)d[i]=(Math.random()*2-1)*(1-i/len);
    const s=this.ctx.createBufferSource(), f=this.ctx.createBiquadFilter(), g=this.ctx.createGain();
    s.buffer=b; f.type='lowpass'; f.frequency.value=cutoff; g.gain.value=vol; s.connect(f); f.connect(g); g.connect(dest); s.start();
  }

  attack(){ if(!this.sfxOn)return; this.tone(180,.07,.075,'sawtooth',this.sfxGain,.55); this.noise(.045,.025,this.sfxGain,1500); }
  hit(){ if(!this.sfxOn)return; this.tone(95,.065,.09,'square',this.sfxGain,.6); }
  kill(){ if(!this.sfxOn)return; this.tone(140,.13,.06,'triangle',this.sfxGain,1.7); }
  pickup(){ if(!this.sfxOn)return; this.tone(520,.075,.035,'sine',this.sfxGain,1.28); }
  level(){ if(!this.sfxOn)return; [392,523,659].forEach((f,i)=>setTimeout(()=>this.tone(f,.18,.055,'triangle'),i*70)); }
  dash(){ if(!this.sfxOn)return; this.noise(.12,.055,this.sfxGain,900); this.tone(220,.12,.04,'sine',this.sfxGain,.45); }
  hurt(){ if(!this.sfxOn)return; this.tone(78,.18,.095,'sawtooth',this.sfxGain,.65); }
  boss(){ if(!this.sfxOn)return; [82,62,48].forEach((f,i)=>setTimeout(()=>this.tone(f,.45,.11,'sawtooth',this.sfxGain,.72),i*130)); }
  victory(){ if(!this.sfxOn)return; [262,330,392,523].forEach((f,i)=>setTimeout(()=>this.tone(f,.34,.06,'triangle'),i*120)); }
}
