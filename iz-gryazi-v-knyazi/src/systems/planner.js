import {careers,diets} from '../data/lifestyle.js';import {homes,vehicles,businesses} from '../data/world.js';import {careerShift,businessDuty,salaryFor} from './routine.js';import {homeTerms,housingBill} from './housing.js';import {businessForecast} from './economy.js';
export function dayPlan(s){
 const next=s.day+1,e=s.employment,c=e&&careers.find(c=>c.id===e.id),shift=c&&careerShift(c.id),weekday=e?(next-e.since)%7:0;
 const leave=c&&Math.max(e.leaveUntil||0,e.medicalUntil||0)>=next;
 const working=!!(c&&!leave&&weekday<shift.days),duties=Object.entries(s.businesses).filter(([,f])=>!f.paused).map(([id,f])=>({name:businesses.find(b=>b.id===id)?.name,...businessDuty(businesses.find(b=>b.id===id),f)}));
 const end=Math.min(23,(working?shift.end:7)+duties.reduce((n,d)=>n+d.hours,0));
 return {working,leave,career:c?.name,end,energy:(working?shift.energy:0)+duties.reduce((n,d)=>n+d.energy,0),duties,free:Math.max(0,23-end)};
}
export function budgetPlan(s){
 const h=homeTerms(s.home),housing=s.housing,nextDue=h.amount?(housing?.nextDue||s.day+h.period):null;
 const diet=diets.find(d=>d.id===(s.skipDiet||'basic')),transport=vehicles.find(v=>v.id===s.vehicle)?.upkeep||0;
 const recurring=transport+Math.floor(s.debt*.018)+(h.kind==='daily'?housingBill(s):h.amount/Math.max(1,h.period));
 const c=s.employment&&careers.find(c=>c.id===s.employment.id),forecast=Object.entries(s.businesses).reduce((n,[id,f])=>{const b=businesses.find(b=>b.id===id);if(!b)return n;const result=businessForecast(s,b,f);return n+(result.netLow+result.netHigh)/2},0);
 return {nextDue,rent:h.amount?housingBill(s):0,food: diet.daily,recurring:Math.round(recurring),daily:Math.round(recurring+diet.daily),nextPay:s.employment?.nextPay||null,accrued:Math.round(s.employment?.accrued||0),salary:c?salaryFor(s,c):0,business:Math.round(forecast)};
}
