from pathlib import Path

root=Path('InkFlowReader')
target=root/'app/src/main/java/app/inkflow/reader'

# Fix double navigation-bar inset: content wrapped by BannerAdSlot must not
# reserve the Android navigation area itself; the ad slot owns that inset.
p=target/'Ui.java'
s=p.read_text()
old='''                left=safe.left;top=safe.top;right=safe.right;bottom=safe.bottom;'''
new='''                left=safe.left;top=safe.top;right=safe.right;bottom="ad_content".equals(view.getTag())?0:safe.bottom;'''
if old not in s:
    raise SystemExit('Ui API30 inset marker missing')
s=s.replace(old,new,1)
old2='''                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();'''
new2='''                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom="ad_content".equals(view.getTag())?0:insets.getSystemWindowInsetBottom();'''
if old2 not in s:
    raise SystemExit('Ui legacy inset marker missing')
s=s.replace(old2,new2,1)
p.write_text(s)

# Mark the wrapped content before insets are dispatched.
p=target/'BannerAdSlot.java'
s=p.read_text()
old='''        if(content instanceof LinearLayout&&"ad_wrapped".equals(content.getTag()))return content;\n        ViewGroup parent=content.getParent() instanceof ViewGroup?(ViewGroup)content.getParent():null;'''
new='''        if(content instanceof LinearLayout&&"ad_wrapped".equals(content.getTag()))return content;\n        content.setTag("ad_content");\n        ViewGroup parent=content.getParent() instanceof ViewGroup?(ViewGroup)content.getParent():null;'''
if old not in s:
    raise SystemExit('Banner wrap marker missing')
s=s.replace(old,new,1)
p.write_text(s)

# Bump release.
build=root/'app/build.gradle'
b=build.read_text().replace('versionCode 17','versionCode 18').replace("versionName '1.6.2'","versionName '1.6.3'")
build.write_text(b)
