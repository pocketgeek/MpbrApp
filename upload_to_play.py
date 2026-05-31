#!/usr/bin/env python3
"""
Upload the signed AAB to Google Play closed testing (alpha) track.
Usage: python3 upload_to_play.py

Update RELEASE_NOTES below before each release.
"""

import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = "us.pgnet.mpbr"
TRACK        = "alpha"   # closed testing in Play Console
KEY_FILE     = Path.home() / "plexcloud-211915-28539bf31df4.json"
AAB_FILE     = Path(__file__).parent / "app/release/app-release.aab"
SCOPES       = ["https://www.googleapis.com/auth/androidpublisher"]

# Update this before each release (max 500 characters)
RELEASE_NOTES = """\
New reticle: Leupold VX-Freedom MOA-Ring 1.5-4×20

Added the Leupold MOA-Ring scope reticle preset. Features the distinctive \
40 MOA ring for fast close-range target acquisition, full crosshair with \
3.4 MOA center circle, lead/windage markers at ±6.5 and ±13 MOA for moving \
targets, BDC tick marks at 5 MOA spacing, and a tapered thick post below. \
Subtensions sourced from the official Leupold reticle diagram; valid at 4× \
(max magnification).\
"""

def main():
    if not KEY_FILE.exists():
        sys.exit(f"Service account key not found: {KEY_FILE}")
    if not AAB_FILE.exists():
        sys.exit(f"AAB not found: {AAB_FILE}\nBuild a signed release in Android Studio first.")
    if len(RELEASE_NOTES) > 500:
        sys.exit(f"RELEASE_NOTES too long ({len(RELEASE_NOTES)} chars, max 500). Trim it.")

    print(f"AAB:     {AAB_FILE}  ({AAB_FILE.stat().st_size / 1_048_576:.1f} MB)")
    print(f"Package: {PACKAGE_NAME}")
    print(f"Track:   {TRACK}")
    print(f"Notes:   {RELEASE_NOTES[:80].strip()}…")
    print()

    creds   = service_account.Credentials.from_service_account_file(str(KEY_FILE), scopes=SCOPES)
    service = build("androidpublisher", "v3", credentials=creds)
    edits   = service.edits()

    # 1 — open an edit
    edit    = edits.insert(packageName=PACKAGE_NAME, body={}).execute()
    edit_id = edit["id"]
    print(f"Edit opened: {edit_id}")

    # 2 — upload the bundle
    print("Uploading AAB… (this may take a minute)")
    bundle = edits.bundles().upload(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        media_body=MediaFileUpload(str(AAB_FILE), mimetype="application/octet-stream"),
    ).execute()
    version_code = bundle["versionCode"]
    print(f"Bundle uploaded: versionCode {version_code}")

    # 3 — assign to track with release notes
    edits.tracks().update(
        packageName=PACKAGE_NAME,
        editId=edit_id,
        track=TRACK,
        body={
            "track": TRACK,
            "releases": [{
                "versionCodes": [version_code],
                "status": "completed",
                "releaseNotes": [{"language": "en-US", "text": RELEASE_NOTES}],
            }],
        },
    ).execute()
    print(f"Assigned to track: {TRACK}")

    # 4 — commit
    edits.commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
    print(f"\nDone. v{version_code} is live on the {TRACK} track.")

if __name__ == "__main__":
    main()
