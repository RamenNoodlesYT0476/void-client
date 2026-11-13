"""Private stdio protocol between Void's desktop UI and the launch worker."""
import base64
import configparser
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import sys
import time
import tempfile
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
import minecraft_launcher_lib as mc

VERSION = "fabric-loader-0.20.1-1.21.11"

def emit(kind, value):
    print(json.dumps({"kind": kind, "value": value}), flush=True)

def interactive_login(client_id):
    result = {}

    class Redirect(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            query = parse_qs(urlsplit(self.path).query)
            valid = (urlsplit(self.path).path == "/" and
                     secrets.compare_digest(query.get("state", [""])[0], state))
            if valid:
                result.update(query)
            self.send_response(200 if valid else 400)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(b"Return to Void Client." if valid else b"Unexpected sign-in response.")

    with HTTPServer(("127.0.0.1", 0), Redirect) as server:
        redirect = f"http://localhost:{server.server_port}/"
        url, state, verifier = mc.microsoft_account.get_secure_login_data(client_id, redirect)
        if not webbrowser.open(url):
            raise RuntimeError("browser")
        server.timeout = 1
        deadline = time.monotonic() + 180
        while not result and time.monotonic() < deadline:
            server.handle_request()
    if not result.get("code"):
        raise RuntimeError("cancelled")
    return mc.microsoft_account.complete_login(client_id, None, redirect, result["code"][0], verifier)

def apply_pending_update(instance):
    import hashlib
    mods = Path(instance) / "mods"
    pending = mods / "void-client-1.1.0+26.3.installing"
    if pending.is_file() and hashlib.sha256(pending.read_bytes()).hexdigest() in {"7013070b85a22847749e9cb714d17bcc9acc5a8fa3ac3c6d587ed41b91ccadda", "cc8ccf6582b80583d07c8c3c3cb165a5f2392b96be53734af8c8f8e2c9cc2bb6", "4568bbb118f8fb1943234ecee23427303ca7486c563dca041b58fac9451b8d2d", "e499c2574544e27f3e24a97be2f69e2d62c0fe7d05c65feac60bdf840ae3dc6c"}:
        try:
            pending.replace(mods / "void-client-1.1.0+26.3.jar")
        except PermissionError:
            raise RuntimeError("Close the running 26.3 game to finish its Void Utilities update.")


def launch(data, account):
    instance = Path(data["instance"])
    apply_pending_update(instance)
    cache = Path(data["cache"])
    manifest = cache / "versions" / VERSION / (VERSION + ".json")
    manifest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(data["manifest"], manifest)
    emit("status", "Checking game files and Java. The first launch downloads Minecraft.")
    def progress(message):
        if not message.startswith("Download " ) or message in ("Download Libraries", "Download Assets"):
            emit("status", message)
    mc.install.install_minecraft_version(VERSION, cache, callback={"setStatus": progress})
    options = {"username": account["name"], "uuid": account["id"], "token": account["access_token"],
               "gameDirectory": str(instance), "launcherName": "Void Client", "launcherVersion": "1.2",
               "jvmArguments": [f"-Xmx{max(1024, min(32768, int(data.get('memoryMiB', 3072))))}M"]}
    cmd = mc.command.get_minecraft_command(VERSION, str(cache), options)
    token_parts = account["access_token"].split(".")
    xuid = ""
    if len(token_parts) == 3:
        try:
            claims = json.loads(base64.urlsafe_b64decode(token_parts[1] + "==="))
            xuid = str(claims.get("xuid", ""))
        except (ValueError, TypeError):
            pass
    cmd = [s.replace("\u0024{clientid}", data["client"]).replace("\u0024{auth_xuid}", xuid) for s in cmd]
    # Windows limits process command lines to 32K. Keep only the classpath in an argfile.
    cp = cmd.index("-cp") if "-cp" in cmd else cmd.index("-classpath")
    argfile = instance / ".void-classpath.args"
    argfile.write_text('"-cp"\n"' + cmd[cp+1].replace("\\", "\\\\").replace('"','\\"') + '"\n', encoding="utf-8")
    cmd[cp:cp+2] = ["@" + str(argfile)]
    process = subprocess.Popen(cmd, cwd=instance, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                               stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        code = process.wait(timeout=2)
        raise RuntimeError("game_exit")
    except subprocess.TimeoutExpired:
        emit("status", "Minecraft started.")

def prism_instance(source, folder, name):
    """Publish a Prism instance atomically, sharing Void's game directory."""
    import _winapi
    source = source.resolve(strict=True)
    folder.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(os.path.normcase(str(source)).encode()).hexdigest()[:24]
    target = folder / ("Void-" + key)
    if not target.exists():
        stage = Path(tempfile.mkdtemp(prefix=".void-", dir=folder))
        try:
            # Prism reads these components and resolves their libraries itself.
            components = [{"uid": "net.minecraft", "version": "1.21.11", "important": True},
                          {"uid": "net.fabricmc.fabric-loader", "version": "0.20.1"}]
            profile = source / "void-prism-components.json"
            if profile.is_file():
                components = json.loads(profile.read_text(encoding="utf-8-sig"))["components"]
            (stage / "mmc-pack.json").write_text(json.dumps({"formatVersion": 1, "components": components}), encoding="utf-8")
            label = " ".join(name.splitlines()).replace("\\", "\\\\")
            (stage / "instance.cfg").write_text("[General]\nInstanceType=OneSix\nname=Void - " + label + "\n", encoding="utf-8")
            _winapi.CreateJunction(str(source), str(stage / "minecraft"))
            try:
                stage.rename(target)
            except FileExistsError:
                pass  # Another launcher created the same wrapper first.
        finally:
            if stage.exists():
                shutil.rmtree(stage)  # Python 3.13 does not follow Windows junctions here.
    game = target / "minecraft"
    if not game.is_junction() or not game.samefile(source):
        raise RuntimeError("Prism instance folder conflicts with the selected Void instance.")
    return target.name

def launch_with_prism(data):
    apply_pending_update(data["instance"])
    executable = Path(data["prismExe"]).resolve(strict=True)
    root = Path(data["prismRoot"]).resolve(strict=True)
    settings = configparser.ConfigParser(interpolation=None, strict=False)
    with (root / "prismlauncher.cfg").open(encoding="utf-8-sig") as handle:
        settings.read_file(handle)
    folder = root / settings.get("General", "InstanceDir", fallback="instances")
    instance_id = prism_instance(Path(data["instance"]), folder, data["name"])
    subprocess.Popen([str(executable), "--dir", str(root), "--launch", instance_id],
                     stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    emit("status", "Launch requested in Prism. Choose your Minecraft account there if prompted.")

def main():
    data = json.load(sys.stdin)
    if data["mode"] == "prism":
        launch_with_prism(data)
        return
    if data["mode"] in ("signin", "signin-play"):
        account = interactive_login(data["client"])
    else:
        account = mc.microsoft_account.complete_refresh(data["client"], None, None, data["refresh"])
    if not all(account.get(k) for k in ("name","id","access_token","refresh_token")):
        raise RuntimeError("profile")
    emit("account", {"name":account["name"], "refresh":account["refresh_token"]})
    if data["mode"] in ("play", "signin-play"):
        launch(data, account)
    else:
        emit("status", "Signed in as " + account["name"] + ".")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        if isinstance(exc, (OSError, RuntimeError, configparser.Error)) and sys.argv[1:] == ["prism"]:
            emit("error", "Prism handoff failed: " + str(exc))
            sys.exit(1)
        messages = {"AzureAppNotPermitted":"Minecraft Services denied this application. Check Void's approved application ID.",
                    "InvalidRefreshToken":"Your Microsoft session expired. Sign out and sign in again.",
                    "AccountNotOwnMinecraft":"This account has no Minecraft Java profile.",
                    "cancelled":"Sign-in was cancelled or timed out.",
                    "browser":"Could not open your default web browser.",
                    "game_exit":"Minecraft closed during startup. Open the instance's logs/latest.log."}
        emit("error", messages.get(type(exc).__name__) or messages.get(str(exc)) or
             ("Could not finish the operation (" + type(exc).__name__ + "). Check your connection, account and launcher settings."))
        sys.exit(1)

