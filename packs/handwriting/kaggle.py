#!/usr/bin/env python3
"""Trains the handwriting recogniser on a free Kaggle GPU instead of GitHub Actions (which
would use the account's Actions minutes), carrying on from the checkpoint kept on this
repository's private "handwriting-training" pre-release and sending each new checkpoint back
there (every --eval-every minutes, so a session that ends early loses little). packs.yml then
exports the pack from it (run it with handwriting_minutes 0: a few minutes).

The Kaggle notebook (GPU on, internet on, a secret GITHUB_TOKEN: a fine-grained token for this
repository with Contents read and write) runs, in one cell:

    from kaggle_secrets import UserSecretsClient
    import os, subprocess
    os.environ["GITHUB_TOKEN"] = UserSecretsClient().get_secret("GITHUB_TOKEN")
    repo = f"https://x-access-token:{os.environ['GITHUB_TOKEN']}@github.com/thebingchilling/bumboard.git"
    subprocess.run(["git", "clone", "--depth", "1", "--filter=blob:none", "--sparse", repo, "bumboard"], check=True)
    subprocess.run(["git", "-C", "bumboard", "sparse-checkout", "set", "packs"], check=True)
    subprocess.run(["pip", "install", "-q", "pyarrow"], check=True)
    subprocess.run(["python", "bumboard/packs/handwriting/kaggle.py", "--minutes", "690"], check=True)

  kaggle.py [--minutes 690] [train.py's other options]
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

REPO = os.environ.get("LYCHEE_REPO", "thebingchilling/bumboard")
TAG = "handwriting-training"
API = f"https://api.github.com/repos/{REPO}"
NAME = "checkpoint.pt"


def _request(method: str, url: str, data: bytes | None = None, kind: str = "application/json",
             accept: str = "application/vnd.github+json"):
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": accept,
        "Content-Type": kind,
        "X-GitHub-Api-Version": "2022-11-28",
    })
    with urllib.request.urlopen(req, timeout=600) as r:
        body = r.read()
        return json.loads(body) if body and accept.endswith("json") else body


def release() -> dict:
    """The training pre-release, made if it isn't there yet (never "latest", never public)."""
    try:
        return _request("GET", f"{API}/releases/tags/{TAG}")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
    return _request("POST", f"{API}/releases", json.dumps({
        "tag_name": TAG, "target_commitish": "main", "name": "Handwriting training",
        "body": "The handwriting recogniser's training checkpoint (packs/handwriting/kaggle.py and packs.yml carry on from it).",
        "prerelease": True, "make_latest": "false",
    }).encode())


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def download(dest: Path) -> bool:
    """The last checkpoint, if there is one."""
    asset = next((a for a in release().get("assets", []) if a["name"] == NAME), None)
    if not asset:
        print("no checkpoint yet: training starts afresh", flush=True)
        return False
    # the asset URL redirects to storage that must not be sent the token
    req = urllib.request.Request(asset["url"], headers={
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}", "Accept": "application/octet-stream"})
    try:
        with urllib.request.build_opener(_NoRedirect).open(req, timeout=600) as r:
            data = r.read()
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 307, 308):
            raise
        with urllib.request.urlopen(e.headers["Location"], timeout=600) as r:
            data = r.read()
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    print(f"checkpoint: {len(data) >> 20} MB from the {TAG} release", flush=True)
    return True


def upload(path: Path) -> None:
    """Replaces the release's checkpoint: the new one goes up first, then takes the old one's name."""
    rel = release()
    assets = {a["name"]: a for a in rel.get("assets", [])}
    if "checkpoint-new.pt" in assets:
        _request("DELETE", f"{API}/releases/assets/{assets['checkpoint-new.pt']['id']}")
    up = rel["upload_url"].split("{")[0]
    new = _request("POST", f"{up}?name=checkpoint-new.pt", path.read_bytes(), kind="application/octet-stream")
    if NAME in assets:
        _request("DELETE", f"{API}/releases/assets/{assets[NAME]['id']}")
    _request("PATCH", f"{API}/releases/assets/{new['id']}", json.dumps({"name": NAME}).encode())
    print(f"checkpoint sent to the {TAG} release", flush=True)


def main():
    if not os.environ.get("GITHUB_TOKEN"):
        sys.exit("GITHUB_TOKEN is not set (the notebook's secret)")
    args = sys.argv[1:]
    if "--minutes" not in args:
        args += ["--minutes", "690"]
    checkpoint = Path("/kaggle/working/checkpoint.pt") if Path("/kaggle/working").is_dir() else HERE / "out" / NAME
    download(checkpoint)
    # the GPU does the learning; every CPU makes handwriting for it
    if "--workers" not in args:
        args += ["--workers", str(max(1, os.cpu_count() or 2))]
    if "--eval-every" not in args:
        args += ["--eval-every", "30"]
    import train

    train.main(args + ["--checkpoint", str(checkpoint)], on_save=upload)


if __name__ == "__main__":
    main()
