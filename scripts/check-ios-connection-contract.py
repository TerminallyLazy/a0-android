#!/usr/bin/env python3
"""Check the shared origin fixture against current Swift types without editing/building the iOS app."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("ios_repo", type=Path)
parser.add_argument("--receipt", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
fixture = root / "app/src/test/resources/connection-origins.tsv"
http = args.ios_repo / "Sources/A0Core/HTTPClient.swift"
qr = args.ios_repo / "Sources/A0Core/QRImport.swift"
# The origin types have no dependency on the rest of A0Core. Compile their exact
# source prefix plus the complete QR source, with no edits to either declaration.
source = http.read_text()
assert source.count("public struct HTTPResponse:") == 1, "Swift source boundary changed; review extraction"
origin_source = source.split("public struct HTTPResponse:", 1)[0]
assert "public struct ServerOrigin:" in origin_source
main = r'''
import Foundation
func authority(_ origin: ServerOrigin) -> String {
    let c = URLComponents(url: origin.url, resolvingAgainstBaseURL: false)!
    return "\(c.scheme!.lowercased())|\(c.host!.lowercased())|\(c.port ?? 443)"
}
let fixture = try String(contentsOfFile: CommandLine.arguments[1], encoding: .utf8)
var count = 0
var failures: [String] = []
for line in fixture.split(separator: "\n") where !line.hasPrefix("#") {
    let fields = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
    precondition(fields.count == 3)
    let actual: ServerOrigin? = fields[0] == "qr"
        ? (try? QRDestination(fields[2]))?.origin : (try? ServerOrigin(fields[2]))
    let matched: Bool
    if fields[1] == "reject" { matched = actual == nil }
    else { matched = actual.map(authority) == authority(try ServerOrigin(fields[1])) }
    count += 1
    if !matched { failures.append("\(fields[0]): \(fields[2])") }
}
let result: [String: Any] = ["cases": count, "failed": failures]
print(String(data: try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys]), encoding: .utf8)!)
'''
with tempfile.TemporaryDirectory(prefix="a0-ios-origin-contract-") as temp:
    directory = Path(temp)
    (directory / "Origin.swift").write_text(origin_source)
    (directory / "main.swift").write_text(main)
    executable = directory / "check"
    subprocess.run(["xcrun", "swiftc", str(directory / "Origin.swift"), str(qr),
                    str(directory / "main.swift"), "-o", str(executable)], check=True)
    result = json.loads(subprocess.check_output([str(executable), str(fixture)], text=True))
result.update({
    "scope": "Swift origin and QR source declarations only; not an iOS app build or runtime acceptance",
    "ios_revision": subprocess.check_output(["git", "-C", str(args.ios_repo), "rev-parse", "HEAD"], text=True).strip(),
    "source_sha256": {str(path.relative_to(args.ios_repo)): hashlib.sha256(path.read_bytes()).hexdigest() for path in (http, qr)},
    "fixture_sha256": hashlib.sha256(fixture.read_bytes()).hexdigest(),
})
if args.receipt:
    args.receipt.write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result, indent=2))
raise SystemExit(bool(result["failed"]))
