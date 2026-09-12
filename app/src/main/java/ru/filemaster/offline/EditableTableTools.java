package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class EditableTableTools {
    private EditableTableTools() {}

    static String readAsTsv(Context ctx, Uri uri) throws Exception {
        String name=FileStore.displayName(ctx,uri).toLowerCase(Locale.ROOT);
        if(name.endsWith(".xlsx"))return xlsxFirstSheet(ctx,uri);
        String raw=readUtf8(ctx,uri).replace("\uFEFF","");
        char sep=detectSeparator(raw);return parseDelimitedToTsv(raw,sep);
    }

    static Uri saveTsv(Context ctx,String text)throws Exception{return FileStore.publishBytes(ctx,normalize(text).getBytes(StandardCharsets.UTF_8),"Таблица_"+System.currentTimeMillis()+".tsv","text/tab-separated-values",null);}
    static Uri saveCsv(Context ctx,String text)throws Exception{StringBuilder out=new StringBuilder();for(String line:normalize(text).split("\n",-1)){String[] cells=line.split("\t",-1);for(int i=0;i<cells.length;i++){if(i>0)out.append(';');out.append(csvQuote(cells[i]));}out.append('\n');}return FileStore.publishBytes(ctx,("\uFEFF"+out).getBytes(StandardCharsets.UTF_8),"Таблица_"+System.currentTimeMillis()+".csv","text/csv",null);}

    static Uri saveXlsx(Context ctx,String text)throws Exception{
        List<List<String>> rows=new ArrayList<>();for(String line:normalize(text).split("\n",-1)){List<String> r=new ArrayList<>();for(String c:line.split("\t",-1))r.add(c);rows.add(r);}ByteArrayOutputStream bos=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bos)){
            put(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Лист1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            for(int r=0;r<rows.size();r++){sheet.append("<row r=\"").append(r+1).append("\">");List<String> row=rows.get(r);for(int c=0;c<row.size();c++){String ref=columnName(c+1)+(r+1),value=row.get(c);if(value.startsWith("=")&&value.length()>1)sheet.append("<c r=\"").append(ref).append("\"><f>").append(xml(value.substring(1))).append("</f></c>");else sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value)).append("</t></is></c>");}sheet.append("</row>");}sheet.append("</sheetData></worksheet>");put(zip,"xl/worksheets/sheet1.xml",sheet.toString());
        }return FileStore.publishBytes(ctx,bos.toByteArray(),"Таблица_редактор_"+System.currentTimeMillis()+".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",null);
    }

    private static String xlsxFirstSheet(Context ctx,Uri uri)throws Exception{File file=FileStore.copyUriToTemp(ctx,uri,".xlsx");try(ZipFile zip=new ZipFile(file)){List<String> shared=readSharedStrings(zip);ZipEntry e=zip.getEntry("xl/worksheets/sheet1.xml");if(e==null)throw new IllegalArgumentException("В XLSX нет первого листа");XmlPullParserFactory f=XmlPullParserFactory.newInstance();f.setNamespaceAware(true);XmlPullParser p=f.newPullParser();try(InputStream in=zip.getInputStream(e)){p.setInput(in,"UTF-8");StringBuilder out=new StringBuilder();List<String> row=null;String type=null,ref=null,value="",formula="";int ev=p.getEventType();while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG){String n=p.getName();if("row".equals(n))row=new ArrayList<>();else if("c".equals(n)){type=p.getAttributeValue(null,"t");ref=p.getAttributeValue(null,"r");value="";formula="";}else if("f".equals(n)&&row!=null)formula=p.nextText();else if(("v".equals(n)||"t".equals(n))&&row!=null)value=p.nextText();}else if(ev==XmlPullParser.END_TAG){String n=p.getName();if("c".equals(n)&&row!=null){int col=refColumn(ref);while(row.size()<col-1)row.add("");String cell=value;if("s".equals(type)){try{int idx=Integer.parseInt(value);cell=idx>=0&&idx<shared.size()?shared.get(idx):value;}catch(Exception ignored){}}if(formula!=null&&!formula.isBlank())cell="="+formula;row.add(cell==null?"":cell);}else if("row".equals(n)&&row!=null){for(int i=0;i<row.size();i++){if(i>0)out.append('\t');out.append(row.get(i));}out.append('\n');row=null;}}ev=p.next();}return out.toString();}}finally{file.delete();}}
    private static List<String> readSharedStrings(ZipFile zip)throws Exception{List<String> list=new ArrayList<>();ZipEntry e=zip.getEntry("xl/sharedStrings.xml");if(e==null)return list;XmlPullParserFactory f=XmlPullParserFactory.newInstance();f.setNamespaceAware(true);XmlPullParser p=f.newPullParser();try(InputStream in=zip.getInputStream(e)){p.setInput(in,"UTF-8");StringBuilder cur=null;int ev=p.getEventType();while(ev!=XmlPullParser.END_DOCUMENT){if(ev==XmlPullParser.START_TAG&&"si".equals(p.getName()))cur=new StringBuilder();else if(ev==XmlPullParser.START_TAG&&"t".equals(p.getName())&&cur!=null)cur.append(p.nextText());else if(ev==XmlPullParser.END_TAG&&"si".equals(p.getName())&&cur!=null){list.add(cur.toString());cur=null;}ev=p.next();}}return list;}
    private static String readUtf8(Context ctx,Uri uri)throws Exception{try(InputStream in=ctx.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IllegalArgumentException("Не удалось открыть таблицу");FileStore.copy(in,out);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static char detectSeparator(String raw){String first=raw.split("\\r?\\n",2)[0];int comma=count(first,','),semi=count(first,';'),tab=count(first,'\t');if(tab>=comma&&tab>=semi&&tab>0)return'\t';if(semi>=comma&&semi>0)return';';return',';}private static int count(String s,char c){int n=0;for(int i=0;i<s.length();i++)if(s.charAt(i)==c)n++;return n;}
    private static String parseDelimitedToTsv(String raw,char sep){StringBuilder out=new StringBuilder(),cell=new StringBuilder();boolean q=false;for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if(c=='"'){if(q&&i+1<raw.length()&&raw.charAt(i+1)=='"'){cell.append('"');i++;}else q=!q;}else if(c==sep&&!q){out.append(cell).append('\t');cell.setLength(0);}else if((c=='\n'||c=='\r')&&!q){if(c=='\r'&&i+1<raw.length()&&raw.charAt(i+1)=='\n')i++;out.append(cell).append('\n');cell.setLength(0);}else cell.append(c);}if(cell.length()>0)out.append(cell);return out.toString();}
    private static String csvQuote(String s){if(s.indexOf(';')>=0||s.indexOf('"')>=0||s.indexOf('\n')>=0||s.indexOf('\r')>=0)return"\""+s.replace("\"","\"\"")+"\"";return s;}private static String normalize(String s){return(s==null?"":s).replace("\r\n","\n").replace('\r','\n');}private static void put(ZipOutputStream z,String n,String s)throws Exception{z.putNextEntry(new ZipEntry(n));z.write(s.getBytes(StandardCharsets.UTF_8));z.closeEntry();}private static String xml(String s){return(s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}private static String columnName(int n){StringBuilder b=new StringBuilder();while(n>0){n--;b.insert(0,(char)('A'+n%26));n/=26;}return b.toString();}private static int refColumn(String r){if(r==null)return 1;int n=0;for(int i=0;i<r.length();i++){char c=r.charAt(i);if(c>='A'&&c<='Z')n=n*26+c-'A'+1;else if(c>='a'&&c<='z')n=n*26+c-'a'+1;else break;}return Math.max(1,n);}
}
