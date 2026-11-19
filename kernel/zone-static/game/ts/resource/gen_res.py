import os
import json
cur_dir = os.path.abspath(os.path.dirname(__file__))

result = {
    "groups": [],
    "resources": []
}
ext_map = {
    "png": "image",
    "jpg": "image",
    "mp3": "sound",
    "json": "json",
    "fnt": "font",
    "bytes": "bin",
    "fui": "bin"
}
preload_keys = ""
start_keys = ""
prefix = ""
ignore_post = ["ui"]


def is_ignore(path):
    global prefix
    for p in ignore_post:
        if path.startswith(os.path.join(prefix, p)):
            return True
    return False


def go_path(path):
    assert os.path.exists(path)
    global preload_keys
    global start_keys
    global prefix
    if path.startswith("."):
        return
    for sub_path in os.listdir(path):
        full_path = os.path.join(path, sub_path)
        abs_path = os.path.join(cur_dir, full_path)
        if os.path.isdir(abs_path):
            go_path(full_path)
        else:
            if sub_path.endswith(".meta") or sub_path.startswith("."):
                os.remove(abs_path)
            else:
                url = full_path.replace(os.path.sep, "/")
                print(url)
                name = full_path.replace(
                    os.path.sep, "_").replace(prefix+"_", "")
                if is_ignore(full_path):
                    name = name.split(".")[0]
                else:
                    name = name.replace(".", "_")
                names = sub_path.split(".")
                type = "text"
                if len(names) >= 2:
                    ext = names[len(names)-1]
                    if ext in ext_map:
                        type = ext_map[ext]
                result["resources"].append({
                    "name": name,
                    "type": type,
                    "url": url
                })
                if "assets_ts/icon" in url or "assets_ts/heros" in url or "assets_ts/hang_scene_bg" in url or "assets_ts/skill_effect" in url:
                    start_keys += name + ","
                else:
                    preload_keys += name + ","


for path in os.listdir(cur_dir):
    if os.path.isdir(path):
        prefix = path
        go_path(path)
result["groups"].append({
    "keys": preload_keys,
    "name": "preload"
})
result["groups"].append({
    "keys": start_keys,
    "name": "start"
})
filename = os.path.join(cur_dir, "default_ts.res.json")
with open(filename, "w", encoding='utf-8') as f:
    json.dump(result, f)
