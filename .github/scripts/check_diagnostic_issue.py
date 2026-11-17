import json
import os
import re
import subprocess
import sys

KNOWN_REPORT_HEADERS = {
    "problem / diagnostic context",
    "fps meter - device diagnostic report",
    "device & os",
    "display & screen",
    "cpu & graphics",
    "thermals & battery",
    "privileged access & operating mode",
    "permissions & services",
    "permissions & shizuku status",
    "raw thermal hal dump"
}

def clean_body(body: str) -> str:
    # 1. Remove HTML comments
    body = re.sub(r'<!--[\s\S]*?-->', '', body)
    user_lines = []
    
    for line in body.splitlines():
        line_s = line.strip()
        if not line_s:
            continue
            
        # Ignore markdown code fences like ```, ```markdown, or ```text
        if line_s.startswith("```"):
            continue
            
        # Ignore standard report section headers
        if line_s.startswith("#"):
            header_text = line_s.lstrip("#").strip().lower()
            if any(header_text == h or header_text.startswith(h) for h in KNOWN_REPORT_HEADERS):
                continue
                
        # Ignore diagnostic bullet points: e.g. - **App Version:** ...
        if line_s.startswith("- **") and ":**" in line_s:
            continue
            
        # Ignore raw thermal HAL dump entries: e.g. Temperature{...}
        if line_s.startswith("Temperature{"):
            continue
            
        # Anything else is user-typed context
        user_lines.append(line_s)
        
    return "\n".join(user_lines).strip()

COMMENT_MSG = (
    "This issue was submitted with a Device Diagnostics report but does not include any description or context about the problem.\n\n"
    "Auto-closing this to avoid flooding the issue tracker. If you are experiencing a problem or bug, please comment with details about what happened and re-open this issue."
)

def process_issue(issue_num: int, title: str, body: str, dry_run: bool = False):
    if not title.startswith("[Device Diagnostics]"):
        return

    user_text = clean_body(body or "")
    if not user_text:
        print(f"Issue #{issue_num} ({title}): No user description found. Auto-closing.")
        if not dry_run:
            subprocess.run(["gh", "issue", "comment", str(issue_num), "--body", COMMENT_MSG], check=False)
            subprocess.run(["gh", "issue", "close", str(issue_num), "--reason", "not planned"], check=True)
    else:
        print(f"Issue #{issue_num} ({title}): User description provided. Leaving open.")

def main():
    dry_run = "--dry-run" in sys.argv
    event_name = os.environ.get("EVENT_NAME", "workflow_dispatch")
    event_issue = os.environ.get("EVENT_ISSUE_NUMBER", "").strip()
    input_issue = os.environ.get("INPUT_ISSUE_NUMBER", "").strip()

    target_issue = event_issue or input_issue

    if event_name == "issues" and target_issue:
        # Triggered when an issue is opened
        res = subprocess.run(["gh", "issue", "view", target_issue, "--json", "number,title,body"], capture_output=True, text=True)
        if res.returncode == 0:
            data = json.loads(res.stdout)
            process_issue(data["number"], data.get("title", ""), data.get("body", "") or "", dry_run=dry_run)
    elif target_issue:
        # Triggered manually for a specific issue
        res = subprocess.run(["gh", "issue", "view", target_issue, "--json", "number,title,body"], capture_output=True, text=True)
        if res.returncode == 0:
            data = json.loads(res.stdout)
            process_issue(data["number"], data.get("title", ""), data.get("body", "") or "", dry_run=dry_run)
        else:
            print(f"Could not find issue #{target_issue}")
    else:
        # Triggered manually without arguments: scan all open diagnostic issues
        res = subprocess.run(["gh", "issue", "list", "--state", "open", "--search", "[Device Diagnostics] in:title", "--json", "number,title,body"], capture_output=True, text=True)
        if res.returncode == 0:
            issues = json.loads(res.stdout)
            print(f"Found {len(issues)} open diagnostic issues to check.")
            for iss in issues:
                process_issue(iss["number"], iss.get("title", ""), iss.get("body", "") or "", dry_run=dry_run)
        else:
            print("Failed to list issues:", res.stderr)

if __name__ == "__main__":
    main()
