import { heroSvg } from './hero.js';

const esc = value => String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
function windows(x,y,cols,rows,w=23,h=33,gap=11,lit=0) {
  let out='';
  for(let r=0;r<rows;r++) for(let c=0;c<cols;c++) {
    const xx=x+c*(w+gap),yy=y+r*(h+gap),on=(r*7+c*11+lit)%5<2;
    out+=`<g class="window ${on?'window-lit':''}" transform="translate(${xx} ${yy})"><rect width="${w}" height="${h}" fill="${on?'url(#litWindow)':'#233038'}" stroke="#25282a" stroke-width="3"/><path d="M${w/2} 0v${h} M0 ${h*.54}h${w}" stroke="#1c2429" stroke-width="2"/><path d="M2 ${h-4}h${w-4}" stroke="#a9a9a0" opacity=".23"/></g>`;
  }
  return out;
}
function panelBlock({x,y,w,h,color,lit=0,sign='',tier=0}) {
  const cols=Math.max(2,Math.floor((w-30)/35)),rows=Math.max(2,Math.floor((h-52)/47));
  return `<g class="city-object building" data-object="building" transform="translate(${x} ${y})">
    <path d="M0 7 L${w-7} 0 L${w} ${h} H0Z" fill="${color}" stroke="#171d21" stroke-width="5"/>
    <path d="M-5 4 L${w-6} -2" stroke="#272b2d" stroke-width="9"/>
    <path d="M${w-7} 0 L${w+17} 13 V${h} H${w}" fill="#242b30" stroke="#171d21" stroke-width="2"/>
    <path d="M0 35 H${w} M0 ${Math.round(h*.52)}H${w} M${Math.round(w*.5)} 0V${h}" stroke="#262c2e" opacity=".35" stroke-width="3"/>
    ${windows(18,29,cols,rows,Math.min(23,(w-30)/cols-11),31,10,lit)}
    <path d="M12 ${h-34} Q${w*.4} ${h-51} ${w-8} ${h-18} L${w-8} ${h} H12Z" fill="#181e22" opacity=".31"/>
    ${sign?`<g transform="translate(${w*.12} ${h-60})"><rect width="${w*.76}" height="28" rx="2" fill="${tier>=3?'#a78a66':'#6f4837'}" stroke="#191c20" stroke-width="3"/><text x="${w*.38}" y="19" text-anchor="middle" fill="#f7e1ba" font-size="13" font-weight="900" letter-spacing="2">${esc(sign.toUpperCase())}</text></g>`:''}
    <path d="M5 ${h-10} l22-8 8 10 27-5 13 6 38-10 18 7" stroke="#4e514c" stroke-width="3" opacity=".6" fill="none"/>
    ${tier<3?'<path d="M13 124 q26-16 38 7 m54 48 q17-8 29 0" stroke="#262e2c" stroke-width="3" fill="none" opacity=".65"/>':''}
  </g>`;
}
function tower(x,y,w,h,tier) {
  return `<g class="city-object tower" data-object="tower" transform="translate(${x} ${y})"><path d="M0 12 L${w-18} 0 L${w} ${h} H0Z" fill="url(#glass)" stroke="#111b24" stroke-width="5"/><path d="M${w-18} 0 L${w+13} 16 L${w+22} ${h} H${w}" fill="#203443" stroke="#111b24" stroke-width="3"/><path d="M10 30 H${w-10} M10 75 H${w-7} M10 120 H${w-4} M10 165 H${w-3} M10 210 H${w-3} M10 255 H${w-2} M${Math.round(w*.5)} 4 V${h}" stroke="#9ab2bc" stroke-width="3" opacity=".37"/><path d="M14 20 L${w*.47} 13 L${w*.31} ${h-9} H14Z" fill="#e1c997" opacity=".13"/><text x="${w/2}" y="${h-17}" text-anchor="middle" fill="#e8c492" font-size="15" font-weight="900" letter-spacing="3">${tier===5?'РЕЗИДЕНЦИЯ':'БИЗНЕС'}</text></g>`;
}
function kiosk(tier) {
  return `<g class="city-object kiosk" data-object="kiosk" transform="translate(88 286)"><path d="M-8 24 H160 L154 118 H0Z" fill="${tier>=3?'#47494b':'#66564b'}" stroke="#151b1e" stroke-width="5"/><path d="M-14 25 L8 -5 H153 L174 25Z" fill="${tier>=3?'#9c765a':'#9f613d'}" stroke="#1a1c1d" stroke-width="5"/><rect x="15" y="42" width="68" height="46" fill="#c8af79" stroke="#1b252a" stroke-width="4"/><path d="M49 42v46 M15 65h68" stroke="#263038" stroke-width="3"/><rect x="94" y="42" width="45" height="76" fill="#263138" stroke="#10191e" stroke-width="4"/><circle cx="132" cy="83" r="3" fill="#c8b27d"/><rect x="5" y="-3" width="145" height="28" fill="#312b28" stroke="#151b1e" stroke-width="3"/><text x="77" y="16" fill="#f0bd7d" font-size="17" font-weight="900" letter-spacing="2" text-anchor="middle">${tier>=3?'КАФЕ • БАР':'24 ЧАСА'}</text><path d="M26 103h40 m-30 5h22" stroke="#30383a" stroke-width="3" opacity=".55"/></g>`;
}
function car(tier) {
  const color=tier>=4?'#22282d':tier>=2?'#535c60':'#6d6860';
  return `<g class="city-object car" data-object="vehicle" transform="translate(${tier>=4?675:652} 370)"><ellipse cx="104" cy="55" rx="117" ry="18" fill="#090c0d" opacity=".5"/><path d="M8 31 l27-4 24-26 h84 l31 29 29 8 7 27 H0 l2-22Z" fill="${color}" stroke="#111719" stroke-width="5"/><path d="M60 5 H139 l21 24 H40Z" fill="#41515a" stroke="#161d22" stroke-width="3"/><path d="M103 6v24" stroke="#151e22" stroke-width="3"/><path d="M17 42 h25 M173 43 h29" stroke="${tier>=4?'#d6c594':'#c29b7c'}" stroke-width="7" stroke-linecap="round"/><circle cx="44" cy="65" r="17" fill="#15191d" stroke="#858682" stroke-width="4"/><circle cx="163" cy="65" r="17" fill="#15191d" stroke="#858682" stroke-width="4"/><circle cx="44" cy="65" r="6" fill="#5e686b"/><circle cx="163" cy="65" r="6" fill="#5e686b"/><path d="M66 39 h66" stroke="#adb2ad" stroke-width="2" opacity=".5"/></g>`;
}
function lamp(x,y=118) {return `<g class="city-object lamp" data-object="lamp" transform="translate(${x} ${y})"><path d="M0 300 L8 23 Q8 0 37 0 H60" fill="none" stroke="#242b2c" stroke-width="9"/><path d="M6 300 L13 33" stroke="#71736d" stroke-width="2" opacity=".6"/><path d="M46 -4 h40 l-7 18 H52Z" fill="#535957" stroke="#1c2528" stroke-width="3"/><ellipse cx="66" cy="14" rx="19" ry="5" fill="#e9c381" opacity=".8"/><ellipse cx="66" cy="26" rx="49" ry="48" fill="url(#lampGlow)" opacity=".43"/></g>`;}
function powerLine() {return `<g class="city-object wires" data-object="wires" fill="none" stroke="#252b2f"><path d="M-10 98 Q330 166 910 108" stroke-width="2"/><path d="M-10 106 Q450 214 910 115" stroke-width="1.7"/><path d="M130 109 l2 140 M513 148 l-4 115" stroke-width="1.5"/></g>`;}
function streetDetails(tier) {return `<g class="street-details"><path d="M0 410 Q260 398 460 418 T900 414 V500 H0Z" fill="url(#road)" stroke="#222829" stroke-width="5"/><path d="M0 435 Q230 427 450 444 T900 439" fill="none" stroke="#aaa9a0" stroke-width="5" stroke-dasharray="65 80" opacity=".26"/><path d="M83 457 Q156 439 211 458 Q154 464 83 457 M514 468 Q618 451 681 468 Q610 478 514 468" fill="#80939a" opacity=".22"/><path d="M560 457 q58-17 88-7" stroke="#e6be7c" opacity=".27" stroke-width="4" fill="none"/>${tier<3?'<path d="M17 411 l43-12 37 10 29-7 28 9 M358 419 l33-8 46 11 18-6" fill="#b4b3ac" opacity=".47"/>':''}<g fill="#181d20" opacity=".85"><path d="M290 410 l9-20 8 20 M322 408 l3-16 7 17 M493 417 l7-21 7 22"/></g></g>`;}

