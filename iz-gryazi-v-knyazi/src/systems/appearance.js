// A person's portrait is resolved from their actual age, never from the
// player's progress or the clothes currently on screen.
export const ageDecade=age=>Math.max(20,Math.min(90,Math.floor(Math.max(0,age)/10)*10));
export const ageAppearance=age=>{
  const decade=ageDecade(age);
  const next=Math.min(90,decade+10);
  const blend=next===decade?0:Math.max(0,Math.min(1,(age-decade)/10));
  return {decade,next,blend};
};
export const ageStyle=age=>{
  const intensity=Math.max(0,Math.min(1,(age-30)/60));
  return `--age-gray:${intensity.toFixed(3)};--age-wrinkles:${Math.pow(intensity,1.35).toFixed(3)};--age-sat:${(1-intensity*.35).toFixed(3)};--age-contrast:${(1+intensity*.12).toFixed(3)}`;
};
