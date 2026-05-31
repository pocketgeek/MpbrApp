#!/usr/bin/env python3
"""
Upload the signed AAB to Google Play closed testing (alpha) track.
Usage: python3 upload_to_play.py

Update RELEASE_NOTES below before each release.
"""

import sys
from pathlib import Path

import httplib2
import google_auth_httplib2
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
New ammo preset: Hornady 6mm ARC 106gr TAP ELD-M

Added factory ballistic data for the Hornady 6mm ARC 106gr TAP \
(Heat Shield tipped match grade, #81605). MV 2610 fps from a 24\" \
barrel, G1 BC 0.580. Source: Hornady TAP spec sheet.\
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
    auth_http = google_auth_httplib2.AuthorizedHttp(creds, http=httplib2.Http(timeout=300))
    service = build("androidpublisher", "v3", http=auth_http)
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
