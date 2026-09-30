const data={"artur":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"azamat":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"irina":{"minAge":20,"columns":8,"rows":4,"cellHeight":1},"lida":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"marina":{"minAge":20,"columns":8,"rows":4,"cellHeight":1},"minister":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"nina":{"minAge":20,"columns":8,"rows":4,"cellHeight":0.7997982854261221},"pasha":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"rosa":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"tamara":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"valera":{"minAge":30,"columns":7,"rows":4,"cellHeight":1.8652263374485596},"vera":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"viktoria":{"minAge":20,"columns":8,"rows":4,"cellHeight":1.3333333333333333},"zoya":{"minAge":30,"columns":7,"rows":4,"cellHeight":1},"alisa":{"minAge":20,"columns":8,"rows":4,"cellHeight":1}};
// Actual portrait boundaries in each illustration. Generated contact sheets
// have unequal columns and rows; a mathematical grid can include a neighbour.
const bounds={
 artur:[[0,240,477,711,948,1185,1421,1659],[0,243,474,700,948]],
 azamat:[[0,240,477,710,947,1186,1421,1659],[0,239,474,708,948]],
 irina:[[0,227,453,676,897,1116,1336,1556,1774],[0,252,500,707,887]],
 lida:[[0,248,496,738,955,1190,1432,1659],[0,243,485,714,948]],
 marina:[[0,226,447,667,890,1112,1333,1555,1774],[0,223,444,666,887]],
 minister:[[0,257,504,739,961,1197,1433,1659],[0,238,474,702,948]],
 nina:[[0,268,533,791,1031,1288,1534,1766,1983],[0,203,412,605,793]],
 pasha:[[0,253,499,733,953,1179,1416,1659],[0,267,480,693,948]],
 rosa:[[0,245,480,714,948,1183,1418,1659],[0,236,475,703,948]],
 tamara:[[0,260,512,752,990,1214,1440,1659],[0,238,474,707,948]],
 valera:[[0,172,344,518,690,864,1038,1215],[0,322,638,946,1295]],
 vera:[[0,260,510,767,1006,1237,1461,1659],[0,236,474,709,948]],
 viktoria:[[0,192,384,576,768,960,1152,1344,1536],[0,256,512,759,1024]],
 zoya:[[0,242,480,716,950,1188,1422,1659],[0,238,474,710,948]],
 alisa:[[0,222,444,666,888,1110,1332,1554,1774],[0,222,444,664,887]]
};
export const citizenAtlases=Object.fromEntries(Object.entries(data).map(([id,atlas])=>[id,{...atlas,bounds:bounds[id],image:new URL(`./people/${id}-life.png`,import.meta.url).href}]));
