"""Provider search, version selection and verified per-instance installation."""
import hashlib
import json
import os
import re
import sys
import tempfile
from pathlib import Path
from urllib.parse import urlsplit
from zipfile import ZipFile
import requests

MC = "1.21.11"
MR = "https://api.modrinth.com/v2/"
CF = "https://api.curseforge.com/v1/"

def api(cf, path, **params):
    headers = {"User-Agent": "VoidClient/1.2 (Fabric launcher)"}
    if cf:
        key = os.environ.get("VOID_CURSEFORGE_KEY")
        if not key:
            raise ValueError("CurseForge needs VOID_CURSEFORGE_KEY. Use Website to download manually.")
        headers["x-api-key"] = key
    response = requests.get((CF if cf else MR) + path, params=params, headers=headers, timeout=20, allow_redirects=False)
    response.raise_for_status()
    return response.json()

def search(cf, query, offset):
    if cf:
        hits = api(True, "mods/search", gameId=432, classId=6, gameVersion=MC, modLoaderType=4,
                   pageSize=20, index=offset, searchFilter=query)["data"]
        return [{"id":str(h["id"]), "name":h["name"]} for h in hits]
    facets = [["project_type:mod"],["categories:fabric"],["versions:"+MC],["client_side:optional","client_side:required"]]
    hits = api(False, "search", query=query, facets=json.dumps(facets), limit=20, offset=offset)["hits"]
    return [{"id":h["project_id"], "name":h["title"]} for h in hits]

def release(cf, project):
    if not re.fullmatch(r"[A-Za-z0-9]+", project):
        raise ValueError("Invalid project.")
    if cf:
        for f in api(True, "mods/"+project+"/files",gameVersion=MC,modLoaderType=4,pageSize=50)["data"]:
            if f["releaseType"] != 1:
                continue
            for digest in f["hashes"]:
                if digest["algo"] == 1:
                    if not f.get("downloadUrl"):
                        raise ValueError("The author requires a website download.")
                    return f["fileName"], f["downloadUrl"], "sha1", digest["value"], sum(d["relationType"]==3 for d in f["dependencies"])
    else:
        for v in api(False, "project/"+project+"/version", loaders='["fabric"]',game_versions=json.dumps([MC])):
            if v["version_type"] == "release":
                for f in v["files"]:
                    if f["primary"] and f["filename"].endswith(".jar"):
                        return f["filename"], f["url"], "sha512", f["hashes"]["sha512"], sum(d["dependency_type"]=="required" for d in v["dependencies"])
    raise ValueError("No compatible stable file found.")

def mod_id(path):
    with ZipFile(path) as jar:
        return json.loads(jar.read("fabric.mod.json"))["id"]

def install(folder, file):
    name,url,algo,expected,_ = file
    if not re.fullmatch(r"[\w+.()\[\] -]+\.jar",name) or Path(name).is_reserved():
        raise ValueError("Unsupported filename.")
    target=folder/name
    if target.exists():
        raise ValueError("This file is already installed.")
    fd,tmp=tempfile.mkstemp(dir=folder,suffix=".part")
    try:
        with os.fdopen(fd,"wb") as stream:
            for _ in range(4):
                parsed=urlsplit(url)
                if parsed.scheme!="https" or not (parsed.hostname=="cdn.modrinth.com" or (parsed.hostname or "").endswith(".forgecdn.net")):
                    raise ValueError("Unexpected download host.")
                with requests.get(url,stream=True,allow_redirects=False,timeout=30) as response:
                    if response.is_redirect:
                        url=response.headers["Location"]
                        continue
                    response.raise_for_status()
                    digest=hashlib.new(algo)
                    for chunk in response.iter_content(65536):
                        stream.write(chunk)
                        if stream.tell()>256*1024*1024:
                            raise ValueError("Download exceeds 256 MiB.")
                        digest.update(chunk)
                    if digest.hexdigest()!=expected.lower():
                        raise ValueError("Checksum mismatch.")
                    break
            else:
                raise ValueError("Too many redirects.")
        new_id=mod_id(tmp)
        for other in [*folder.glob("*.jar"),*folder.glob("*.jar.disabled")]:
            try:
                current=mod_id(other)
            except (ValueError,KeyError):
                continue
            if current==new_id:
                raise ValueError("This instance already contains "+new_id+".")
        os.rename(tmp,target)
    finally:
        Path(tmp).unlink(missing_ok=True)

def main():
    req=json.load(sys.stdin)
    cf=req["provider"]=="CurseForge"
    if req["action"]=="search":
        return search(cf,req["query"],req["offset"])
    file=release(cf,req["id"])
    if req["action"]=="install":
        if req["filename"]!=file[0] or req["digest"]!=file[3]:
            raise ValueError("Release changed. Review the file again.")
        install(Path(req["instance"])/"mods",file)
    return {"filename":file[0],"digest":file[3],"dependencies":file[4]}
if __name__=="__main__":
    try:
        print(json.dumps({"result":main()}))
    except Exception as exc:
        print(json.dumps({"error":str(exc)}))
