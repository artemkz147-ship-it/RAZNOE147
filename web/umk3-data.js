(() => {
'use strict';
const M=(name,cmd,type,damage=10,extra={})=>({name,cmd,type,damage,...extra});
const C=(name,seq,damage)=>({name,seq,damage});
const F=(id,name,primary,dark,style,moves,combos,fatalities,extra={})=>({
  id,name,primary,dark,style,moves,combos,fatalities,...extra
});
const fighters = [
F('kitana','KITANA','#2f63d5','#071637','femaleNinja',[
 M('Fan Lift',['B','B','HP'],'lift',0,{range:420}),
 M('Fan Throw',['F','F','HP+LP'],'projectile',10,{projectile:'fan',air:true}),
 M('Square Wave Punch',['D','DB','B','HP'],'dash',12,{speed:760})
],[C('Fan Slice',['HP','HP','B+LP','F+HP'],25),C('Kick Combo',['HK','HK','LK','B+HK'],23)],[
 M('Decapitating Fan',['B','D','F','F','HK'],'fatality',0,{fatal:'slice',distance:'close'}),
 M('Kiss of Doom',['RUN','RUN','BL','BL','LK'],'fatality',0,{fatal:'inflate',distance:'close'})
],{gender:'female'}),
F('reptile','REPTILE','#3faa4a','#07240c','ninja',[
 M('Running Serpent',['B','F','LK'],'dash',11,{speed:820}),
 M('Acid Spit',['F','F','HP'],'projectile',10,{projectile:'acid'}),
 M('Slide',['B','LP+LK+BL'],'slide',12),
 M('Invisibility',['U','D','HK'],'invisible',0),
 M('Slow Force Ball',['B','B','HP+LP'],'projectile',7,{projectile:'forceSlow'}),
 M('Fast Force Ball',['F','F','HP+LP'],'projectile',7,{projectile:'forceFast'})
],[C('Ninja Chain',['HP','HP','HK','B+HK'],23),C('Uppercut Chain',['HP','HP','D+LP'],18)],[
 M('Extra Meal',['B','B','F','D','BL'],'fatality',0,{fatal:'devour',distance:'sweep'}),
 M('Acid Bath',['F','F','U','U','HK'],'fatality',0,{fatal:'acid',distance:'sweep'})
]),
F('sonya','SONYA','#75a64a','#12240a','soldierFemale',[
 M('Energy Rings',['D','DF','F','LP'],'projectile',10,{projectile:'rings'}),
 M('Bicycle Kick',['B','B','D','HK'],'multiKick',14),
 M('Leg Grab',['D','LP+BL'],'grab',13),
 M('Square Wave Punch',['F','B','HP'],'dash',11,{speed:750})
],[C('Uppercut Combo',['HP','HP','U+LP'],18),C('Special Forces',['HK','HK','HP','HP','LP','B+HP'],31)],[
 M('Kiss of Fire',['B','F','D','D','RUN'],'fatality',0,{fatal:'burn',distance:'any'}),
 M('Energy Net',['BL+RUN','U','U','B','D'],'fatality',0,{fatal:'net',distance:'mid'})
],{gender:'female'}),
F('jax','JAX','#8b5b43','#17100c','jax',[
 M('Missile',['B','F','HP'],'projectile',9,{projectile:'missile'}),
 M('Double Missile',['F','F','B','B','HP'],'projectileBurst',14,{projectile:'missile',count:2}),
 M('Blazing Punch',['F','F','HK'],'dash',13,{speed:820}),
 M('Gotcha Grab',['F','F','LP'],'grabMulti',15),
 M('Earthquake Smash',['D','D','LK'],'ground',12)
],[C('Bionic Chain',['HP','HP','BL','LP','B+HP'],24),C('Heavy Chain',['HK','HK','D+HP','HP','BL','LP','B+HP'],33)],[
 M('Arm Blades',['U','U','D','F','BL'],'fatality',0,{fatal:'slice',distance:'close'}),
 M('Giant Stomp',['RUN','BL','RUN','RUN','LK'],'fatality',0,{fatal:'stomp',distance:'mid'})
]),
F('nightwolf','NIGHTWOLF','#2b7c72','#08201d','nightwolf',[
 M('Spirit Arrow',['D','B','LP'],'projectile',9,{projectile:'arrow'}),
 M('Hatchet Uppercut',['D','DF','F','HP'],'uppercutSpecial',12),
 M('Reflector',['B','B','B','HK'],'reflect',0),
 M('Shadow Ram',['F','F','LK'],'dash',13,{speed:860})
],[C('Hatchet Chain',['HP','HP','LP','SPECIAL'],23),C('Spirit Combo',['LK','HP','HP','LP','HK'],34)],[
 M('Spirit Beam',['BL','U','U','B','F','BL'],'fatality',0,{fatal:'beam',distance:'close'}),
 M('Lightning Axe',['B','B','D','HP'],'fatality',0,{fatal:'lightning',distance:'mid'})
]),
F('jade','JADE','#2c9e66','#092019','femaleNinja',[
 M('High Boomerang',['B','F','HP'],'projectile',9,{projectile:'boomerangHigh'}),
 M('Mid Boomerang',['B','F','LP'],'projectile',9,{projectile:'boomerang'}),
 M('Low Boomerang',['B','F','LK'],'projectile',9,{projectile:'boomerangLow'}),
 M('Returning Boomerang',['B','B','F','LP'],'projectile',10,{projectile:'boomerangReturn'}),
 M('Jade Kick',['D','F','LK'],'dash',12,{speed:850}),
 M('Projectile Glow',['B','F','HK'],'reflect',0,{duration:2.5})
],[C('Staff Chain',['HP','HP','D+LP','D+HP'],23),C('Royal Kick',['HK','HK','LK','B+HK'],24)],[
 M('Staff Impale',['U','U','D','F','HP'],'fatality',0,{fatal:'impale',distance:'close'}),
 M('Boomerang Cut',['RUN','RUN','RUN','BL','RUN'],'fatality',0,{fatal:'slice',distance:'mid'})
],{gender:'female'}),
F('scorpion','SCORPION','#e0a31a','#261909','ninja',[
 M('Spear',['B','B','LP'],'spear',6,{stun:1.2}),
 M('Teleport Punch',['D','B','HP'],'teleport',11,{air:true}),
 M('Air Throw',['BL'],'airThrow',12,{air:true})
],[C('Spear Kick Chain',['HK','HK','LK','LK'],28),C('Axe Kick Chain',['HP','HP','HK','B+HK'],28)],[
 M('Toasty',['D','D','U','HK'],'fatality',0,{fatal:'burn',distance:'jump'}),
 M('Hell Horde',['F','F','D','U','RUN'],'fatality',0,{fatal:'hell',distance:'close'})
]),
F('kano','KANO','#7d5b49','#17100d','kano',[
 M('Cannonball',['B','F','LK'],'roll',12),
 M('Upward Cannonball',['F','D','F','HK'],'uppercutSpecial',12),
 M('Knife Throw',['D','B','HP'],'projectile',9,{projectile:'knife'}),
 M('Knife Uppercut',['D','F','HP'],'uppercutSpecial',11),
 M('Choke Hold',['B','D','F','LP'],'grab',13)
],[C('Knife Chain',['HP','HP','D+LP','D+HP'],22),C('Kick Chain',['HK','HK','LK','B+HK'],23)],[
 M('Skeleton Rip',['LP','F','D','D','F','LP'],'fatality',0,{fatal:'rip',distance:'close'}),
 M('Laser Heat',['LP','BL','BL','HK'],'fatality',0,{fatal:'laser',distance:'sweep'})
]),
F('mileena','MILEENA','#b83d98','#310929','femaleNinja',[
 M('Roll',['B','B','D','HK'],'roll',11),
 M('Sai Shot',['B','F','HP'],'projectile',9,{projectile:'sai',air:true}),
 M('Teleport Kick',['F','F','LK'],'teleportKick',12,{air:true})
],[C('Tarkatan Chain',['HP','HP','HK','HK','U+LK','U+HK'],26),C('Kick Chain',['HK','HK','LK','B+HK'],23)],[
 M('Tack Storm',['B','B','B','F','LK'],'fatality',0,{fatal:'projectileStorm',distance:'any'}),
 M('Kiss Devour',['D','F','D','F','LP'],'fatality',0,{fatal:'devour',distance:'close'})
],{gender:'female'}),
F('ermac','ERMAC','#b9252f','#280608','ninja',[
 M('Fire Teleport',['D','B','HP'],'teleport',11),
 M('Green Sphere',['D','DB','B','LP'],'projectile',9,{projectile:'soul'}),
 M('Soul Slam',['B','D','B','HK'],'telekinesis',12,{range:520})
],[C('Soul Chain',['HP','HP','B+LP','F+LP'],19),C('Red Ninja Chain',['HK','HK','LK','B+HK'],23)],[
 M('Telekinetic Slam',['D','U','D','D','D','BL'],'fatality',0,{fatal:'slam',distance:'sweep'}),
 M('Head Strike',['RUN','BL','RUN','RUN','HK'],'fatality',0,{fatal:'decap',distance:'close'})
]),
F('classic-subzero','CLASSIC SUB-ZERO','#54a8df','#0c1a26','ninja',[
 M('Freeze',['D','DF','F','LP'],'projectile',0,{projectile:'freeze',freeze:2}),
 M('Ground Freeze',['D','DB','B','LK'],'groundFreeze',0),
 M('Slide',['B','LP+LK+BL'],'slide',12)
],[C('Classic Chain',['HP','HP','D+LP','D+HP'],22),C('Ice Finish',['HP','HP','LK','B+HK'],22)],[
 M('Classic Rip',['D','D','D','F','HP'],'fatality',0,{fatal:'decap',distance:'close'})
],{secret:true}),
F('subzero','SUB-ZERO','#2f91d2','#061a2e','subzero',[
 M('Ice Shower Front',['D','DF','B','HP'],'iceShower',0,{offset:100}),
 M('Ice Shower Back',['D','DB','F','HP'],'iceShower',0,{offset:-100}),
 M('Ice Shower Top',['D','DF','F','HP'],'iceShower',0,{offset:0}),
 M('Ice Clone',['D','DB','B','LP'],'clone',0,{air:true}),
 M('Ice Freeze',['D','DF','F','LP'],'projectile',0,{projectile:'freeze',freeze:2}),
 M('Slide',['B','LP+LK+BL'],'slide',12)
],[C('Unmasked Chain',['HK','HK','B+HK'],18),C('Lin Kuei Chain',['HP','HP','B+LP','B+LK','B+HK','B+HK'],23)],[
 M('Frozen Break',['BL','BL','RUN','BL','RUN'],'fatality',0,{fatal:'freezeBreak',distance:'close'}),
 M('Shatter Breath',['B','B','D','B','RUN'],'fatality',0,{fatal:'freezeShatter',distance:'sweep'})
]),
F('sektor','SEKTOR','#b52c27','#2b0706','cyborg',[
 M('Heat Missile',['D','DB','B','HP'],'projectile',11,{projectile:'heatMissile',tracking:true}),
 M('Straight Missile',['F','F','LP'],'projectile',10,{projectile:'missile'}),
 M('Teleport Punch',['F','F','LK'],'teleport',11,{air:true})
],[C('Cyber Chain',['HP','HP','LK','LP'],24),C('Missile Juggle',['HP','HP','D+HP','HK'],25)],[
 M('Compactor',['LP','RUN','RUN','BL'],'fatality',0,{fatal:'crush',distance:'sweep'}),
 M('Flamethrower',['F','F','F','B','BL'],'fatality',0,{fatal:'burn',distance:'any'})
],{robot:true}),
F('sindel','SINDEL','#754e93','#17101e','sindel',[
 M('Air Fireball',['B','DB','D','DF','F','LK'],'projectile',10,{projectile:'purpleFire',air:true}),
 M('Ground Fireball',['F','F','LP'],'projectile',9,{projectile:'purpleFire'}),
 M('Scream',['F','F','F','HP'],'stunWave',0,{stun:1.4}),
 M('Flight',['B','B','F','HK'],'flight',0)
],[C('Queen Chain',['HK','HP','HP','LP','HK'],33),C('Air Queen',['HK','HP','HP','D+HP','HK'],40)],[
 M('Sonic Skin',['RUN','RUN','BL','BL','RUN+BL'],'fatality',0,{fatal:'scream',distance:'close'}),
 M('Hair Spin',['RUN','RUN','BL','RUN','BL'],'fatality',0,{fatal:'hair',distance:'sweep'})
],{gender:'female'}),
F('stryker','STRYKER','#5d6270','#101217','stryker',[
 M('High Grenade',['D','DB','B','HP'],'projectile',10,{projectile:'grenadeHigh'}),
 M('Low Grenade',['D','DB','B','LP'],'projectile',10,{projectile:'grenadeLow'}),
 M('Baton Throw',['F','F','HK'],'projectile',10,{projectile:'baton'}),
 M('Baton Trip',['F','B','LP'],'lowStrike',10),
 M('Gun Shot',['B','F','HP'],'projectile',8,{projectile:'bullet',fast:true})
],[C('Riot Chain',['LK','HP','HP','D+LP'],23),C('Gun Juggle',['HK','HP','HP','D+LP','SPECIAL'],40)],[
 M('Explosive Charge',['D','F','D','F','BL'],'fatality',0,{fatal:'bomb',distance:'close'}),
 M('Taser',['F','F','F','LK'],'fatality',0,{fatal:'electric',distance:'mid'})
]),
F('cyrax','CYRAX','#d2ad18','#292204','cyborg',[
 M('Net',['B','B','LK'],'projectile',0,{projectile:'net',stun:1.5}),
 M('Exploding Teleport',['F','D','BL'],'teleportExplosion',12,{air:true}),
 M('Close Bomb',['B','B','HK'],'bomb',10,{offset:170}),
 M('Far Bomb',['F','F','HK'],'bomb',10,{offset:360})
],[C('Cyber Net Chain',['HP','HP','HK','HP'],24),C('Extended Cyber',['HP','HP','HK','HP','HK','B+HK'],30)],[
 M('Helicopter',['D','D','U','D','HP'],'fatality',0,{fatal:'slice',distance:'any'}),
 M('Self Destruct',['D','D','F','U','RUN'],'fatality',0,{fatal:'selfDestruct',distance:'close'})
],{robot:true}),
F('kung-lao','KUNG LAO','#2c3f69','#0d1016','kunglao',[
 M('Hat Throw',['B','F','LP'],'projectile',10,{projectile:'hat'}),
 M('Teleport',['D','U'],'teleport',0),
 M('Teleport Punch',['D','U','HP'],'teleport',11),
 M('Teleport Kick',['D','U','HK'],'teleportKick',12),
 M('Dive Kick',['D','HK'],'diveKick',12,{air:true}),
 M('Shield Spin',['F','DF','D','DF','F','RUN'],'spin',14)
],[C('Hat Chain',['HP','LP','HP','LP','LK','LK','B+HK'],34),C('Short Chain',['HP','LK','B+HK'],19)],[
 M('Whirlwind',['RUN','BL','RUN','BL','D'],'fatality',0,{fatal:'spin',distance:'any'}),
 M('Hat Saw',['F','F','B','D','HP'],'fatality',0,{fatal:'slice',distance:'close'})
]),
F('kabal','KABAL','#6b604c','#14120f','kabal',[
 M('Tornado Spin',['B','F','LK'],'spinStun',0,{stun:1.3}),
 M('Purple Energy Ball',['B','B','HP'],'projectile',10,{projectile:'purpleBall',air:true}),
 M('Ground Blade',['B','B','B','RUN'],'groundBlade',11)
],[C('Hook Chain',['LK','LK','HP','HP','D+LP','D+HP'],37),C('Mask Chain',['HP','HP','HK','HK','B+HK'],25)],[
 M('Inflator',['D','D','B','F','BL'],'fatality',0,{fatal:'inflate',distance:'sweep'}),
 M('Scare Soul',['RUN','BL','BL','BL','HK'],'fatality',0,{fatal:'soulScare',distance:'close'})
]),
F('sheeva','SHEEVA','#914f38','#25100a','sheeva',[
 M('Fireball',['D','DF','F','HP'],'projectile',10,{projectile:'fireball'}),
 M('Teleport Stomp',['D','U'],'stompTeleport',13),
 M('Ground Stomp',['B','D','B','HK'],'ground',12)
],[C('Shokan Chain',['HP','HP','LP','F+HP'],25),C('Four Arm Chain',['HP','HP','LP','B+HK','B+HK','B+LK','B+HK'],42)],[
 M('Pound Down',['F','D','D','F','LP'],'fatality',0,{fatal:'stomp',distance:'close'}),
 M('Skin Pull',['HK','F','B','F','F','HK'],'fatality',0,{fatal:'rip',distance:'close'})
],{gender:'female'}),
F('shang-tsung','SHANG TSUNG','#bea03a','#211b08','shang',[
 M('One Skull',['B','B','HP'],'projectile',9,{projectile:'skull'}),
 M('Two Skulls',['B','B','F','HP'],'projectileBurst',13,{projectile:'skull',count:2}),
 M('Three Skulls',['B','B','F','F','HP'],'projectileBurst',17,{projectile:'skull',count:3}),
 M('Hell Fire',['F','B','B','LK'],'groundFire',12),
 M('Morph',['D','D','U'],'morph',0)
],[C('Sorcerer Chain',['LK','HP','HP','LP','B+HK'],27),C('Fire Juggle',['SPECIAL','D+HP'],36)],[
 M('Spike Bed',['LP','D','F','F','D','LP'],'fatality',0,{fatal:'impale',distance:'close'}),
 M('Soul Steal',['LP','RUN','BL','RUN','BL','LP'],'fatality',0,{fatal:'soul',distance:'close'})
]),
F('liu-kang','LIU KANG','#b72d26','#21100c','liukang',[
 M('Bicycle Kick',['B','F','LK'],'multiKick',14),
 M('Flying Kick',['F','F','HK'],'dash',13,{speed:900}),
 M('High Dragon Fire',['F','F','HP'],'projectile',9,{projectile:'fireball',air:true}),
 M('Low Dragon Fire',['F','F','LP'],'projectile',8,{projectile:'fireballLow'})
],[C('Shaolin Chain',['LK','LK','HK','LK'],25),C('Dragon Chain',['HP','HP','BL','LK','LK','HK','LK'],36)],[
 M('Invisible Burn',['F','F','D','D','LK'],'fatality',0,{fatal:'burn',distance:'close'}),
 M('Arcade Drop',['U','D','U','U','BL+RUN'],'fatality',0,{fatal:'arcade',distance:'any'})
]),
F('smoke','SMOKE','#8b7c9c','#19151f','cyborg',[
 M('Trident Spear',['B','B','LP'],'spear',6,{stun:1.2}),
 M('Teleport Punch',['F','F','LK'],'teleport',11,{air:true}),
 M('Air Throw',['BL'],'airThrow',12,{air:true}),
 M('Invisibility',['U','U','RUN'],'invisible',0)
],[C('Smoke Chain',['HP','HP','LK','HK','LP'],26),C('Harpoon Chain',['HP','HP','B+HP'],18)],[
 M('World Bomb',['U','U','F','D'],'fatality',0,{fatal:'worldBomb',distance:'any'}),
 M('Implanted Bomb',['RUN+BL','D','D','F','U'],'fatality',0,{fatal:'bomb',distance:'sweep'})
],{robot:true}),
F('human-smoke','HUMAN SMOKE','#8f969b','#17191b','ninja',[
 M('Spear',['B','B','LP'],'spear',6,{stun:1.2}),
 M('Teleport Punch',['D','B','HP'],'teleport',11,{air:true}),
 M('Air Throw',['BL'],'airThrow',12,{air:true})
],[C('Human Smoke Chain',['HP','HP','HK','B+HK'],24),C('Fast Chain',['HK','D+LP','D+HP'],18)],[
 M('Head Strike',['RUN','BL','RUN','RUN','HK'],'fatality',0,{fatal:'decap',distance:'close'})
],{secret:true}),
F('rain','RAIN','#704cc8','#160a31','ninja',[
 M('Lightning',['D','B','HP'],'lightning',11),
 M('Water Ball',['D','F','HP'],'projectile',9,{projectile:'water'}),
 M('Roundhouse Warp',['B','F','HK'],'dash',13,{speed:880})
],[C('Storm Chain',['HP','HP','HK','B+HK'],24)],[
 M('Storm Execution',['D','D','F','HP'],'fatality',0,{fatal:'lightning',distance:'mid'})
],{secret:true,bonus:true}),
F('noob','NOOB SAIBOT','#24242a','#040405','ninja',[
 M('Shadow Projectile',['D','F','LP'],'projectile',10,{projectile:'shadow'}),
 M('Shadow Teleport',['D','B','HP'],'teleport',11),
 M('Shadow Slide',['B','F','LK'],'slide',12)
],[C('Shadow Chain',['HP','HP','LK','B+HK'],24)],[
 M('Shadow Split',['D','D','B','HP'],'fatality',0,{fatal:'shadow',distance:'mid'})
],{secret:true,bonus:true}),
F('motaro','MOTARO','#7c492e','#1b0e08','motaro',[
 M('Tail Shot',['B','F','HP'],'projectile',12,{projectile:'greenOrb'}),
 M('Centaur Charge',['F','F','HK'],'dash',16,{speed:950}),
 M('Teleport Grab',['D','U'],'teleportGrab',16),
 M('Projectile Reflect',['B','B','BL'],'reflect',0,{duration:3})
],[C('Centaur Crush',['HP','HP','HK'],30)],[],{boss:true,unlockable:false}),
F('shao-kahn','SHAO KAHN','#743533','#190808','shaokahn',[
 M('Shadow Charge',['F','F','HK'],'dash',17,{speed:980}),
 M('Shadow Uppercut',['D','F','HP'],'uppercutSpecial',16),
 M('Helmet Fireball',['B','F','LP'],'projectile',12,{projectile:'greenOrb'}),
 M('Hammer Smash',['B','D','HP'],'hammer',18)
],[C('Emperor Chain',['HP','HP','HK','B+HK'],34)],[],{boss:true,unlockable:false})
];

const stages = [
 {id:'subway',name:'THE SUBWAY',kind:'subway',sky:['#05090c','#101b21'],accent:'#59b2b7',fatal:'train',parallax:1.0},
 {id:'street',name:'THE STREET',kind:'street',sky:['#0a0b12','#251620'],accent:'#d2545e',fatal:null,parallax:.8},
 {id:'rooftop',name:'THE ROOFTOP',kind:'roof',sky:['#090817','#251c3c'],accent:'#d04e70',fatal:null,parallax:.65},
 {id:'bank',name:'THE BANK',kind:'bank',sky:['#111215','#2a2e31'],accent:'#d9bd73',fatal:null,parallax:.7},
 {id:'soul',name:'SOUL CHAMBER',kind:'soul',sky:['#031008','#12351f'],accent:'#55e06d',fatal:null,parallax:.9},
 {id:'bell',name:'BELL TOWER',kind:'tower',sky:['#120d08','#3a2111'],accent:'#d49a42',fatal:'fall',parallax:.75},
 {id:'temple',name:'KOMBAT TEMPLE',kind:'temple',sky:['#100806','#351611'],accent:'#d94731',fatal:null,parallax:.8},
 {id:'graveyard',name:'GRAVEYARD',kind:'grave',sky:['#081014','#25333a'],accent:'#8da5a3',fatal:null,parallax:.65},
 {id:'waterfront',name:'WATERFRONT',kind:'water',sky:['#03131c','#164151'],accent:'#4db4bd',fatal:null,parallax:.7},
 {id:'lost-portal',name:'LOST PORTAL',kind:'portal',sky:['#070921','#172b57'],accent:'#4ca3ff',fatal:null,parallax:.85},
 {id:'jade-desert',name:"JADE'S DESERT",kind:'desert',sky:['#3a1d18','#c66d37'],accent:'#ffd47b',fatal:null,parallax:.6},
 {id:'kahn-kave',name:"KAHN'S KAVE",kind:'cave',sky:['#100606','#3a1011'],accent:'#ff4838',fatal:null,parallax:.9},
 {id:'scorpion-lair',name:"SCORPION'S LAIR",kind:'hell',sky:['#130404','#4a1008'],accent:'#ff6b1a',fatal:'lava',parallax:.85},
 {id:'balcony',name:'THE BALCONY',kind:'balcony',sky:['#0b0b13','#29223d'],accent:'#a98cff',fatal:null,parallax:.7},
 {id:'noob-dorfen',name:"NOOB'S DORFEN",kind:'noob',sky:['#010102','#111116'],accent:'#393944',fatal:null,parallax:.65},
 {id:'pit3',name:'THE PIT III',kind:'pit',sky:['#0d0808','#2e1515'],accent:'#c93a2f',fatal:'blades',parallax:.8}
];

window.UMK3_DATA = {
 version:'0.4.0',
 fighters,
 stages,
 coreRoster:fighters.filter(f=>!f.boss && !f.bonus),
 allPlayable:fighters.filter(f=>!f.boss),
 bosses:fighters.filter(f=>f.boss),
 controls:{
  keyboard:{LP:'J',HP:'U',LK:'K',HK:'I',BL:'L',RUN:'O',PAUSE:'ESC'},
  note:'Directions are relative to the opponent: F=forward, B=back.'
 }
};
})();
