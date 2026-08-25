from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"hotfix pattern not found: {label}")
    return text.replace(old, new, 1)


launcher_path = Path("app/src/main/assets/launcher.js")
launcher = launcher_path.read_text(encoding="utf-8")
launcher = replace_once(
    launcher,
    "function loadControlProfile(core){try{return normalizeControlProfile(core,JSON.parse(localStorage.getItem(`retro-controls-${core}`)||'null'))}catch(_){return normalizeControlProfile(core,null)}}",
    "function loadControlProfile(core){try{const raw=JSON.parse(localStorage.getItem(`retro-controls-${core}`)||'null');return raw&&typeof raw==='object'?normalizeControlProfile(core,raw):null}catch(_){return null}}\nfunction loadMapperProfile(core){return loadControlProfile(core)||normalizeControlProfile(core,null)}",
    "do not inject synthetic control profile during boot",
)
launcher = replace_once(
    launcher,
    "mapperDraft=loadControlProfile(selected.core);renderMapper();",
    "mapperDraft=loadMapperProfile(selected.core);renderMapper();",
    "mapper still gets defaults",
)
launcher_path.write_text(launcher, encoding="utf-8")


game_path = Path("app/src/main/assets/game.html")
game = game_path.read_text(encoding="utf-8")
game = replace_once(
    game,
    "#game,.ejs_parent,.ejs_game,.ejs_canvas_parent{position:fixed!important;inset:0!important;width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;background:#000!important;outline:0!important;overflow:hidden!important}\n.ejs_canvas,#game canvas{position:fixed!important;left:0!important;top:0!important;margin:0!important;max-width:none!important;max-height:none!important;transform:none!important;background:#000!important}\nbody:not(.stretch) .ejs_canvas,body:not(.stretch) #game canvas{width:100%!important;height:100%!important;object-fit:contain!important;object-position:center center!important}\nbody.stretch .ejs_canvas,body.stretch #game canvas{width:100vw!important;height:100vh!important;object-fit:fill!important;object-position:center center!important;aspect-ratio:auto!important}",
    "#game,.ejs_parent,.ejs_game,.ejs_canvas_parent{position:fixed!important;inset:0!important;width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;background:#000!important;outline:0!important;overflow:hidden!important}\n.ejs_canvas,#game canvas{width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;object-fit:contain!important;object-position:center center!important;background:#000!important}\nbody.stretch .ejs_canvas,body.stretch #game canvas{width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;object-fit:fill!important;object-position:center center!important;aspect-ratio:auto!important}",
    "safe preboot canvas CSS",
)
game = replace_once(
    game,
    "currentProfile=config.controlProfile||null;",
    "currentProfile=config.controlProfile&&typeof config.controlProfile==='object'?config.controlProfile:null;",
    "sanitize optional control profile",
)
game = replace_once(
    game,
    "window.EJS_ready=()=>{applySettings(currentSettings);if(currentProfile)applyControlProfile(currentProfile);send('retro-emulator-ready')}",
    "window.EJS_ready=()=>{send('retro-emulator-ready')}",
    "do not mutate emulator during ready",
)
game = replace_once(
    game,
    "new MutationObserver(()=>{if(started){enforceStretch();applySavedLayout()}}).observe(document.querySelector('#game'),{childList:true,subtree:true})",
    "new MutationObserver(()=>{if(startConfirmed){enforceStretch();applySavedLayout()}}).observe(document.querySelector('#game'),{childList:true,subtree:true})",
    "defer canvas mutation until game start",
)
game = replace_once(
    game,
    "window.addEventListener('resize',()=>setTimeout(()=>{enforceStretch();applySavedLayout()},30));",
    "window.addEventListener('resize',()=>{if(startConfirmed)setTimeout(()=>{enforceStretch();applySavedLayout()},30)});",
    "defer resize styling until game start",
)
game_path.write_text(game, encoding="utf-8")


gradle_path = Path("app/build.gradle")
gradle = gradle_path.read_text(encoding="utf-8")
gradle = gradle.replace("versionCode 9", "versionCode 10", 1)
gradle = gradle.replace("versionName '2.6.0'", "versionName '2.6.1'", 1)
gradle_path.write_text(gradle, encoding="utf-8")

print("Retro 3 v2.6.1 startup hotfix applied")
