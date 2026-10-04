window.Assets={ready:false,images:{},load(){
 if(this.ready)return Promise.resolve();
 const names=['hero','soldiers','city','roof','catwalk','crate','shrine','life','energy','claw','armour'];
 return Promise.all(names.map(name=>new Promise((resolve,reject)=>{const img=new Image();img.onload=()=>{if(img.naturalWidth<1)return reject(Error(name));this.images[name]=img;resolve()};img.onerror=()=>reject(Error('Не удалось загрузить '+name));img.src='art/'+name+'.webp'}))).then(()=>{this.ready=true});
}};