export function citySvg(district,state) {
  const tier=district.tier,[mid,dark,warm]=district.palette;
  const heroTier=Math.min(5,Math.max(Math.floor(state.story/2),state.home==='estate'?5:state.home==='penthouse'?4:0));
  const buildings=tier>=4
    ?`${tower(25,115,193,306,tier)}${tower(222,45,202,370,tier)}${tower(609,99,244,316,tier)}`
    :`${panelBlock({x:8,y:117,w:211,h:296,color:mid,lit:2,tier})}${panelBlock({x:211,y:65,w:239,h:350,color:dark,lit:4,sign:tier>=2?'АРЕНДА':'',tier})}${panelBlock({x:632,y:103,w:250,h:307,color:mid,lit:7,tier})}`;
  const middle=tier>=4?`<g class="city-object club" data-object="club" transform="translate(436 276)"><path d="M0 0h170v138H0Z" fill="#26313a" stroke="#11191e" stroke-width="4"/><path d="M14 32h140 M14 72h140" stroke="#e1bc7f" stroke-width="3" opacity=".7"/><rect x="55" y="75" width="60" height="63" fill="#121b21" stroke="#d6b885" stroke-width="3"/><text x="85" y="25" text-anchor="middle" fill="#e7c18b" font-size="16" font-weight="900">ВЫСОТА</text></g>`:kiosk(tier);
  return `<svg class="city-svg" viewBox="0 0 900 500" preserveAspectRatio="xMidYMid slice" role="img" aria-label="Сцена района ${esc(district.name)}">
    <defs>
      <linearGradient id="sky" x2="0" y2="1"><stop stop-color="${tier>=4?'#304450':'#3b454b'}"/><stop offset=".68" stop-color="${tier>=4?'#657782':'#6a7071'}"/><stop offset="1" stop-color="${tier>=4?'#a48f78':'#847971'}"/></linearGradient>
      <linearGradient id="road" x2="0" y2="1"><stop stop-color="#282e30"/><stop offset="1" stop-color="#0e1519"/></linearGradient>
      <linearGradient id="glass" x2="1" y2=".3"><stop stop-color="#2b424e"/><stop offset=".45" stop-color="#54707b"/><stop offset=".46" stop-color="#334b58"/><stop offset="1" stop-color="#1e3441"/></linearGradient>
      <linearGradient id="litWindow" x2="1" y2="1"><stop stop-color="#efcd89"/><stop offset="1" stop-color="#86694c"/></linearGradient>
      <radialGradient id="lampGlow"><stop stop-color="#efc681" stop-opacity=".76"/><stop offset="1" stop-color="#efc681" stop-opacity="0"/></radialGradient>
      <filter id="grain"><feTurbulence type="fractalNoise" baseFrequency=".5" numOctaves="3" seed="2" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/><feComponentTransfer><feFuncA type="linear" slope=".11"/></feComponentTransfer></filter>
      <filter id="blur"><feGaussianBlur stdDeviation="8"/></filter>
    </defs>
    <g class="city-object sky" data-object="sky"><rect width="900" height="500" fill="url(#sky)"/><ellipse cx="760" cy="98" rx="147" ry="53" fill="${warm}" opacity=".13" filter="url(#blur)"/></g>
    <g class="city-object skyline" data-object="skyline" fill="#3b4649" opacity=".45"><path d="M0 270 h47v-89h57v107h19V224h83v71h31V191h74v104h35V235h78v59h51V215h85v79h26v-70h95v65h42V190h83v108h31V241h62v56h45V205h50v96H900V410H0Z"/></g>
    ${powerLine()}
    <g class="buildings">${buildings}</g>
    <path d="M0 390 Q398 382 900 388 L900 420 Q465 410 0 421Z" fill="#424748" stroke="#212729" stroke-width="4"/>
    ${middle}
    <g class="city-object poster" data-object="poster" transform="translate(${tier>=4?274:259} 300) rotate(-3)"><rect width="73" height="88" fill="#c1b298" stroke="#252a29" stroke-width="2"/><path d="M4 6h65v26H4Z" fill="${warm}" opacity=".8"/><text x="36" y="24" text-anchor="middle" fill="#252524" font-size="9" font-weight="900">${tier>=3?'УСПЕХ':'РАБОТА'}</text><path d="M12 45h50 M12 53h45 M12 61h49 M12 69h31" stroke="#55534c" stroke-width="2" opacity=".8"/></g>
    ${lamp(560,92)}
    ${streetDetails(tier)}
    ${car(tier)}
    <g class="hero-object" data-object="hero" transform="translate(452 242)">${heroSvg(heroTier)}</g>
    <g class="city-object trash" data-object="trash" transform="translate(29 390)"><path d="M0 0l9 25 25-2 9-25Z" fill="#313a3c" stroke="#161d20" stroke-width="3"/><path d="M-4 -4h49" stroke="#161d20" stroke-width="6"/><path d="M8-13h17l7 9H1Z" fill="#514f47" stroke="#161d20" stroke-width="2"/></g>
    <g class="city-object foreground" data-object="foreground"><path d="M0 475 q53-18 100 8 h73 q48-16 79 6 M615 490 q44-20 81-6 h122q44-16 90 2" stroke="#656865" stroke-width="4" opacity=".3" fill="none"/><path d="M178 458 l7 7 22-3 M337 471 l19-8 8 13 M720 451 l14 8 21-2" fill="none" stroke="#777a74" opacity=".35" stroke-width="3"/></g>
    <rect width="900" height="500" filter="url(#grain)" opacity=".48" pointer-events="none"/>
  </svg>`;
}
