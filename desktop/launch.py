"""Private stdio protocol between Void's desktop UI and the launch worker."""
import base64
import json
import os
import secrets
import shutil
import subprocess
import sys
import time
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

def launch(data, account):
    instance = Path(data["instance"])
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

def main():
    data = json.load(sys.stdin)
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
        messages = {"AzureAppNotPermitted":"Minecraft Services denied this application. Check Void's approved application ID.",
                    "InvalidRefreshToken":"Your Microsoft session expired. Sign out and sign in again.",
                    "AccountNotOwnMinecraft":"This account has no Minecraft Java profile.",
                    "cancelled":"Sign-in was cancelled or timed out.",
                    "browser":"Could not open your default web browser.",
                    "game_exit":"Minecraft closed during startup. Open the instance's logs/latest.log."}
        emit("error", messages.get(type(exc).__name__) or messages.get(str(exc)) or
             ("Could not finish the operation (" + type(exc).__name__ + "). Check your connection, account and launcher settings."))
        sys.exit(1)

