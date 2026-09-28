export function heroSvg(tier=0) {
  const cloth=['#4b5351','#5b5a53','#4b5157','#313e49','#243943','#1c2732'][tier];
  const shirt=tier>=3?'#ded8c9':'#8c8576';
  const trim=tier>=4?'#c7a367':'#9a7155';
  const outer=tier>=4?'<path d="M-24 67 Q-39 83 -35 142 L-20 143 -11 88Z" fill="#29333a" stroke="#151c21" stroke-width="3"/>':'';
  const accessory=tier>=4?'<path d="M2 67 L7 92 L12 67" fill="#a67954" stroke="#0d1519" stroke-width="2"/><path d="M-21 111 l15 0" stroke="#d7b67a" stroke-width="3"/>':tier>=2?'<path d="M-14 79 l16 24 14-26" fill="none" stroke="#aa7962" stroke-width="4"/>':'';
  return `<g class="hero-sway" aria-label="Главный герой" transform="translate(0 0)">
    <ellipse cx="0" cy="185" rx="30" ry="8" fill="#080d12" opacity=".55"/>
    <path d="M-17 139 L-20 180 Q-17 187 -5 182 L1 142Z M5 139 L7 181 Q20 188 23 179 L19 135Z" fill="#273039" stroke="#10161b" stroke-width="3"/>
    <path d="M-25 176 q10 4 22 1 l2 8 h-29Z M7 177 q12 3 20-1 l4 9 H5Z" fill="${tier>=3?'#141b21':'#302c2a'}" stroke="#10161b" stroke-width="2"/>
    ${outer}<path d="M-22 69 Q-31 91 -21 145 Q1 151 22 143 Q30 91 19 68Z" fill="${cloth}" stroke="#11191c" stroke-width="4"/>
    <path d="M-8 69 L0 91 L10 70" fill="${shirt}" stroke="#202528" stroke-width="2"/>
    <path d="M-20 82 Q-31 103 -33 134 l9 5 13-40 M19 82 Q32 102 34 132 l-9 8 -14-42" fill="${cloth}" stroke="#11191c" stroke-width="4"/>
    <path d="M-34 132 l-3 12 11 4 5-12 M25 136 l3 11 11-4 -5-13" fill="#b4866b" stroke="#382c29" stroke-width="2"/>
    <path d="M-11 62 L-11 71 Q0 81 12 69 L11 58" fill="#b68469" stroke="#352d2a" stroke-width="2"/>
    <path d="M-19 20 Q-23 41 -16 57 Q-8 70 6 66 Q21 60 22 37 Q18 19 4 15Z" fill="#bd8b6d" stroke="#332926" stroke-width="3"/>
    <path d="M-20 32 Q-29 21 -18 8 Q-1 -5 19 9 Q25 17 22 29 Q7 18 -4 24 Q-12 25 -20 32Z" fill="${tier>=4?'#292724':'#3a302b'}" stroke="#1d1c1a" stroke-width="3"/>
    <path d="M-14 39 l9-2 M8 37 l9 2" stroke="#2d2828" stroke-width="3" stroke-linecap="round"/>
    <path d="M-11 43 q4 3 7 0 M8 43 q5 3 8 0" stroke="#241e1d" stroke-width="2" fill="none"/>
    <path d="M3 45 l-3 8 5 1 M-4 59 q8 ${tier>=3?'-2':'3'} 14-2" stroke="#704d43" stroke-width="2" fill="none" stroke-linecap="round"/>
    <path d="M-18 51 q-5 8 2 12" stroke="#855f52" stroke-width="2" fill="none"/>
    <path d="M-19 72 l10 12 8-9 9 10 10-13" fill="none" stroke="${trim}" stroke-width="2"/>
    ${accessory}
    ${tier>=5?'<path d="M-18 39 Q0 33 20 38" fill="none" stroke="#c5ad7d" stroke-width="3" opacity=".75"/>':''}
  </g>`;
}
