from pathlib import Path

p=Path('InkFlowReader/app/src/main/java/app/inkflow/reader/BookReaderActivity.java')
s=p.read_text()

repls=[
    (
        'word-wrap:break-word;text-align:',
        'overflow-wrap:break-word;word-break:normal;-webkit-hyphens:auto;hyphens:auto;text-align:'
    ),
    (
        'p{margin:.68em 0;text-indent:"+indent+"px;orphans:2;widows:2;}',
        'p{margin:.68em 0;text-indent:"+indent+"px;orphans:2;widows:2;text-align-last:left;}ul,ol{margin:.72em 0;padding-left:1.45em;}li{margin:.24em 0;break-inside:avoid;overflow-wrap:break-word;}pre,code{white-space:pre-wrap;overflow-wrap:anywhere;}'
    ),
    (
        "#reader{box-sizing:border-box;height:100vh;column-fill:auto;padding-top:14px;padding-bottom:18px;will-change:transform;}",
        "#reader{box-sizing:border-box;height:100vh;column-fill:auto;padding-top:14px;padding-bottom:18px;will-change:transform;overflow:visible;max-width:none;}"
    ),
    (
        "var m=\"+margins+\";r.style.columnWidth=Math.max(120,this.cell-2*m)+'px';r.style.columnGap=(2*m)+'px';r.style.marginLeft=m+'px';r.style.transform='translate3d(0,0,0)';",
        "var m=\"+margins+\";var content=Math.max(120,this.cell-2*m);r.style.width=content+'px';r.style.minWidth=content+'px';r.style.maxWidth=content+'px';r.style.columnWidth=content+'px';r.style.columnGap=(2*m)+'px';r.style.marginLeft=m+'px';r.style.marginRight=m+'px';r.style.transform='translate3d(0,0,0)';"
    ),
    (
        "self.pages=Math.max(1,Math.ceil((r.scrollWidth+m)/self.cell));",
        "self.pages=Math.max(1,Math.round((r.scrollWidth+2*m)/self.cell));"
    ),
    (
        "document.getElementById('reader').style.transform='translate3d('+(-p*this.cell)+'px,0,0)';",
        "document.getElementById('reader').style.transform='translate3d('+Math.round(-p*this.cell)+'px,0,0)';"
    ),
    (
        "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>",
        "<html lang='ru'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
    )
]

for old,new in repls:
    if old not in s:
        raise SystemExit('pagination patch pattern missing: '+old[:100])
    s=s.replace(old,new,1)

# In a two-page spread always land on the beginning of the spread.
old='currentPage=pageCount<=1?0:(int)Math.round((ratio/1000000.0)*(pageCount-1)); setPageInternal(currentPage,false);'
new='currentPage=pageCount<=1?0:(int)Math.round((ratio/1000000.0)*(pageCount-1)); if(pageStep>1) currentPage=(currentPage/pageStep)*pageStep; setPageInternal(currentPage,false);'
if old not in s:
    raise SystemExit('spread alignment pattern missing')
s=s.replace(old,new,1)

p.write_text(s)
