// Random encounters share one persisted budget during fast forward.
export function encounterAllowed(s,key,cooldown=60){
 if(s.death||s.pending||s.recentIncident||s.outcome||s.romance?.conflict||s.jailDays)return false;
 if(s.eventDays?.[key]!==undefined&&s.day-s.eventDays[key]<cooldown)return false;
 if(!s.activeSkip)return true;
 const month=Math.floor((s.day-1)/30),b=s.skipEncounters;
 return !b||b.month!==month||b.count<2&&s.day-b.lastDay>=7;
}
export function recordEncounter(s,key){
 s.eventDays||={};s.eventDays[key]=s.day;
 if(!s.activeSkip)return;
 const month=Math.floor((s.day-1)/30);
 if(!s.skipEncounters||s.skipEncounters.month!==month)s.skipEncounters={month,count:0,lastDay:0};
 s.skipEncounters.count++;s.skipEncounters.lastDay=s.day;
}
